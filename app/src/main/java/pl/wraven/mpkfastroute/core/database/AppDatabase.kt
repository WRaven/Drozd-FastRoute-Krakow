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
        StopTimeEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao

    abstract fun routeDao(): RouteDao

    abstract fun tripDao(): TripDao

    abstract fun serviceCalendarDao(): ServiceCalendarDao

    abstract fun stopTimeDao(): StopTimeDao

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
    }
}
