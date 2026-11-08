package pl.wraven.mpkfastroute.feature.search

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class TaxDonationReminderScheduleResult {
    SCHEDULED,
    ALREADY_SCHEDULED,
    ALREADY_SENT
}

object TaxDonationReminderScheduler {
    const val WORK_NAME = "tax-donation-reminder-2027"
    private const val LEGACY_NOTIFICATION_ID = 2_002

    private const val PREFERENCES_NAME = "tax_donation_reminder"
    private const val ENABLED_KEY = "enabled"
    private const val SENT_KEY = "sent"

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(ENABLED_KEY, false)

    fun wasSent(context: Context): Boolean =
        preferences(context).getBoolean(SENT_KEY, false)

    fun schedule(context: Context): TaxDonationReminderScheduleResult {
        if (wasSent(context)) return TaxDonationReminderScheduleResult.ALREADY_SENT
        if (isEnabled(context)) return TaxDonationReminderScheduleResult.ALREADY_SCHEDULED

        cancelLegacySystemReminder(context)
        preferences(context).edit().putBoolean(ENABLED_KEY, true).apply()
        return TaxDonationReminderScheduleResult.SCHEDULED
    }

    fun cancel(context: Context) {
        cancelLegacySystemReminder(context)
        preferences(context).edit().putBoolean(ENABLED_KEY, false).apply()
    }

    fun cancelLegacySystemReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        context.getSystemService(NotificationManager::class.java)
            .cancel(LEGACY_NOTIFICATION_ID)
    }

    fun markShownIfDue(
        context: Context,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val prefs = preferences(context)
        if (!prefs.getBoolean(ENABLED_KEY, false) || prefs.getBoolean(SENT_KEY, false)) {
            return false
        }
        if (!isTaxDonationReminderDue(now, zoneId)) return false

        prefs.edit()
            .putBoolean(ENABLED_KEY, false)
            .putBoolean(SENT_KEY, true)
            .apply()
        return true
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal fun isTaxDonationReminderDue(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Boolean = !taxDonationReminderAt(zoneId).toInstant().isAfter(now)

internal fun taxDonationReminderDelayMillis(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Long = Duration.between(now, taxDonationReminderAt(zoneId).toInstant())
    .toMillis()
    .coerceAtLeast(0)

internal fun taxDonationReminderAt(zoneId: ZoneId): java.time.ZonedDateTime =
    LocalDate.of(2027, 2, 15).atTime(LocalTime.of(10, 0)).atZone(zoneId)

@HiltWorker
class TaxDonationReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: androidx.work.WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        Log.i(TAG, "Ignoring obsolete scheduled system reminder after in-app reminder update.")
        return Result.success()
    }

    private companion object {
        const val TAG = "TaxDonationReminder"
    }
}
