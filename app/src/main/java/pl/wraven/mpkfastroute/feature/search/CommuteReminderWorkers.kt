package pl.wraven.mpkfastroute.feature.search

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.common.api.ApiException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.first
import pl.wraven.mpkfastroute.MainActivity
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.gtfs.GtfsServiceClock
import pl.wraven.mpkfastroute.core.live.DepartureEstimate
import pl.wraven.mpkfastroute.core.live.ServiceAlertsClient
import pl.wraven.mpkfastroute.core.location.LocationUnavailableException
import pl.wraven.mpkfastroute.core.settings.AppPreferences

private fun hasBackgroundLocationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@HiltWorker
class CommuteReminderCoordinatorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val appPreferences: AppPreferences,
    private val contextEngine: ContextEngine
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        if (!hasBackgroundLocationPermission(applicationContext)) {
            Log.w(TAG, "Commute reminders need background location permission.")
            return Result.failure()
        }

        val now = LocalDateTime.now()
        for (destination in SavedDestination.values()) {
            val schedule = when (destination) {
                SavedDestination.HOME -> appPreferences.homeSchedule.first()
                SavedDestination.WORK -> appPreferences.workSchedule.first()
            } ?: continue
            val targetExists = when (destination) {
                SavedDestination.HOME ->
                    appPreferences.homePlace.first() != null ||
                        appPreferences.homeStopId.first() != null
                SavedDestination.WORK ->
                    appPreferences.workPlace.first() != null ||
                        appPreferences.workStopId.first() != null
            }
            if (!targetExists) continue

            val arrivalTarget = schedule.nextOccurrence(now)
            val lastNotifiedOccurrence = when (destination) {
                SavedDestination.HOME ->
                    appPreferences.homeCommuteNotificationOccurrence.first()
                SavedDestination.WORK ->
                    appPreferences.workCommuteNotificationOccurrence.first()
            }
            if (lastNotifiedOccurrence == arrivalTarget.toString()) continue
            if (arrivalTarget.isAfter(now.plusSeconds(ROUTE_LOOKAHEAD_SECONDS))) continue

            val result = try {
                contextEngine.routeToSavedStop(destination, arrivalTarget)
            } catch (_: SecurityException) {
                Log.w(TAG, "Commute reminders cannot access the current location.")
                return Result.failure()
            } catch (_: LocationUnavailableException) {
                return Result.retry()
            } catch (_: ApiException) {
                return Result.retry()
            }

            if (result is QuickActionResult.JourneyFound) {
                val departureTarget = result.departureTarget ?: continue
                CommuteReminderScheduler.scheduleOccurrence(
                    context = applicationContext,
                    destination = destination,
                    arrivalTarget = arrivalTarget,
                    departureTarget = departureTarget
                )
            }
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "CommuteReminderPlan"
        const val ROUTE_LOOKAHEAD_SECONDS = 2 * 60 * 60L
    }
}

