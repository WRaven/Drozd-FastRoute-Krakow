package pl.wraven.mpkfastroute.core.live

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.EnumMap
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ZtpGtfsArchiveRepository internal constructor(
    private val httpClient: HttpClient,
    private val cacheDirectory: File
) {
    @Inject
    constructor(httpClient: HttpClient, @ApplicationContext context: Context) :
        this(httpClient, File(context.cacheDir, "ztp-live-gtfs"))

    private val mutexes = EnumMap<TransitVehicleMode, Mutex>(TransitVehicleMode::class.java).apply {
        TransitVehicleMode.entries.forEach { put(it, Mutex()) }
    }

    suspend fun <T> withArchive(
        mode: TransitVehicleMode,
        nowMillis: Long = System.currentTimeMillis(),
        forceRefresh: Boolean = false,
        read: suspend (File) -> T
    ): T = mutexes.getValue(mode).withLock {
        withContext(Dispatchers.IO) {
            if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs()) {
                throw IOException("Nie można utworzyć pamięci podręcznej rozkładu Live.")
            }
            val archive = File(cacheDirectory, "${mode.feedSuffix}.zip")
            if (forceRefresh || !archive.isFile ||
                nowMillis - archive.lastModified() !in 0 until CACHE_DURATION_MILLIS
            ) {
                val temporary = File(cacheDirectory, "${mode.feedSuffix}.tmp")
                try {
                    val coroutineContext = currentCoroutineContext()
                    try {
                        httpClient.prepareGet(gtfsUrl(mode)) {
                            timeout {
                                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                                socketTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS
                                requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS
                            }
                        }.execute { response ->
                            if (!response.status.isSuccess()) {
                                throw IOException(
                                    "ZTP zwrócił HTTP ${response.status.value} dla rozkładu ${mode.pluralLabel}."
                                )
                            }
                            response.bodyAsChannel().toInputStream().use { input ->
                                temporary.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(STREAM_BUFFER_SIZE)
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val bytesRead = input.read(buffer)
                                        if (bytesRead < 0) break
                                        output.write(buffer, 0, bytesRead)
                                    }
                                }
                            }
                        }
                    } catch (exception: HttpRequestTimeoutException) {
                        throw IOException(
                            "Przekroczono czas pobierania rozkładu ${mode.pluralLabel}.",
                            exception
                        )
                    }
                    ZipFile(temporary).use { zip ->
                        if (!zip.entries().asSequence().any {
                                it.name.substringAfterLast('/') == "trips.txt"
                            }
                        ) {
                            throw IOException("Archiwum rozkładu Live nie zawiera kursów.")
                        }
                    }
                    temporary.setLastModified(nowMillis)
                    Files.move(temporary.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    archive.setLastModified(nowMillis)
                } finally {
                    Files.deleteIfExists(temporary.toPath())
                }
            }
            read(archive)
        }
    }

    companion object {
        internal const val CACHE_DURATION_MILLIS = 60 * 60 * 1_000L
        private const val CONNECT_TIMEOUT_MILLIS = 30_000L
        private const val DOWNLOAD_TIMEOUT_MILLIS = 120_000L
        private const val STREAM_BUFFER_SIZE = 32 * 1024

        fun gtfsUrl(mode: TransitVehicleMode): String =
            "https://gtfs.ztp.krakow.pl/GTFS_KRK_${mode.feedSuffix}.zip"
    }
}
