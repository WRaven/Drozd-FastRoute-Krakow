package pl.wraven.mpkfastroute.feature.search

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

enum class TaxDonationReminderScheduleResult {
    SCHEDULED,
    ALREADY_SCHEDULED,
    ALREADY_SENT,
    DATE_PASSED,
    NOTIFICATIONS_DISABLED
}

object TaxDonationReminderScheduler {
    const val WORK_NAME = "tax-donation-reminder-2027"
    const val CHANNEL_ID = "tax_donation_reminder"
    const val NOTIFICATION_ID = 2_002
    const val INFO_URL = "https://fsmm.pl/partner/aleksandra-lachnowicz/"

    private const val PREFERENCES_NAME = "tax_donation_reminder"
    private const val ENABLED_KEY = "enabled"
    private const val SENT_KEY = "sent"

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(ENABLED_KEY, false)

    fun wasSent(context: Context): Boolean =
        preferences(context).getBoolean(SENT_KEY, false)

    fun prepareNotifications(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Przypomnienie o 1,5% podatku",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Jednorazowe przypomnienie o dobrowolnym wsparciu."
                }
            )
        }
    }

    fun schedule(context: Context): TaxDonationReminderScheduleResult {
        if (wasSent(context)) return TaxDonationReminderScheduleResult.ALREADY_SENT
        if (isEnabled(context)) return TaxDonationReminderScheduleResult.ALREADY_SCHEDULED
        if (!isTaxDonationReminderDateInFuture()) {
            return TaxDonationReminderScheduleResult.DATE_PASSED
        }

        prepareNotifications(context)
        if (!hasNotificationPermission(context)) {
            return TaxDonationReminderScheduleResult.NOTIFICATIONS_DISABLED
        }

        val delayMillis = Duration.between(
            Instant.now(),
            taxDonationReminderAt(ZoneId.systemDefault()).toInstant()
        ).toMillis()
        val request = OneTimeWorkRequestBuilder<TaxDonationReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        preferences(context).edit().putBoolean(ENABLED_KEY, true).apply()
        return TaxDonationReminderScheduleResult.SCHEDULED
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        preferences(context).edit().putBoolean(ENABLED_KEY, false).apply()
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }

    internal fun markSent(context: Context) {
        preferences(context).edit()
            .putBoolean(ENABLED_KEY, false)
            .putBoolean(SENT_KEY, true)
            .apply()
    }

    internal fun markUnavailable(context: Context) {
        preferences(context).edit().putBoolean(ENABLED_KEY, false).apply()
    }

    internal fun hasNotificationPermission(context: Context): Boolean {
        val hasRuntimePermission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelEnabled =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                manager.getNotificationChannel(CHANNEL_ID)?.importance !=
                NotificationManager.IMPORTANCE_NONE
        return hasRuntimePermission && manager.areNotificationsEnabled() && channelEnabled
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal fun isTaxDonationReminderDateInFuture(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Boolean = taxDonationReminderAt(zoneId).toInstant().isAfter(now)

internal fun taxDonationReminderAt(zoneId: ZoneId): java.time.ZonedDateTime =
    LocalDate.of(2027, 2, 15).atTime(LocalTime.of(10, 0)).atZone(zoneId)

@HiltWorker
class TaxDonationReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: androidx.work.WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        TaxDonationReminderScheduler.prepareNotifications(applicationContext)
        if (!TaxDonationReminderScheduler.hasNotificationPermission(applicationContext)) {
            Log.w(TAG, "Tax donation reminder not sent: notification permission is unavailable.")
            TaxDonationReminderScheduler.markUnavailable(applicationContext)
            return Result.failure()
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val notificationIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(TaxDonationReminderScheduler.INFO_URL)
        )
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            TaxDonationReminderScheduler.NOTIFICATION_ID,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = "Jeśli podoba Ci się FastRoute, możesz przekazać 1,5% podatku na " +
            "DT u Oli w Legionowie. KRS: 0000270261. Cel szczegółowy: " +
            "DT u Oli w Legionowie 16057."
        val notification = Notification.Builder(
            applicationContext,
            TaxDonationReminderScheduler.CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FastRoute bez reklam")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        return try {
            manager.notify(TaxDonationReminderScheduler.NOTIFICATION_ID, notification)
            TaxDonationReminderScheduler.markSent(applicationContext)
            Result.success()
        } catch (exception: SecurityException) {
            Log.w(TAG, "Tax donation reminder not sent because Android denied notifications.", exception)
            TaxDonationReminderScheduler.markUnavailable(applicationContext)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "TaxDonationReminder"
    }
}