@HiltWorker
class CommuteStatusWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val appPreferences: AppPreferences,
    private val contextEngine: ContextEngine,
    private val database: AppDatabase,
    private val departuresRepository: pl.wraven.mpkfastroute.core.live.TtssDepartureRepository,
    private val serviceAlertsClient: ServiceAlertsClient
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val destination = inputData.getString(CommuteReminderScheduler.DESTINATION_KEY)
            ?.let { value ->
                SavedDestination.values().firstOrNull { it.name == value }
            } ?: return Result.failure()
        val arrivalTarget = inputData.getString(CommuteReminderScheduler.ARRIVAL_TARGET_KEY)
            ?.let(::parseDateTime)
            ?: return Result.failure()
        val workName = inputData.getString(CommuteReminderScheduler.OCCURRENCE_KEY)
            ?: return Result.failure()
        val notificationId = notificationId(destination)

        if (LocalDateTime.now() >= arrivalTarget) {
            cancelNotification(notificationId)
            return Result.success()
        }
        if (!hasBackgroundLocationPermission(applicationContext)) {
            Log.w(TAG, "Commute notification stopped: background location permission missing.")
            cancelNotification(notificationId)
            return Result.failure()
        }
        if (!hasNotificationPermission(applicationContext)) {
            Log.w(TAG, "Commute notification stopped: notification permission missing.")
            return Result.failure()
        }
        if (!scheduleStillMatches(destination, arrivalTarget)) {
            cancelNotification(notificationId)
            return Result.success()
        }

        val result = try {
            contextEngine.routeToSavedStop(destination, arrivalTarget)
        } catch (_: SecurityException) {
            cancelNotification(notificationId)
            return Result.failure()
        } catch (_: LocationUnavailableException) {
            return Result.retry()
        } catch (_: ApiException) {
            return Result.retry()
        }

        val journeyResult = result as? QuickActionResult.JourneyFound
        if (journeyResult == null) {
            val plannedDeparture = inputData.getString(
                CommuteReminderScheduler.DEPARTURE_TARGET_KEY
            )?.let(::parseDateTime)
            val now = LocalDateTime.now()
            if (plannedDeparture == null || !now.isBefore(plannedDeparture)) {
                cancelNotification(notificationId)
                return Result.success()
            }
            if (!now.isBefore(plannedDeparture.minusMinutes(15))) {
                val posted = postNotification(
                    notificationId = notificationId,
                    title = destination.notificationTitle(),
                    body = "Nie udało się potwierdzić trasy ani pobrać jej odjazdów live."
                )
                if (posted) markNotified(destination, arrivalTarget)
            }
            CommuteReminderScheduler.scheduleNextUpdate(
                applicationContext,
                workName,
                inputData,
                minOf(
                    CommuteReminderScheduler.UPDATE_INTERVAL_MINUTES,
                    roundedUpMinutes(Duration.between(now, plannedDeparture))
                )
            )
            return Result.success()
        }

        val departureTarget = journeyResult.departureTarget ?: return Result.failure()
        val now = LocalDateTime.now()
        val notificationTime = departureTarget.minusMinutes(NOTIFICATION_LEAD_MINUTES)
        val updatedInput = inputData.toBuilder()
            .putString(
                CommuteReminderScheduler.DEPARTURE_TARGET_KEY,
                departureTarget.toString()
            )
            .build()

        if (now.isBefore(notificationTime)) {
            cancelNotification(notificationId)
            val delayMinutes = minOf(
                CommuteReminderScheduler.UPDATE_INTERVAL_MINUTES,
                roundedUpMinutes(Duration.between(now, notificationTime))
            )
            CommuteReminderScheduler.scheduleNextUpdate(
                applicationContext,
                workName,
                updatedInput,
                delayMinutes
            )
            return Result.success()
        }

        val body = buildNotificationBody(journeyResult, departureTarget)
        val posted = postNotification(
            notificationId = notificationId,
            title = "${destination.notificationTitle()} · " +
                "odjazd ${departureTarget.format(TIME_FORMAT)}",
            body = body
        )
        if (!posted) return Result.failure()
        markNotified(destination, arrivalTarget)

        val minutesToDeparture = roundedUpMinutes(Duration.between(now, departureTarget))
        if (minutesToDeparture > 0) {
            CommuteReminderScheduler.scheduleNextUpdate(
                applicationContext,
                workName,
                updatedInput,
                minOf(CommuteReminderScheduler.UPDATE_INTERVAL_MINUTES, minutesToDeparture)
            )
        } else {
            cancelNotification(notificationId)
        }
        return Result.success()
    }

    private suspend fun scheduleStillMatches(
        destination: SavedDestination,
        arrivalTarget: LocalDateTime
    ): Boolean {
        val now = LocalDateTime.now()
        val schedule = when (destination) {
            SavedDestination.HOME -> appPreferences.homeSchedule.first()
            SavedDestination.WORK -> appPreferences.workSchedule.first()
        } ?: return false
        return schedule.nextOccurrence(now) == arrivalTarget
    }

    private suspend fun markNotified(
        destination: SavedDestination,
        arrivalTarget: LocalDateTime
    ) {
        when (destination) {
            SavedDestination.HOME ->
                appPreferences.markHomeCommuteNotificationOccurrence(arrivalTarget.toString())
            SavedDestination.WORK ->
                appPreferences.markWorkCommuteNotificationOccurrence(arrivalTarget.toString())
        }
    }

    private suspend fun buildNotificationBody(
        journeyResult: QuickActionResult.JourneyFound,
        departureTarget: LocalDateTime
    ): String {
        val transitSteps = journeyResult.journey.steps
            .filterIsInstance<JourneyStep.Transit>()
        val firstTransit = transitSteps.firstOrNull()
        val routeShortNames = transitSteps
            .mapTo(mutableSetOf()) { it.connection.routeShortName }
        val routeIds = database.routeDao().getAll()
            .filter { it.routeShortName in routeShortNames }
            .mapTo(mutableSetOf()) { it.routeId }
        val stopIds = transitSteps.flatMapTo(mutableSetOf()) {
            listOf(it.connection.departureStopId, it.connection.arrivalStopId)
        }
        val serviceAlerts = serviceAlertsClient.getAlerts(
            routeIds = routeIds,
            routeShortNames = routeShortNames,
            stopIds = stopIds
        )
        val disruptions = when {
            serviceAlerts.alerts.isNotEmpty() -> {
                val alertText = serviceAlerts.alerts.take(MAX_ALERTS_IN_NOTIFICATION)
                    .joinToString("; ") { alert ->
                        val detail = alert.description?.takeIf(String::isNotBlank)
                        if (detail == null) alert.title else "${alert.title}: $detail"
                    }
                "Utrudnienia: $alertText" +
                    if (serviceAlerts.isComplete) "" else " (niepełne dane)."
            }

            serviceAlerts.isComplete -> "Brak zgłoszonych utrudnień na trasie."
            else -> "Nie udało się sprawdzić wszystkich źródeł utrudnień."
        }
        val departureLines = if (firstTransit == null) {
            "Brak odjazdu MPK na wybranej trasie."
        } else {
            val currentTime = LocalDateTime.now()
            val currentServiceTime = GtfsServiceClock.from(currentTime)
            val direction = database.tripDao()
                .getById(firstTransit.connection.tripId)
                ?.tripHeadsign
                .orEmpty()
            val liveResult = try {
                departuresRepository.getDepartures(
                    stopId = firstTransit.connection.departureStopId,
                    serviceDate = currentServiceTime.serviceDate,
                    startTimeSeconds = currentServiceTime.timeSeconds,
                    nowTimeSeconds = currentTime.toLocalTime().toSecondOfDay()
                )
            } catch (_: IOException) {
                null
            } catch (_: SQLiteException) {
                null
            }
            val nextDepartures = liveResult?.departures
                ?.filter { estimate ->
                    estimate.routeShortName == firstTransit.connection.routeShortName &&
                        (direction.isBlank() || normalize(estimate.direction) == normalize(direction)) &&
                        estimate.scheduledDepartureTime >= currentServiceTime.timeSeconds
                }
                ?.sortedBy(DepartureEstimate::estimatedDepartureTime)
                ?.take(MAX_DEPARTURES_IN_NOTIFICATION)
                .orEmpty()
            when {
                nextDepartures.isNotEmpty() -> nextDepartures.joinToString(
                    prefix = "Odjazdy: ",
                    separator = " · "
                ) { estimate ->
                    formatDeparture(estimate, currentServiceTime.serviceDate)
                }

                liveResult == null ->
                    "Odjazdy live niedostępne; planowany odjazd " +
                        departureTarget.format(TIME_FORMAT) + "."

                else ->
                    "Brak dalszych odjazdów dla linii " +
                        firstTransit.connection.routeShortName + " w najbliższych 2 godzinach."
            }
        }
        return "Odjazd za ${roundedUpMinutes(Duration.between(LocalDateTime.now(), departureTarget))} min.\n" +
            "$departureLines\n$disruptions"
    }

    private fun formatDeparture(
        estimate: DepartureEstimate,
        serviceDate: java.time.LocalDate
    ): String {
        val departureTime = serviceDate.atStartOfDay()
            .plusSeconds(estimate.estimatedDepartureTime.toLong())
            .format(TIME_FORMAT)
        val liveLabel = if (estimate.isLive) "live" else "rozkład"
        val delay = estimate.delaySeconds?.let { seconds ->
            when {
                seconds > 0 -> " +${seconds / 60} min"
                seconds < 0 -> " ${seconds / 60} min"
                else -> " punktualnie"
            }
        }.orEmpty()
        return "$departureTime $liveLabel$delay"
    }

    private fun postNotification(notificationId: Int, title: String, body: String): Boolean {
        if (!hasNotificationPermission(applicationContext)) return false
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CommuteReminderScheduler.CHANNEL_ID,
                "Powiadomienia o dojeździe",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Odjazdy live i utrudnienia na zapisanych trasach."
            }
        )
        if (
            !manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(CommuteReminderScheduler.CHANNEL_ID)
                ?.importance == NotificationManager.IMPORTANCE_NONE
        ) {
            return false
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(
            applicationContext,
            CommuteReminderScheduler.CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
        manager.notify(notificationId, notification)
        return true
    }

    private fun cancelNotification(notificationId: Int) {
        applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(notificationId)
    }

    private fun notificationId(destination: SavedDestination): Int = when (destination) {
        SavedDestination.HOME -> CommuteReminderScheduler.HOME_NOTIFICATION_ID
        SavedDestination.WORK -> CommuteReminderScheduler.WORK_NOTIFICATION_ID
    }

    private fun parseDateTime(value: String): LocalDateTime? = try {
        LocalDateTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun normalize(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)

    private fun roundedUpMinutes(duration: Duration): Long =
        ((duration.seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE)
            .coerceAtLeast(1)

    private fun SavedDestination.notificationTitle(): String = when (this) {
        SavedDestination.HOME -> "Do domu"
        SavedDestination.WORK -> "Do pracy"
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        val hasRuntimePermission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelEnabled =
            manager.getNotificationChannel(CommuteReminderScheduler.CHANNEL_ID)
                ?.importance != NotificationManager.IMPORTANCE_NONE
        return hasRuntimePermission && manager.areNotificationsEnabled() && channelEnabled
    }

    private companion object {
        const val TAG = "CommuteStatusWorker"
        const val MAX_ALERTS_IN_NOTIFICATION = 2
        const val MAX_DEPARTURES_IN_NOTIFICATION = 2
        const val SECONDS_PER_MINUTE = 60L
        val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
