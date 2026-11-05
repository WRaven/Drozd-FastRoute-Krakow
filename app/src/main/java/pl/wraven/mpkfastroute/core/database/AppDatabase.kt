package pl.wraven.mpkfastroute.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StopEntity::class,
        RouteEntity::class,
        TripEntity::class,
        StopTimeEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao

    abstract fun routeDao(): RouteDao

    abstract fun tripDao(): TripDao

    abstract fun stopTimeDao(): StopTimeDao
}
