package pl.wraven.mpkfastroute.feature.search

import android.content.Context
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object CommuteReminderScheduler {
    const val DESTINATION_KEY = "destination"
    const val ARRIVAL_TARGET_KEY = "arrivalTarget"
    const val DEPARTURE_TARGET_KEY = "departureTarget"
    const val OCCURRENCE_KEY = "occurrence"
    const val WORK_TAG = "commute-reminders"
    const val CHANNEL_ID = "commute_reminders"
    const val UPDATE_INTERVAL_MINUTES = 5L
    const val HOME_NOTIFICATION_ID = 2_000
    const val WORK_NOTIFICATION_ID = 2_001

    private const val COORDINATOR_WORK_NAME = "commute-reminder-coordinator"
    private const val IMMEDIATE_WORK_NAME = "commute-reminder-check-now"

    fun synchronize(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(COORDINATOR_WORK_NAME)
            workManager.cancelAllWorkByTag(WORK_TAG)
            context.getSystemService(NotificationManager::class.java)
                .apply {
                    cancel(HOME_NOTIFICATION_ID)
                    cancel(WORK_NOTIFICATION_ID)
                }
            return
        }

        val periodicRequest =
            PeriodicWorkRequestBuilder<CommuteReminderCoordinatorWorker>(
                COORDINATOR_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .addTag(WORK_TAG)
                .build()
        workManager.enqueueUniquePeriodicWork(
            COORDINATOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CommuteReminderCoordinatorWorker>()
                .addTag(WORK_TAG)
                .build()
        )
    }

    fun scheduleOccurrence(
        context: Context,
        destination: SavedDestination,
        arrivalTarget: LocalDateTime,
        departureTarget: LocalDateTime
    ) {
        val uniqueName = occurrenceWorkName(destination, arrivalTarget)
        val delayMillis = Duration.between(
            LocalDateTime.now(),
            departureTarget.minusMinutes(NOTIFICATION_LEAD_MINUTES)
        ).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<CommuteStatusWorker>()
            .setInputData(
                workDataOf(
                    DESTINATION_KEY to destination.name,
                    ARRIVAL_TARGET_KEY to arrivalTarget.toString(),
                    DEPARTURE_TARGET_KEY to departureTarget.toString(),
                    OCCURRENCE_KEY to uniqueName
                )
            )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleNextUpdate(
        context: Context,
        uniqueName: String,
        inputData: androidx.work.Data,
        delayMinutes: Long
    ) {
        val request = OneTimeWorkRequestBuilder<CommuteStatusWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun occurrenceWorkName(
        destination: SavedDestination,
        arrivalTarget: LocalDateTime
    ): String = "commute-${destination.name.lowercase()}-$arrivalTarget"

    private const val COORDINATOR_INTERVAL_MINUTES = 15L
    private const val NOTIFICATION_LEAD_MINUTES = 15L
}
