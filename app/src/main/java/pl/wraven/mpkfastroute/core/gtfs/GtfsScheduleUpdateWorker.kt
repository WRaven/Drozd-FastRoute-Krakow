package pl.wraven.mpkfastroute.core.gtfs

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
        val forceDownload = inputData.getBoolean(
            GtfsScheduleUpdateScheduler.FORCE_REFRESH_KEY,
            false
        )
        val updated = gtfsZipParser.updateSchedule(forceDownload) { progress ->
            setProgress(
                workDataOf(
                    GtfsScheduleUpdateScheduler.PROGRESS_PERCENT_KEY to progress.percent,
                    GtfsScheduleUpdateScheduler.PROGRESS_MESSAGE_KEY to progress.message
                )
            )
        }
        if (updated) {
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
        Result.failure(
            workDataOf(GtfsScheduleUpdateScheduler.ERROR_MESSAGE_KEY to exception.message)
        )
    } catch (exception: IllegalStateException) {
        Log.e(TAG, "Could not import the downloaded ZTP GTFS schedule.", exception)
        Result.failure(
            workDataOf(GtfsScheduleUpdateScheduler.ERROR_MESSAGE_KEY to exception.message)
        )
    }

    private companion object {
        const val TAG = "GtfsScheduleUpdate"
    }
}

object GtfsScheduleUpdateScheduler {
    const val WORK_TAG = "gtfs-schedule-update"
    const val PROGRESS_PERCENT_KEY = "progress_percent"
    const val PROGRESS_MESSAGE_KEY = "progress_message"
    const val ERROR_MESSAGE_KEY = "error_message"
    const val FORCE_REFRESH_KEY = "force_refresh"

    const val STARTUP_WORK_NAME = "gtfs-schedule-startup-update"
    const val PERIODIC_WORK_NAME = "gtfs-schedule-update"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workManager = WorkManager.getInstance(context)
        val periodicRequest = PeriodicWorkRequestBuilder<GtfsScheduleUpdateWorker>(
            24,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .addTag(PERIODIC_WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        enqueueUpdate(workManager, constraints, forceDownload = false)
    }

    fun requestRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        enqueueUpdate(
            WorkManager.getInstance(context),
            constraints,
            forceDownload = true
        )
    }

    private fun enqueueUpdate(
        workManager: WorkManager,
        constraints: Constraints,
        forceDownload: Boolean
    ) {
        val request = OneTimeWorkRequestBuilder<GtfsScheduleUpdateWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putBoolean(FORCE_REFRESH_KEY, forceDownload).build())
            .addTag(WORK_TAG)
            .addTag(STARTUP_WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            STARTUP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
