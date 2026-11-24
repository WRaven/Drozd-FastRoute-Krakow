package pl.wraven.mpkfastroute.core.live

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.wraven.mpkfastroute.core.gtfs.readCsv
import pl.wraven.mpkfastroute.core.gtfs.required

@Singleton
class ZtpTramRoutesRepository @Inject constructor(
    private val archiveRepository: ZtpGtfsArchiveRepository
) {
    private val mutex = Mutex()
    private var cachedRoutes: Map<String, String>? = null
    private var cachedAtMillis = 0L
    private var cachedArchiveModified = -1L

    suspend fun getRouteShortNamesByTripId(
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, String> = mutex.withLock {
        archiveRepository.withArchive(TransitVehicleMode.TRAM, nowMillis) { archive ->
            val archiveModified = archive.lastModified()
            cachedRoutes?.takeIf {
                archiveModified == cachedArchiveModified &&
                    nowMillis - cachedAtMillis in 0 until CACHE_DURATION_MILLIS
            }?.let { return@withArchive it }

            val routes = parseTramRouteShortNames(archive)
            cachedRoutes = routes
            cachedAtMillis = nowMillis
            cachedArchiveModified = archiveModified
            routes
        }
    }

    companion object {
        const val TRAM_GTFS_URL = "https://gtfs.ztp.krakow.pl/GTFS_KRK_T.zip"
        private const val CACHE_DURATION_MILLIS = 60 * 60 * 1_000L
    }
}

internal suspend fun parseTramRouteShortNames(archive: File): Map<String, String> {
    val routes = mutableMapOf<String, String>()
    val trips = mutableMapOf<String, String>()
    val coroutineContext = currentCoroutineContext()
    ZipFile(archive).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            coroutineContext.ensureActive()
            val entry = entries.nextElement()
            val name = entry.name.substringAfterLast('/')
            if (!entry.isDirectory && (name == "routes.txt" || name == "trips.txt")) {
                zip.getInputStream(entry).use { input ->
                    readCsv(input, entry.name) { columns, values, rowNumber ->
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
            }
        }
    }
    return buildTramRouteMap(routes, trips)
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
    return buildTramRouteMap(routes, trips)
}

private fun buildTramRouteMap(
    routes: Map<String, String>,
    trips: Map<String, String>
): Map<String, String> {
    if (routes.isEmpty() || trips.isEmpty()) {
        throw IOException("Rozkład tramwajowy nie zawiera linii lub kursów.")
    }
    return trips.mapValues { (tripId, routeId) ->
        routes[routeId] ?: throw IOException("Kurs $tripId odwołuje się do brakującej linii $routeId.")
    }
}
