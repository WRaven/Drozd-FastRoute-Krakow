package pl.wraven.mpkfastroute.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Query("SELECT * FROM stops ORDER BY stopName, stopId")
    fun observeAll(): Flow<List<StopEntity>>

    @Query("SELECT * FROM stops ORDER BY stopName, stopId")
    suspend fun getAll(): List<StopEntity>

    @Query("SELECT * FROM stops WHERE stopId = :stopId LIMIT 1")
    suspend fun getById(stopId: String): StopEntity?

    @Query("SELECT * FROM stops WHERE stopCode = :stopCode")
    suspend fun getByCode(stopCode: String): List<StopEntity>

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun count(): Int

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)

    @Query("DELETE FROM stops")
    suspend fun deleteAll()
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY routeShortName, routeId")
    suspend fun getAll(): List<RouteEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<RouteEntity>)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE tripId = :tripId LIMIT 1")
    suspend fun getById(tripId: String): TripEntity?

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripEntity>)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}

@Dao
interface ServiceCalendarDao {
    @Query("SELECT * FROM calendar WHERE startDate <= :date AND endDate >= :date")
    suspend fun getForDate(date: String): List<ServiceCalendarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(calendars: List<ServiceCalendarEntity>)

    @Query("DELETE FROM calendar")
    suspend fun deleteAll()

    @Query("SELECT * FROM calendar_dates WHERE date = :date")
    suspend fun getExceptionsForDate(date: String): List<CalendarDateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAllExceptions(exceptions: List<CalendarDateEntity>)

    @Query("DELETE FROM calendar_dates")
    suspend fun deleteAllExceptions()
}

data class GtfsConnectionRow(
    val departureStopId: String,
    val arrivalStopId: String,
    val departureTime: Int,
    val arrivalTime: Int,
    val tripId: String,
    val routeShortName: String,
    val serviceId: String
)

@Dao
interface StopTimeDao {
    @Query(
        """
        SELECT * FROM stop_times
        WHERE departureTime BETWEEN :startTime AND :endTime
        ORDER BY departureTime, tripId, stopSequence
        """
    )
    suspend fun getDeparturesBetween(startTime: Int, endTime: Int): List<StopTimeEntity>

    @Query(
        """
        SELECT departure.stopId AS departureStopId,
               arrival.stopId AS arrivalStopId,
               departure.departureTime AS departureTime,
               arrival.arrivalTime AS arrivalTime,
               trip.tripId AS tripId,
               route.routeShortName AS routeShortName,
               trip.serviceId AS serviceId
        FROM stop_times AS departure
        JOIN stop_times AS arrival
          ON arrival.tripId = departure.tripId
         AND arrival.stopSequence = (
             SELECT MIN(nextStop.stopSequence)
             FROM stop_times AS nextStop
             WHERE nextStop.tripId = departure.tripId
               AND nextStop.stopSequence > departure.stopSequence
         )
        JOIN trips AS trip ON trip.tripId = departure.tripId
        JOIN routes AS route ON route.routeId = trip.routeId
        WHERE departure.departureTime BETWEEN :startTime AND :endTime
        ORDER BY departure.departureTime, departure.tripId, departure.stopSequence
        """
    )
    suspend fun getConnectionsBetween(startTime: Int, endTime: Int): List<GtfsConnectionRow>

    @Query("SELECT * FROM stop_times WHERE tripId = :tripId ORDER BY stopSequence")
    suspend fun getForTrip(tripId: String): List<StopTimeEntity>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stopTimes: List<StopTimeEntity>)

    @Query("DELETE FROM stop_times")
    suspend fun deleteAll()
}
