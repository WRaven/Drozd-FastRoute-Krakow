package pl.wraven.mpkfastroute.core.live

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.gtfs.readCsv
import pl.wraven.mpkfastroute.core.gtfs.required

@Singleton
class ZtpTramRoutesRepository @Inject constructor(
    private val httpClient: HttpClient
) {
    private val mutex = Mutex()
    private var cachedRoutes: Map<String, String>? = null
    private var cachedAtMillis = 0L

    suspend fun getRouteShortNamesByTripId(
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, String> = mutex.withLock {
        cachedRoutes?.takeIf {
            nowMillis - cachedAtMillis in 0 until CACHE_DURATION_MILLIS
        }?.let { return@withLock it }

        val response = try {
            httpClient.get(TRAM_GTFS_URL) {
                timeout { requestTimeoutMillis = ARCHIVE_TIMEOUT_MILLIS }
            }
        } catch (exception: HttpRequestTimeoutException) {
            throw IOException("Przekroczono czas pobierania numerów linii tramwajowych.", exception)
        }
        if (!response.status.isSuccess()) {
            throw IOException("ZTP zwrócił HTTP ${response.status.value} dla rozkładu tramwajowego.")
        }
        val archive = response.body<ByteArray>()
        val routes = withContext(Dispatchers.Default) {
            parseTramRouteShortNames(archive.inputStream())
        }
        cachedRoutes = routes
        cachedAtMillis = nowMillis
        routes
    }

    companion object {
        const val TRAM_GTFS_URL = "https://gtfs.ztp.krakow.pl/GTFS_KRK_T.zip"
        private const val CACHE_DURATION_MILLIS = 60 * 60 * 1_000L
        private const val ARCHIVE_TIMEOUT_MILLIS = 60_000L
    }
}

internal suspend fun parseTramRouteShortNames(archive: InputStream): Map<String, String> {
    val routes = mutableMapOf<String, String>()
    val trips = mutableMapOf<String, String>()
    val coroutineContext = currentCoroutineContext()
    ZipInputStream(archive.buffered()).use { zip ->
        while (true) {
            coroutineContext.ensureActive()
            val entry = zip.nextEntry ?: break
            val name = entry.name.substringAfterLast('/')
            if (!entry.isDirectory && (name == "routes.txt" || name == "trips.txt")) {
                readCsv(zip, entry.name) { columns, values, rowNumber ->
                    coroutineContext.ensureActive()
                    val routeId = values.required(columns, "route_id", entry.name, rowNumber)
                    if (name == "routes.txt") {
                        routes[routeId] = values.required(
                            columns, "route_short_name", entry.name, rowNumber
                        )
                    } else {
                        trips[values.required(columns, "trip_id", entry.name, rowNumber)] = routeId
                    }
                }
            }
            zip.closeEntry()
        }
    }
    if (routes.isEmpty() || trips.isEmpty()) {
        throw IOException("Rozkład tramwajowy nie zawiera linii lub kursów.")
    }
    return trips.mapValues { (tripId, routeId) ->
        routes[routeId] ?: throw IOException("Kurs $tripId odwołuje się do brakującej linii $routeId.")
    }
}
