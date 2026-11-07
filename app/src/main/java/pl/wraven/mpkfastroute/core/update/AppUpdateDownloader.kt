package pl.wraven.mpkfastroute.core.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AppUpdateDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun download(
        update: UpdateCheckResult,
        onProgress: (Int?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!GitHubUpdateChecker.isTrustedDownloadUrl(update.downloadUrl)) {
            throw IOException("Adres pobierania APK jest nieprawidłowy.")
        }

        val connection = URL(update.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true
        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IOException("GitHub zwrócił HTTP $statusCode podczas pobierania APK.")
            }
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                throw IOException("Pobieranie APK przekierowało na niezabezpieczony adres.")
            }

            val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY)
            if (!updateDirectory.isDirectory && !updateDirectory.mkdirs()) {
                throw IOException("Nie można przygotować miejsca na pobraną aktualizację.")
            }
            val targetFile = File(
                updateDirectory,
                "mpk-fastroute-${safeFileVersion(update.latestVersion)}.apk"
            )
            val temporaryFile = File.createTempFile("mpk-fastroute-", ".apk", updateDirectory)
            var completed = false
            try {
                val contentLength = connection.contentLengthLong
                if (contentLength <= 0) onProgress(null)
                val coroutineContext = currentCoroutineContext()
                var downloadedBytes = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (contentLength > 0) {
                                onProgress(
                                    ((downloadedBytes.toDouble() / contentLength) * 100)
                                        .toInt()
                                        .coerceIn(0, 99)
                                )
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (downloadedBytes == 0L ||
                    (contentLength > 0 && downloadedBytes != contentLength)
                ) {
                    throw IOException("Pobrany plik APK jest pusty lub niekompletny.")
                }
                val packageName = context.packageManager
                    .getPackageArchiveInfo(temporaryFile.absolutePath, 0)
                    ?.packageName
                if (packageName != context.packageName) {
                    throw IOException("Pobrany plik nie jest aktualizacją tej aplikacji.")
                }
                if (targetFile.exists() && !targetFile.delete()) {
                    throw IOException("Nie można zastąpić poprzednio pobranego pliku APK.")
                }
                if (!temporaryFile.renameTo(targetFile)) {
                    throw IOException("Nie można zapisać pobranego pliku APK.")
                }
                completed = true
                onProgress(100)
                targetFile
            } finally {
                if (!completed) temporaryFile.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun safeFileVersion(version: String): String =
        version.replace(UNSAFE_FILE_NAME_CHARACTERS, "_")

    private companion object {
        const val UPDATE_DIRECTORY = "updates"
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 120_000
        const val BUFFER_SIZE = 32 * 1024
        val UNSAFE_FILE_NAME_CHARACTERS = Regex("[^A-Za-z0-9._-]")
    }
}
