package pl.wraven.mpkfastroute.core.live

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
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

    private val mutex = Mutex()

    suspend fun <T> withArchive(
        mode: TransitVehicleMode,
        nowMillis: Long = System.currentTimeMillis(),
        forceRefresh: Boolean = false,
        read: suspend (File) -> T
    ): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs()) {
                throw IOException("Nie można utworzyć pamięci podręcznej rozkładu Live.")
            }
            val archive = File(cacheDirectory, "${mode.feedSuffix}.zip")
            if (forceRefresh || !archive.isFile ||
                nowMillis - archive.lastModified() !in 0 until CACHE_DURATION_MILLIS
            ) {
                val response = try {
                    httpClient.get(gtfsUrl(mode)) {
                        timeout { requestTimeoutMillis = 120_000L }
                    }
                } catch (exception: HttpRequestTimeoutException) {
                    throw IOException("Przekroczono czas pobierania rozkładu ${mode.pluralLabel}.", exception)
                }
                if (!response.status.isSuccess()) {
                    throw IOException("ZTP zwrócił HTTP ${response.status.value} dla rozkładu ${mode.pluralLabel}.")
                }
                val temporary = File(cacheDirectory, "${mode.feedSuffix}.tmp")
                try {
                    temporary.writeBytes(response.body<ByteArray>())
                    ZipFile(temporary).use { zip ->
                        if (!zip.entries().asSequence().any {
                                it.name.substringAfterLast('/') == "trips.txt"
                            }
                        ) {
                            throw IOException("Archiwum rozkładu Live nie zawiera kursów.")
                        }
                    }
                    Files.move(temporary.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    if (!archive.setLastModified(nowMillis)) {
                        throw IOException("Nie można zapisać czasu aktualizacji rozkładu Live.")
                    }
                } finally {
                    Files.deleteIfExists(temporary.toPath())
                }
            }
            read(archive)
        }
    }

    companion object {
        internal const val CACHE_DURATION_MILLIS = 60 * 60 * 1_000L
        fun gtfsUrl(mode: TransitVehicleMode): String =
            "https://gtfs.ztp.krakow.pl/GTFS_KRK_${mode.feedSuffix}.zip"
    }
}
