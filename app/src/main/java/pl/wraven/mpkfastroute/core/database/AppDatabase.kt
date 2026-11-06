package pl.wraven.mpkfastroute.core.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StopEntity::class,
        RouteEntity::class,
        TripEntity::class,
        ServiceCalendarEntity::class,
        CalendarDateEntity::class,
        StopTimeEntity::class,
        ConnectionEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao

    abstract fun routeDao(): RouteDao

    abstract fun tripDao(): TripDao

    abstract fun serviceCalendarDao(): ServiceCalendarDao

    abstract fun stopTimeDao(): StopTimeDao

    abstract fun connectionDao(): ConnectionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calendar` (
                        `serviceId` TEXT NOT NULL,
                        `monday` INTEGER NOT NULL,
                        `tuesday` INTEGER NOT NULL,
                        `wednesday` INTEGER NOT NULL,
                        `thursday` INTEGER NOT NULL,
                        `friday` INTEGER NOT NULL,
                        `saturday` INTEGER NOT NULL,
                        `sunday` INTEGER NOT NULL,
                        `startDate` TEXT NOT NULL,
                        `endDate` TEXT NOT NULL,
                        PRIMARY KEY(`serviceId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calendar_dates` (
                        `serviceId` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `exceptionType` INTEGER NOT NULL,
                        PRIMARY KEY(`serviceId`, `date`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_calendar_dates_date` ON `calendar_dates` (`date`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_stop_times_tripId`")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_stop_times_tripId_stopSequence`
                    ON `stop_times` (`tripId`, `stopSequence`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_stop_times_departureTime_tripId_stopSequence`
                    ON `stop_times` (`departureTime`, `tripId`, `stopSequence`)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `connections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tripId` TEXT NOT NULL,
                        `serviceId` TEXT NOT NULL,
                        `routeShortName` TEXT NOT NULL,
                        `departureStopId` TEXT NOT NULL,
                        `arrivalStopId` TEXT NOT NULL,
                        `departureTime` INTEGER NOT NULL,
                        `arrivalTime` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_connections_departureTime_serviceId_tripId`
                    ON `connections` (`departureTime`, `serviceId`, `tripId`)
                    """.trimIndent()
                )
            }
        }
    }
}
