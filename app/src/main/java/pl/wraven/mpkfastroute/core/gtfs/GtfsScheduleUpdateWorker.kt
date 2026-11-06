package pl.wraven.mpkfastroute.core.gtfs

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit

@HiltWorker
class GtfsScheduleUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val gtfsZipParser: GtfsZipParser
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = try {
        if (gtfsZipParser.importScheduleIfUpdated()) {
            Log.i(TAG, "A new ZTP GTFS schedule was downloaded and imported.")
        } else {
            Log.i(TAG, "The installed ZTP GTFS schedule is up to date.")
        }
        Result.success()
    } catch (exception: IOException) {
        Log.w(TAG, "Could not check or download the ZTP GTFS schedule; retrying.", exception)
        Result.retry()
    } catch (exception: SQLiteException) {
        Log.e(TAG, "Could not save the updated ZTP GTFS schedule; retrying.", exception)
        Result.retry()
    } catch (exception: IllegalArgumentException) {
        Log.e(TAG, "The downloaded ZTP GTFS schedule is invalid.", exception)
        Result.failure()
    } catch (exception: IllegalStateException) {
        Log.e(TAG, "Could not import the downloaded ZTP GTFS schedule.", exception)
        Result.failure()
    }

    private companion object {
        const val TAG = "GtfsScheduleUpdate"
    }
}

object GtfsScheduleUpdateScheduler {
    private const val WORK_NAME = "gtfs-schedule-update"
    private const val WORK_TAG = "gtfs-schedule-update"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<GtfsScheduleUpdateWorker>(
            24,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
