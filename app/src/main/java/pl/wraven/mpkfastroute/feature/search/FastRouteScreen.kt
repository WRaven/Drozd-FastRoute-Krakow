package pl.wraven.mpkfastroute.feature.search

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeDefaults
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt
import pl.wraven.mpkfastroute.core.audio.RadioStation
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Haversine
import pl.wraven.mpkfastroute.core.csa.JourneyTimeMode
import pl.wraven.mpkfastroute.core.csa.JourneyTimePlan
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.live.DepartureEstimate
import pl.wraven.mpkfastroute.core.live.LiveDeparturesResult
import pl.wraven.mpkfastroute.core.location.GeoPlace
import pl.wraven.mpkfastroute.core.settings.DestinationSchedule
import pl.wraven.mpkfastroute.core.settings.DestinationShortcutNames
import pl.wraven.mpkfastroute.core.update.AppUpdateInstaller
import pl.wraven.mpkfastroute.core.update.GitHubUpdateChecker
import pl.wraven.mpkfastroute.core.update.UpdateCheckResult
import pl.wraven.mpkfastroute.core.weather.OpenMeteoClient
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import pl.wraven.mpkfastroute.R

private const val OLA_SUPPORT_INFO_URL = "https://fsmm.pl/partner/aleksandra-lachnowicz/"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FastRouteScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appView = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsVisible by remember { mutableStateOf(false) }
    var databaseUpdateDialogVisible by rememberSaveable { mutableStateOf(false) }
    var taxDonationReminderEnabled by remember {
        mutableStateOf(TaxDonationReminderScheduler.isEnabled(context))
    }
    var taxDonationReminderSent by remember {
        mutableStateOf(TaxDonationReminderScheduler.wasSent(context))
    }
    var taxDonationReminderDialogVisible by rememberSaveable { mutableStateOf(false) }
    var bugReportVisible by rememberSaveable { mutableStateOf(false) }
    var radioSelectionVisible by remember { mutableStateOf(false) }
    var radioManagementVisible by remember { mutableStateOf(false) }
    var scheduleTarget by remember { mutableStateOf<SavedDestination?>(null) }
    var scheduleSavesPlace by remember { mutableStateOf(false) }
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var routeTimePlanVisible by remember { mutableStateOf(false) }
    var weatherVisible by remember { mutableStateOf(false) }
    var liveMapVisible by remember { mutableStateOf(false) }
    var startupLocationRequested by rememberSaveable { mutableStateOf(false) }
    var backgroundLocationHelpVisible by remember { mutableStateOf(false) }
    var reminderPermissionsRequested by rememberSaveable { mutableStateOf(false) }
    var permissionStateRevision by remember { mutableStateOf(0) }
    val canInstallAppUpdates = remember(permissionStateRevision) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            pendingLocationAction?.invoke()
        } else {
            viewModel.showMessage("Aby użyć tej akcji, zezwól na dostęp do lokalizacji.")
        }
        pendingLocationAction = null
    }

    val runWithLocationPermission: (() -> Unit) -> Unit = { action ->
        val hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation =
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (hasFineLocation || hasCoarseLocation) {
            action()
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun showTaxDonationReminderIfDue() {
        if (TaxDonationReminderScheduler.markShownIfDue(context)) {
            taxDonationReminderEnabled = false
            taxDonationReminderSent = true
            taxDonationReminderDialogVisible = true
        }
    }

    fun scheduleTaxDonationReminder() {
        when (TaxDonationReminderScheduler.schedule(context)) {
            TaxDonationReminderScheduleResult.SCHEDULED -> {
                taxDonationReminderEnabled = true
                viewModel.showMessage("Jednorazowe przypomnienie w aplikacji ustawiono.")
            }
            TaxDonationReminderScheduleResult.ALREADY_SCHEDULED -> {
                taxDonationReminderEnabled = true
            }
            TaxDonationReminderScheduleResult.ALREADY_SENT -> {
                taxDonationReminderSent = true
                viewModel.showMessage("Przypomnienie o 1,5% podatku zostało już pokazane.")
            }
        }
    }

    fun enableTaxDonationReminder() {
        scheduleTaxDonationReminder()
    }

    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            CommuteReminderScheduler.synchronize(context, enabled = true)
            permissionStateRevision += 1
        } else {
            CommuteReminderScheduler.synchronize(context, enabled = false)
            backgroundLocationHelpVisible = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestBackgroundLocationPermission(
                context = context,
                launcher = backgroundLocationPermissionLauncher,
                onOpenSettingsRequired = { backgroundLocationHelpVisible = true },
                onReady = {
                    CommuteReminderScheduler.synchronize(context, enabled = true)
                    permissionStateRevision += 1
                }
            )
        } else {
            CommuteReminderScheduler.synchronize(context, enabled = false)
            permissionStateRevision += 1
            viewModel.showMessage("Bez uprawnienia do powiadomień przypomnienia o dojeździe nie będą działać.")
        }
    }

    fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestBackgroundLocationPermission(
                context = context,
                launcher = backgroundLocationPermissionLauncher,
                onOpenSettingsRequired = { backgroundLocationHelpVisible = true },
                onReady = {
                    CommuteReminderScheduler.synchronize(context, enabled = true)
                    permissionStateRevision += 1
                }
            )
        }
    }

    fun requestCommuteReminderPermissions() {
        val hasForegroundLocation =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (hasForegroundLocation) {
            requestNotificationPermission()
        } else {
            runWithLocationPermission(::requestNotificationPermission)
        }
    }

    val hasSavedCommuteSchedule =
        (state.hasHomeDestination && state.homeSchedule != null) ||
            (state.hasWorkDestination && state.workSchedule != null)
    val commuteRemindersEnabled = remember(permissionStateRevision, hasSavedCommuteSchedule) {
        hasSavedCommuteSchedule && hasCommuteReminderPermissions(context)
    }

    LaunchedEffect(Unit) {
        TaxDonationReminderScheduler.cancelLegacySystemReminder(context)
        showTaxDonationReminderIfDue()
    }

    LaunchedEffect(taxDonationReminderEnabled, taxDonationReminderSent) {
        if (taxDonationReminderEnabled && !taxDonationReminderSent) {
            val delayMillis = taxDonationReminderDelayMillis()
            if (delayMillis > 0) delay(delayMillis)
            showTaxDonationReminderIfDue()
        }
    }

    LaunchedEffect(hasSavedCommuteSchedule) {
        CommuteReminderScheduler.synchronize(context, commuteRemindersEnabled)
        if (
            hasSavedCommuteSchedule &&
            !commuteRemindersEnabled &&
            !reminderPermissionsRequested
        ) {
            reminderPermissionsRequested = true
            requestCommuteReminderPermissions()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionStateRevision += 1
        taxDonationReminderEnabled = TaxDonationReminderScheduler.isEnabled(context)
        taxDonationReminderSent = TaxDonationReminderScheduler.wasSent(context)
        showTaxDonationReminderIfDue()
        viewModel.refreshServiceNews()
        viewModel.refreshLiveServiceAlerts()
        CommuteReminderScheduler.synchronize(
            context,
            hasSavedCommuteSchedule && hasCommuteReminderPermissions(context)
        )
    }

    LaunchedEffect(state.isScheduleEmpty) {
        if (!state.isScheduleEmpty && !startupLocationRequested) {
            startupLocationRequested = true
            runWithLocationPermission(viewModel::useCurrentLocationIfOriginEmpty)
        }
    }

    fun requestScheduleSetup(destination: SavedDestination) {
        if (state.destinationPlace == null && state.destinationStop == null) {
            viewModel.showMessage("Najpierw wybierz adres lub przystanek docelowy.")
        } else {
            scheduleTarget = destination
            scheduleSavesPlace = true
        }
    }

    fun requestScheduleEdit(destination: SavedDestination) {
        scheduleTarget = destination
        scheduleSavesPlace = false
    }

    fun openExternalUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage("Nie znaleziono aplikacji do otwarcia tego odnośnika.")
        } catch (_: SecurityException) {
            viewModel.showMessage("Nie można otworzyć tego odnośnika.")
        }
    }

    fun requestAppUpdateInstallPermission() {
        if (canInstallAppUpdates) return
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage("Nie można otworzyć ustawień instalacji aplikacji.")
        } catch (_: SecurityException) {
            viewModel.showMessage("Brak dostępu do ustawień instalacji aplikacji.")
        }
    }

    fun installDownloadedAppUpdate() {
        val apkPath = state.downloadedAppUpdatePath
        if (apkPath == null) {
            viewModel.showMessage("Najpierw pobierz plik aktualizacji.")
            return
        }
        if (!canInstallAppUpdates) {
            requestAppUpdateInstallPermission()
            return
        }
        val apkFile = File(apkPath)
        if (!apkFile.isFile || !apkFile.canRead()) {
            viewModel.clearDownloadedAppUpdate()
            viewModel.showMessage("Pobrany plik APK jest niedostępny. Pobierz go ponownie.")
            return
        }
        try {
            context.startActivity(AppUpdateInstaller.createInstallIntent(context, apkFile))
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage("Nie znaleziono systemowego instalatora pakietów.")
        } catch (_: SecurityException) {
            viewModel.showMessage("Android zablokował otwarcie instalatora aplikacji.")
        } catch (exception: IllegalArgumentException) {
            viewModel.showMessage("Nie można udostępnić pliku APK: ${exception.message.orEmpty()}")
        }
    }

    LaunchedEffect(state.statusMessage) {
        val message = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    state.databaseUpdateProgressPercent?.let { progressPercent ->
                        DatabaseUpdateIndicator(progressPercent) {
                            databaseUpdateDialogVisible = true
                        }
                    }
                    TextButton(onClick = { settingsVisible = true }) {
                        Text("Ustawienia")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
        if (state.isScheduleEmpty) {
            OnboardingContent(
                progressMessage = state.progressMessage,
                isImporting = state.isImporting,
                onRefresh = viewModel::refreshSchedule,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            )
        } else {
            SearchContent(
                state = state,
                onUseCurrentLocation = {
                    runWithLocationPermission(viewModel::useCurrentLocation)
                },
                onOriginQueryChange = viewModel::setOriginQuery,
                onSelectOrigin = viewModel::selectOrigin,
                onSelectOriginAddress = viewModel::selectOriginAddress,
                onDestinationQueryChange = viewModel::setDestinationQuery,
                onSelectDestination = viewModel::selectDestination,
                onSelectAddress = viewModel::selectDestinationAddressAndSearch,
                onSearch = viewModel::searchRoute,
                onGoHome = { runWithLocationPermission(viewModel::routeHome) },
                onGoToWork = { runWithLocationPermission(viewModel::routeToWork) },
                onEditWorkSchedule = { requestScheduleEdit(SavedDestination.WORK) },
                onSetHome = { requestScheduleSetup(SavedDestination.HOME) },
                onSetWork = { requestScheduleSetup(SavedDestination.WORK) },
                onCustomRoute = viewModel::routeCustom,
                onToggleRadio = {
                    if (state.isRadioPlaying) {
                        viewModel.stopRadio()
                    } else {
                        viewModel.playLastSelectedRadio()
                    }
                },
                onChooseRadio = { radioSelectionVisible = true },
                onManageRadio = { radioManagementVisible = true },
                onSaveCustomRoute = viewModel::saveCurrentRouteAsCustom,
                onEditRoutePlan = { routeTimePlanVisible = true },
                onOpenServiceAlerts = {
                    openExternalUrl("https://ztp.krakow.pl/kmk/komunikaty")
                },
                onOpenMpKX = {
                    openExternalUrl("https://x.com/MPK_Krakow")
                },
                onOpenServiceNews = ::openExternalUrl,
                onRefreshServiceNews = {
                    viewModel.refreshServiceNews(force = true)
                    viewModel.refreshLiveServiceAlerts(force = true)
                },
                onOpenWeather = {
                    weatherVisible = true
                    runWithLocationPermission(viewModel::loadWeather)
                },
                onOpenLiveMap = {
                    liveMapVisible = true
                    viewModel.clearLiveMapUserLocation()
                    runWithLocationPermission(viewModel::locateUserOnLiveMap)
                },
                remindersEnabled = commuteRemindersEnabled,
                onEnableCommuteReminders = ::requestCommuteReminderPermissions,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            )
        }
    }

    if (databaseUpdateDialogVisible) {
        val progressPercent = state.databaseUpdateProgressPercent
        AlertDialog(
            onDismissRequest = { databaseUpdateDialogVisible = false },
            title = { Text("Aktualizacja danych") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        state.progressMessage.ifBlank {
                            if (progressPercent == null) {
                                "Aktualizacja nie jest już aktywna."
                            } else {
                                "Przygotowywanie aktualizacji..."
                            }
                        }
                    )
                    if (progressPercent != null) {
                        Text("Postęp: $progressPercent%")
                        LinearProgressIndicator(
                            progress = { progressPercent.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text("Możesz zamknąć to okno — aktualizacja będzie kontynuowana w tle.")
                    Text(
                        if (state.isScheduleEmpty) {
                            "Podczas pierwszego pobierania wyszukiwanie tras będzie dostępne " +
                                "po przygotowaniu rozkładu."
                        } else {
                            "Dostępny rozkład pozostaje używany podczas aktualizacji. " +
                                "Możesz nadal korzystać z aplikacji i wyszukiwać trasy."
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { databaseUpdateDialogVisible = false }) {
                    Text("Zamknij")
                }
            }
        )
    }

    if (settingsVisible) {
        SettingsDialog(
            shortcutNames = state.destinationShortcutNames,
            isSavingShortcutNames = state.isSavingShortcutNames,
            shortcutNamesSaveError = state.shortcutNamesSaveError,
            onSaveShortcutNames = viewModel::saveDestinationShortcutNames,
            progressMessage = state.progressMessage,
            isImporting = state.isImporting ||
                state.databaseUpdateProgressPercent != null,
            homeStopName = state.homePlace?.label
                ?: state.stops.firstOrNull { it.stopId == state.homeStopId }
                    ?.let { StopNameGrouper.mainName(it.stopName) },
            workStopName = state.workPlace?.label
                ?: state.stops.firstOrNull { it.stopId == state.workStopId }
                    ?.let { StopNameGrouper.mainName(it.stopName) },
            homeSchedule = state.homeSchedule,
            workSchedule = state.workSchedule,
            diagnosticEntries = state.diagnosticEntries,
            updateCheckMessage = state.updateCheckMessage,
            updateCheckResult = state.updateCheckResult,
            isCheckingUpdates = state.isCheckingUpdates,
            isDownloadingAppUpdate = state.isDownloadingAppUpdate,
            appUpdateDownloadProgressPercent = state.appUpdateDownloadProgressPercent,
            appUpdateDownloadError = state.appUpdateDownloadError,
            isAppUpdateDownloaded = state.downloadedAppUpdatePath != null &&
                state.downloadedAppUpdateVersion == state.updateCheckResult?.latestVersion,
            canInstallAppUpdates = canInstallAppUpdates,
            onRefresh = viewModel::refreshSchedule,
            onCheckUpdates = viewModel::checkForUpdates,
            onDownloadUpdate = {
                state.updateCheckResult?.takeIf { it.updateAvailable }
                    ?.let(viewModel::downloadAppUpdate)
            },
            onInstallUpdate = ::installDownloadedAppUpdate,
            onRequestInstallPermission = ::requestAppUpdateInstallPermission,
            onOpenRelease = ::openExternalUrl,
            onOpenLatestRelease = {
                openExternalUrl(GitHubUpdateChecker.RELEASES_PAGE)
            },
            onOpenCharityInfo = {
                openExternalUrl(OLA_SUPPORT_INFO_URL)
            },
            taxDonationReminderEnabled = taxDonationReminderEnabled,
            taxDonationReminderSent = taxDonationReminderSent,
            onEnableTaxDonationReminder = ::enableTaxDonationReminder,
            onCancelTaxDonationReminder = {
                TaxDonationReminderScheduler.cancel(context)
                taxDonationReminderEnabled = false
                viewModel.showMessage("Anulowano przypomnienie o 1,5% podatku.")
            },
            onContactAuthor = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:lukasz@drozd.cc"))
                    )
                } catch (_: ActivityNotFoundException) {
                    viewModel.showMessage("Nie znaleziono aplikacji pocztowej.")
                } catch (_: SecurityException) {
                    viewModel.showMessage("Nie można otworzyć aplikacji pocztowej.")
                }
            },
            onReportBug = {
                settingsVisible = false
                bugReportVisible = true
            },
            onEditHomeSchedule = { requestScheduleEdit(SavedDestination.HOME) },
            onEditWorkSchedule = { requestScheduleEdit(SavedDestination.WORK) },
            hasHomeDestination = state.hasHomeDestination,
            hasWorkDestination = state.hasWorkDestination,
            onClearDiagnostics = viewModel::clearDiagnostics,
            onDiagnosticsCopied = viewModel::diagnosticsCopied,
            onDismiss = { settingsVisible = false }
        )
    }

    if (taxDonationReminderDialogVisible) {
        AlertDialog(
            onDismissRequest = { taxDonationReminderDialogVisible = false },
            title = { Text("FastRoute bez reklam") },
            text = {
                Text(
                    "Jeśli podoba Ci się aplikacja, możesz dobrowolnie przekazać 1,5% " +
                        "podatku na DT u Oli w Legionowie. KRS: 0000270261. " +
                        "Cel szczegółowy: DT u Oli w Legionowie 16057."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        taxDonationReminderDialogVisible = false
                        openExternalUrl(OLA_SUPPORT_INFO_URL)
                    }
                ) {
                    Text("Poznaj inicjatywę")
                }
            },
            dismissButton = {
                TextButton(onClick = { taxDonationReminderDialogVisible = false }) {
                    Text("Zamknij")
                }
            }
        )
    }

    if (bugReportVisible) {
        BugReportDialog(appView = appView, onDismiss = { bugReportVisible = false })
    }

    state.updatePrompt?.takeIf { !settingsVisible && !bugReportVisible }?.let { update ->
        UpdateAvailableDialog(
            update = update,
            isDownloading = state.isDownloadingAppUpdate,
            downloadProgressPercent = state.appUpdateDownloadProgressPercent,
            downloadError = state.appUpdateDownloadError,
            isDownloaded = state.downloadedAppUpdatePath != null &&
                state.downloadedAppUpdateVersion == update.latestVersion,
            canInstall = canInstallAppUpdates,
            onDownload = { viewModel.downloadAppUpdate(update) },
            onInstall = ::installDownloadedAppUpdate,
            onRequestInstallPermission = ::requestAppUpdateInstallPermission,
            onOpenRelease = { openExternalUrl(update.releaseUrl) },
            onSkip = viewModel::skipUpdatePrompt,
            onDismiss = viewModel::dismissUpdatePrompt
        )
    }

    if (radioSelectionVisible) {
        ModalBottomSheet(onDismissRequest = { radioSelectionVisible = false }) {
            state.radioStations.forEach { station ->
                ListItem(
                    headlineContent = { Text(station.name) },
                    supportingContent = { Text(station.streamUrl) },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            radioSelectionVisible = false
                            viewModel.playRadioStation(station.id)
                        },
                        onLongClick = {
                            radioSelectionVisible = false
                            radioManagementVisible = true
                        }
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (weatherVisible) {
        ModalBottomSheet(onDismissRequest = { weatherVisible = false }) {
            WeatherForecastSheet(
                forecast = state.weatherForecast,
                isLoading = state.isLoadingWeather,
                error = state.weatherError,
                onRetry = { runWithLocationPermission(viewModel::loadWeather) },
                onOpenAttribution = { openExternalUrl(OpenMeteoClient.ATTRIBUTION_URL) },
                onOpenLicense = { openExternalUrl(OpenMeteoClient.LICENSE_URL) }
            )
        }
    }

    if (liveMapVisible) {
        LiveVehicleMapDialog(
            snapshot = state.liveVehicleSnapshot,
            isLoading = state.isLoadingLiveVehicles,
            error = state.liveVehicleError,
            userLocation = state.liveMapUserLocation,
            userLocationError = state.liveMapLocationError,
            stops = state.stops,
            selectedStop = state.selectedLiveMapStop,
            stopTimetable = state.liveMapStopTimetable,
            isLoadingStopTimetable = state.isLoadingLiveMapStopTimetable,
            stopTimetableError = state.liveMapStopTimetableError,
            selectedVehicle = state.selectedLiveVehicle,
            selectedVehicleTrip = state.selectedLiveVehicleTrip,
            selectedVehicleTripUpdate = state.selectedLiveVehicleTripUpdate,
            isLoadingVehicleTrip = state.isLoadingSelectedLiveVehicleTrip,
            isLoadingVehicleTripUpdate = state.isLoadingSelectedLiveVehicleTripUpdate,
            vehicleTripError = state.selectedLiveVehicleTripError,
            vehicleTripUpdateError = state.selectedLiveVehicleTripUpdateError,
            onRefresh = viewModel::refreshLiveVehiclePositions,
            onLocateUser = {
                runWithLocationPermission(viewModel::locateUserOnLiveMap)
            },
            onSelectStop = viewModel::loadLiveMapStop,
            onSelectVehicle = viewModel::selectLiveVehicle,
            onClearSelectedStop = viewModel::clearLiveMapStop,
            onClearSelectedVehicle = viewModel::clearSelectedLiveVehicle,
            onDismiss = {
                liveMapVisible = false
                viewModel.clearLiveMapStop()
                viewModel.clearSelectedLiveVehicle()
            }
        )
    }

    if (radioManagementVisible) {
        RadioManagementDialog(
            stations = state.radioStations,
            onSave = viewModel::saveRadioStations,
            onDismiss = { radioManagementVisible = false }
        )
    }

    scheduleTarget?.let { destination ->
        val initialSchedule = when (destination) {
            SavedDestination.HOME -> state.homeSchedule
            SavedDestination.WORK -> state.workSchedule
        } ?: DestinationSchedule.DEFAULT
        DestinationScheduleDialog(
            destinationName = when (destination) {
                SavedDestination.HOME -> state.destinationShortcutNames.home
                SavedDestination.WORK -> state.destinationShortcutNames.work
            },
            initialSchedule = initialSchedule,
            onSave = { schedule ->
                when (destination) {
                    SavedDestination.HOME -> if (scheduleSavesPlace) {
                        viewModel.saveSelectedStopAsHome(schedule)
                    } else {
                        viewModel.saveHomeSchedule(schedule)
                    }

                    SavedDestination.WORK -> if (scheduleSavesPlace) {
                        viewModel.saveSelectedStopAsWork(schedule)
                    } else {
                        viewModel.saveWorkSchedule(schedule)
                    }
                }
                scheduleTarget = null
            },
            onDismiss = { scheduleTarget = null }
        )
    }

    if (routeTimePlanVisible) {
        JourneyTimePlanDialog(
            currentPlan = state.routeTimePlan,
            onSave = { plan ->
                viewModel.setRouteTimePlan(plan)
                routeTimePlanVisible = false
            },
            onDismiss = { routeTimePlanVisible = false }
        )
    }

    if (backgroundLocationHelpVisible) {
        AlertDialog(
            onDismissRequest = { backgroundLocationHelpVisible = false },
            title = { Text("Lokalizacja w tle") },
            text = {
                Text(
                    "Aby wyznaczać trasę przed odjazdem i aktualizować powiadomienie co 5 minut, " +
                        "ustaw dla aplikacji lokalizację „Zawsze”. GPS jest używany lokalnie."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        backgroundLocationHelpVisible = false
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        } catch (_: ActivityNotFoundException) {
                            viewModel.showMessage("Nie można otworzyć ustawień aplikacji.")
                        } catch (_: SecurityException) {
                            viewModel.showMessage("Nie można otworzyć ustawień aplikacji.")
                        }
                    }
                ) {
                    Text("Otwórz ustawienia")
                }
            },
            dismissButton = {
                TextButton(onClick = { backgroundLocationHelpVisible = false }) {
                    Text("Później")
                }
            }
        )
    }
}

@Composable
private fun DatabaseUpdateIndicator(progressPercent: Int, onClick: () -> Unit) {
    val progress = progressPercent.coerceIn(0, 100)
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(36.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Aktualizacja bazy rozkładów: $progress%"
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.size(34.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OnboardingContent(
    progressMessage: String,
    isImporting: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pobieranie rozkładu ZTP Kraków",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(16.dp))
        if (progressMessage.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(progressMessage)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRefresh, enabled = !isImporting) {
            Text(if (isImporting) "Pobieranie..." else "Spróbuj ponownie")
        }
    }
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onUseCurrentLocation: () -> Unit,
    onOriginQueryChange: (String) -> Unit,
    onSelectOrigin: (StopGroup) -> Unit,
    onSelectOriginAddress: (GeoPlace) -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    onSelectDestination: (StopGroup) -> Unit,
    onSelectAddress: (GeoPlace) -> Unit,
    onSearch: () -> Unit,
    onGoHome: () -> Unit,
    onGoToWork: () -> Unit,
    onEditWorkSchedule: () -> Unit,
    onSetHome: () -> Unit,
    onSetWork: () -> Unit,
    onCustomRoute: () -> Unit,
    onToggleRadio: () -> Unit,
    onChooseRadio: () -> Unit,
    onManageRadio: () -> Unit,
    onSaveCustomRoute: () -> Unit,
    onEditRoutePlan: () -> Unit,
    onOpenServiceAlerts: () -> Unit,
    onOpenMpKX: () -> Unit,
    onOpenServiceNews: (String) -> Unit,
    onRefreshServiceNews: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenLiveMap: () -> Unit,
    remindersEnabled: Boolean,
    onEnableCommuteReminders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stopGroups = remember(state.stops) { StopNameGrouper.group(state.stops) }
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var isOriginFieldFocused by remember { mutableStateOf(false) }
    var isDestinationFieldFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val originCardBringIntoViewRequester = remember { BringIntoViewRequester() }
    val destinationCardBringIntoViewRequester = remember { BringIntoViewRequester() }
    val destinationSuggestionsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var wasKeyboardVisible by remember { mutableStateOf(false) }
    var expandedDepartureTripId by remember { mutableStateOf<String?>(null) }
    var showOriginStopPicker by remember(state.originPlace) { mutableStateOf(false) }
    var showDestinationStopPicker by remember(state.destinationPlace) {
        mutableStateOf(false)
    }

    LaunchedEffect(state.journeyOptions, state.journey) {
        expandedDepartureTripId = null
    }

    LaunchedEffect(
        isKeyboardVisible,
        isOriginFieldFocused,
        isDestinationFieldFocused,
        state.destinationPlace == null
    ) {
        if (isKeyboardVisible) {
            when {
                isDestinationFieldFocused -> {
                    if (state.destinationPlace == null) {
                        destinationSuggestionsBringIntoViewRequester.bringIntoView()
                    } else {
                        destinationCardBringIntoViewRequester.bringIntoView()
                    }
                }
                isOriginFieldFocused ->
                    originCardBringIntoViewRequester.bringIntoView()
            }
        } else if (wasKeyboardVisible) {
            scrollState.animateScrollTo(0)
        }
        wasKeyboardVisible = isKeyboardVisible
    }

    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isKeyboardVisible) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(originCardBringIntoViewRequester),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                Text("Szybkie akcje", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                            onClick = onOpenLiveMap,
                            enabled = !state.isImporting,
                            label = { Text("🗺 Live") }
                    )
                    AssistChip(
                            onClick = onOpenWeather,
                            enabled = !state.isLoadingWeather,
                            label = {
                                Text(if (state.isLoadingWeather) "🌤 Ładowanie..." else "🌤 Pogoda")
                            }
                        )
                        AssistChip(
                        onClick = if (state.hasHomeDestination) onGoHome else onSetHome,
                        enabled = !state.isSearching && !state.isImporting,
                        label = {
                            Text(
                                "🏠 " + if (state.hasHomeDestination) {
                                    state.destinationShortcutNames.home
                                } else {
                                    "Ustaw: ${state.destinationShortcutNames.home}"
                                }
                            )
                        }
                    )
                    AssistChip(
                        onClick = if (state.hasWorkDestination) onGoToWork else onSetWork,
                        enabled = !state.isSearching && !state.isImporting,
                        label = {
                            Text(
                                "🏢 " + if (state.hasWorkDestination) {
                                    state.destinationShortcutNames.work
                                } else {
                                    "Ustaw: ${state.destinationShortcutNames.work}"
                                }
                            )
                        }
                    )
                    if (state.hasWorkDestination) {
                        AssistChip(
                            onClick = onEditWorkSchedule,
                            enabled = !state.isImporting,
                            label = {
                                Text("🕒 Harmonogram: ${state.destinationShortcutNames.work}")
                            }
                        )
                    }
                    AssistChip(
                        onClick = onCustomRoute,
                        enabled = !state.isSearching && !state.isImporting,
                        label = {
                            Text(if (state.customRoute == null) "Własny · zapisz trasę" else "Własny")
                        }
                    )
                    AssistChip(
                        onClick = onOpenServiceAlerts,
                        enabled = !state.isImporting,
                        label = { Text("📢 Komunikaty MPK") }
                    )
                    AssistChip(
                        onClick = onOpenMpKX,
                        enabled = !state.isImporting,
                        label = { Text("X: MPK Kraków") }
                    )
                    if (state.homeSchedule != null || state.workSchedule != null) {
                        AssistChip(
                            onClick = onEnableCommuteReminders,
                            enabled = !state.isImporting,
                            label = {
                                Text(
                                    if (remindersEnabled) "🔔 Powiadomienia aktywne"
                                    else "🔔 Włącz powiadomienia"
                                )
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleRadio,
                        enabled = !state.isSearching,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (state.isRadioPlaying) "■ Zatrzymaj radio"
                            else "▶ Uruchom radio"
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onChooseRadio) {
                        Text(
                            state.radioStations
                                .firstOrNull { it.id == state.lastSelectedRadioId }
                                ?.name ?: "Wybierz"
                        )
                    }
                    TextButton(onClick = onManageRadio) {
                        Text("⚙")
                    }
                }
                    ServiceNewsTicker(
                        state = state,
                        onOpenAlerts = onOpenServiceAlerts,
                        onOpenArticle = onOpenServiceNews,
                        onRefresh = onRefreshServiceNews
                    )
                }
            }
        }
        if (state.isImporting && state.progressMessage.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.progressMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (state.isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        if (!isKeyboardVisible) {
            Text("Znajdź połączenie", style = MaterialTheme.typography.titleLarge)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Początek",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onUseCurrentLocation,
                        enabled = !state.isSearching && !state.isImporting
                    ) {
                        Text("📍 Moja lokalizacja")
                    }
                }
                OutlinedTextField(
                    value = state.originQuery,
                    onValueChange = onOriginQueryChange,
                    label = { Text("Adres lub przystanek") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isOriginFieldFocused = it.isFocused }
                )
                if (
                    state.originPlace == null &&
                    isKeyboardVisible &&
                    isOriginFieldFocused
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddressSuggestionPicker(
                            query = state.originQuery,
                            recentPlaces = state.recentAddressPlaces,
                            candidates = state.originAddressCandidates,
                            isSearching = state.isSearchingOriginAddresses,
                            onSelect = {
                                keyboardController?.hide()
                                onSelectOriginAddress(it)
                            }
                        )
                        state.originAddressSearchError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        StopGroupPicker(
                            query = state.originQuery,
                            groups = stopGroups,
                            recentStopNames = state.recentStopSearches,
                            onSelectGroup = {
                                keyboardController?.hide()
                                onSelectOrigin(it)
                            }
                        )
                    }
                }
                state.originPlace?.let { place ->
                    if (
                        !state.originStopManuallySelected &&
                        state.nearbyOriginGroups.isNotEmpty()
                    ) {
                        TextButton(
                            onClick = { showOriginStopPicker = !showOriginStopPicker },
                            enabled = !state.isSearching && !state.isImporting
                        ) {
                            Text(
                                if (showOriginStopPicker) "Ukryj przystanki"
                                else "Wybierz przystanek"
                            )
                        }
                        if (showOriginStopPicker) {
                            Text("Wybierz przystanek początkowy:")
                            state.nearbyOriginGroups.forEach { group ->
                                val distanceMeters = distanceToStopGroup(place, group)
                                TextButton(
                                    onClick = {
                                        showOriginStopPicker = false
                                        onSelectOrigin(group)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${group.name} · ${distanceMeters.toInt()} m")
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(destinationCardBringIntoViewRequester),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Cel", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.destinationQuery,
                    onValueChange = onDestinationQueryChange,
                    label = { Text("Adres lub przystanek") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isDestinationFieldFocused = it.isFocused }
                )
                if (state.destinationPlace != null) {
                    if (
                        !state.destinationStopManuallySelected &&
                        state.nearbyDestinationGroups.isNotEmpty()
                    ) {
                        TextButton(
                            onClick = {
                                showDestinationStopPicker = !showDestinationStopPicker
                            },
                            enabled = !state.isSearching && !state.isImporting
                        ) {
                            Text(
                                if (showDestinationStopPicker) "Ukryj przystanki"
                                else "Wybierz przystanek"
                            )
                        }
                        if (showDestinationStopPicker) {
                            Text("Wybierz przystanek docelowy:")
                            state.nearbyDestinationGroups.forEach { group ->
                                val distanceMeters =
                                    distanceToStopGroup(state.destinationPlace, group)
                                TextButton(
                                    onClick = {
                                        showDestinationStopPicker = false
                                        onSelectDestination(group)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${group.name} · ${distanceMeters.toInt()} m")
                                }
                            }
                        }
                    }
                } else if (isKeyboardVisible && isDestinationFieldFocused) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                            .bringIntoViewRequester(
                                destinationSuggestionsBringIntoViewRequester
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddressSuggestionPicker(
                            query = state.destinationQuery,
                            recentPlaces = state.recentAddressPlaces,
                            candidates = state.addressCandidates,
                            isSearching = state.isSearchingDestinationAddresses,
                            onSelect = {
                                keyboardController?.hide()
                                onSelectAddress(it)
                            }
                        )
                        state.destinationAddressSearchError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        StopGroupPicker(
                            query = state.destinationQuery,
                            groups = stopGroups,
                            recentStopNames = state.recentStopSearches,
                            onSelectGroup = {
                                keyboardController?.hide()
                                onSelectDestination(it)
                            }
                        )
                    }
                }
            }
        }

        if (!isKeyboardVisible) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                TextButton(
                    onClick = onEditRoutePlan,
                    enabled = !state.isSearching && !state.isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.routeTimePlan?.let { plan ->
                            val modeLabel = when (plan.mode) {
                                JourneyTimeMode.DEPART_AFTER -> "Odjazd po"
                                JourneyTimeMode.ARRIVE_BY -> "Przyjazd najpóźniej"
                            }
                            "$modeLabel · ${
                                plan.dateTime.format(
                                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                                )
                            }"
                        } ?: "🕒 Zaplanuj odjazd lub przyjazd"
                    )
                }
                Button(
                    onClick = onSearch,
                    enabled = !state.isSearching && !state.isImporting &&
                        (state.originPlace != null || state.originStop != null) &&
                        (state.destinationPlace != null || state.destinationStop != null),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isSearching) "Szukam..." else "Szukaj trasy")
                    }
                }
            }
        }
        state.routeSearchMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        if (state.journeyOptions.isNotEmpty()) {
            Text(
                "Znalezione odjazdy (${state.journeyOptions.size})",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        state.arrivalTarget?.let { target ->
            Text(
                "Plan dojazdu najpóźniej: ${target.format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        state.journeyOptions.forEachIndexed { index, journey ->
            val departureKey = journey.firstTransitConnection()?.tripId ?: "departure-$index"
            JourneyResult(
                journey = journey,
                stops = state.stops,
                origin = state.stops.firstOrNull {
                    it.stopId == journey.originAccess.stopId
                } ?: state.originStop,
                destination = state.stops.firstOrNull {
                    it.stopId == journey.destinationAccess.stopId
                } ?: state.destinationStop,
                routeNumber = index + 1,
                isExpanded = departureKey == expandedDepartureTripId,
                onClick = {
                    expandedDepartureTripId =
                        if (departureKey == expandedDepartureTripId) null else departureKey
                },
                onSaveAsCustom = onSaveCustomRoute
            )
        }
        if (state.journeyOptions.isEmpty()) {
            state.journey?.let { journey ->
                val departureKey =
                    journey.firstTransitConnection()?.tripId ?: "departure-fallback"
                JourneyResult(
                    journey = journey,
                    stops = state.stops,
                    origin = state.originStop,
                    destination = state.destinationStop,
                    routeNumber = 1,
                    isExpanded = departureKey == expandedDepartureTripId,
                    onClick = {
                        expandedDepartureTripId =
                            if (departureKey == expandedDepartureTripId) null else departureKey
                    },
                    onSaveAsCustom = onSaveCustomRoute
                )
            }
        }
        state.liveDepartures?.let { departures ->
            LiveDeparturesSection(
                stop = state.originStop,
                departures = departures
            )
        }
    }
}

private const val MAX_SERVICE_NEWS_HEADLINES = 5
private const val MAX_LIVE_ALERT_DESCRIPTION_LENGTH = 180

internal val serviceNewsMarqueeVelocity = MarqueeDefaults.Velocity * 1.2f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServiceNewsTicker(
    state: SearchUiState,
    onOpenAlerts: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val items = state.serviceNews.take(MAX_SERVICE_NEWS_HEADLINES)
    val liveHeadlines = state.activeServiceAlerts.map { alert ->
        alert.description
            ?.takeIf { it.isNotBlank() && it != alert.title }
            ?.let { "${alert.title} — ${it.take(MAX_LIVE_ALERT_DESCRIPTION_LENGTH)}" }
            ?: alert.title
    }
    val liveTicker = liveHeadlines
        .distinct()
        .take(MAX_SERVICE_NEWS_HEADLINES)
        .joinToString(separator = "   •   ")
    val newsTicker = items
        .map { it.title }
        .distinct()
        .joinToString(separator = "   •   ")
    val hasError = state.serviceNewsError != null || state.liveServiceAlertsError != null
    val message = when {
        liveTicker.isNotBlank() && hasError -> "Niepełne dane • Awaria • $liveTicker"
        liveTicker.isNotBlank() -> "Awaria • $liveTicker"
        newsTicker.isNotBlank() && hasError -> "Niepełne dane • ZTP • $newsTicker"
        newsTicker.isNotBlank() -> "ZTP • $newsTicker"
        state.isLoadingServiceNews || state.isLoadingLiveServiceAlerts ->
            "Pobieranie bieżących komunikatów ZTP..."
        hasError -> listOfNotNull(
            state.liveServiceAlertsError,
            state.serviceNewsError
        ).distinct().joinToString(" ")
        else -> "Brak aktywnych awarii w danych ZTP"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            )
            .clickable {
                when {
                    state.activeServiceAlerts.isNotEmpty() -> onOpenAlerts()
                    items.isNotEmpty() -> onOpenArticle(items.first().url)
                    else -> onOpenAlerts()
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("ZTP", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = serviceNewsMarqueeVelocity
                ),
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasError) {
            TextButton(
                onClick = onRefresh,
                enabled = !state.isLoadingServiceNews && !state.isLoadingLiveServiceAlerts
            ) {
                Text("Ponów")
            }
        }
    }
}

@Composable
private fun AddressSuggestionPicker(
    query: String,
    recentPlaces: List<GeoPlace>,
    candidates: List<GeoPlace>,
    isSearching: Boolean,
    onSelect: (GeoPlace) -> Unit
) {
    val recent = recentPlaces
        .filter { query.isBlank() || StopNameGrouper.matches(query, it.label) }
        .take(MAX_RECENT_ADDRESS_SUGGESTIONS)
    val recentLabels = recent.mapTo(mutableSetOf()) {
        StopNameGrouper.normalizeForSearch(it.label)
    }
    val matches = candidates
        .filterNot { StopNameGrouper.normalizeForSearch(it.label) in recentLabels }
        .take(MAX_ADDRESS_SUGGESTIONS)

    if (isSearching) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Szukam adresów...", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (recent.isNotEmpty()) {
        Text("Ostatnie adresy", style = MaterialTheme.typography.labelLarge)
        recent.forEach { place ->
            TextButton(
                onClick = { onSelect(place) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Adres · ${place.label}")
            }
        }
    }
    if (matches.isNotEmpty()) {
        Text("Adresy", style = MaterialTheme.typography.labelLarge)
        matches.forEach { place ->
            TextButton(
                onClick = { onSelect(place) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Adres · ${place.label}")
            }
        }
    }
}

@Composable
private fun StopGroupPicker(
    query: String,
    groups: List<StopGroup>,
    recentStopNames: List<String>,
    onSelectGroup: (StopGroup) -> Unit
) {
    val recentGroups = recentStopNames.mapNotNull { recentName ->
        groups.firstOrNull { it.name.equals(recentName, ignoreCase = true) }
            ?: groups.firstOrNull { StopNameGrouper.sameNameForSearch(it.name, recentName) }
    }.distinctBy { StopNameGrouper.normalizeForSearch(it.name) }
        .filter { query.isBlank() || StopNameGrouper.matches(query, it.name) }
        .take(MAX_RECENT_STOP_SUGGESTIONS)
    val recentGroupNames = recentGroups
        .mapTo(mutableSetOf()) { StopNameGrouper.normalizeForSearch(it.name) }
    val matchingGroups = if (query.length >= MIN_STOP_QUERY_LENGTH) {
        groups.asSequence()
            .filter { group ->
                StopNameGrouper.matches(query, group.name) ||
                    group.stops.any { stop ->
                        stop.stopCode?.let { StopNameGrouper.matches(query, it) } == true
                    }
            }
            .filterNot {
                StopNameGrouper.normalizeForSearch(it.name) in recentGroupNames
            }
            .take(MAX_STOP_SUGGESTIONS)
            .toList()
    } else {
        emptyList()
    }

    if (recentGroups.isNotEmpty()) {
        Text("Ostatnie przystanki", style = MaterialTheme.typography.labelLarge)
        recentGroups.forEach { group ->
            TextButton(
                onClick = { onSelectGroup(group) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(group.name)
            }
        }
        if (matchingGroups.isNotEmpty()) {
            Text("Przystanki", style = MaterialTheme.typography.labelLarge)
        }
    }
    matchingGroups.forEach { group ->
        TextButton(
            onClick = { onSelectGroup(group) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(group.name)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JourneyResult(
    journey: CsaJourney,
    stops: List<StopEntity>,
    origin: StopEntity?,
    destination: StopEntity?,
    routeNumber: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onSaveAsCustom: () -> Unit
) {
    val transitConnections = collapseTransitSteps(journey.steps)
        .filterIsInstance<JourneyStep.Transit>()
        .map(JourneyStep.Transit::connection)
    val firstConnection = transitConnections.firstOrNull()
    val routeLines = transitConnections
        .map { it.routeShortName }
        .distinct()
        .joinToString(" → ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onSaveAsCustom
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "$routeNumber. Odjazd ${
                    firstConnection?.let {
                        formatJourneyTime(it.departureTime, journey.departureTime)
                    } ?: formatJourneyTime(journey.departureTime, journey.departureTime)
                }",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (firstConnection != null) {
                Text(
                    "${stopName(stops, firstConnection.departureStopId)} · Linia $routeLines",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                if (isExpanded) "Dotknij, aby ukryć szczegóły" else "Dotknij, aby zobaczyć szczegóły",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isExpanded) {
                HorizontalDivider()
                Text(
                    "Przytrzymaj kartę, aby zapisać trasę jako „Własny”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${origin?.stopName?.let(StopNameGrouper::mainName).orEmpty()} → " +
                        destination?.stopName?.let(StopNameGrouper::mainName).orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JourneyMetric(
                        label = "PRZYJAZD",
                        value = formatJourneyTime(
                            journey.estimatedArrivalTime,
                            journey.departureTime
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    JourneyMetric(
                        label = "CZAS",
                        value = formatDuration(journey.totalDurationSeconds),
                        modifier = Modifier.weight(1f)
                    )
                    JourneyMetric(
                        label = "SPACER",
                        value = formatDistance(journey.totalWalkingDistanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (journey.transferPenaltySeconds > 0) {
                    Text(
                        "Kara za przesiadkę: +${journey.transferPenaltySeconds / 60} min",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    "Dystans pieszo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JourneyMetric(
                        label = "NA START",
                        value = formatDistance(journey.originAccess.walkingDistanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                    JourneyMetric(
                        label = "PRZESIADKI",
                        value = formatDistance(journey.transferWalkingDistanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                    JourneyMetric(
                        label = "DO CELU",
                        value = formatDistance(journey.destinationAccess.walkingDistanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    collapseTransitSteps(journey.steps).forEach { step ->
                    when (step) {
                        is JourneyStep.Transit -> {
                            val connection = step.connection
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "Linia ${connection.routeShortName}",
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 8.dp
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "${stopName(stops, connection.departureStopId)} · " +
                                                formatJourneyTime(connection.departureTime, journey.departureTime),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${stopName(stops, connection.arrivalStopId)} · " +
                                                formatJourneyTime(connection.arrivalTime, journey.departureTime),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        is JourneyStep.Walk -> {
                            val footpath = step.footpath
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "PIESZO",
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 8.dp
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "${stopName(stops, footpath.fromStopId)} → " +
                                                stopName(stops, footpath.toStopId),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${formatDistance(footpath.distanceMeters)} · " +
                                                formatDuration(footpath.durationSeconds),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun collapseTransitSteps(steps: List<JourneyStep>): List<JourneyStep> {
    val collapsed = mutableListOf<JourneyStep>()
    steps.forEach { step ->
        val current = step as? JourneyStep.Transit
        val previous = collapsed.lastOrNull() as? JourneyStep.Transit
        if (
            current != null &&
            previous != null &&
            previous.connection.tripId == current.connection.tripId &&
            previous.connection.arrivalStopId == current.connection.departureStopId
        ) {
            collapsed[collapsed.lastIndex] = JourneyStep.Transit(
                previous.connection.copy(
                    arrivalStopId = current.connection.arrivalStopId,
                    arrivalTime = current.connection.arrivalTime
                )
            )
        } else {
            collapsed += step
        }
    }
    return collapsed
}

@Composable
private fun LiveDeparturesSection(
    stop: StopEntity?,
    departures: LiveDeparturesResult
) {
    if (departures.departures.isEmpty()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Odjazdy z ${stop?.stopName?.let(StopNameGrouper::mainName).orEmpty()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Czerwone +N oznacza opóźnienie w minutach.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            departures.departures.take(MAX_DISPLAYED_DEPARTURES).forEach { departure ->
                LiveDepartureRow(departure)
            }
        }
    }
}

@Composable
internal fun LiveDepartureRow(departure: DepartureEstimate) {
    val delayMinutes = departure.delaySeconds
        ?.takeIf { departure.isLive && it > 0 }
        ?.let { ((it.toLong() + 59) / 60).toInt() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "${departure.routeShortName} → ${departure.direction}",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${formatGtfsTime(departure.estimatedDepartureTime)} · " +
                if (departure.isLive) "LIVE" else "ROZKŁAD",
            color = if (departure.isLive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        delayMinutes?.let {
            Text(
                "+$it",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    shortcutNames: DestinationShortcutNames,
    isSavingShortcutNames: Boolean,
    shortcutNamesSaveError: String?,
    onSaveShortcutNames: (DestinationShortcutNames) -> Unit,
    progressMessage: String,
    isImporting: Boolean,
    homeStopName: String?,
    workStopName: String?,
    hasHomeDestination: Boolean,
    hasWorkDestination: Boolean,
    homeSchedule: DestinationSchedule?,
    workSchedule: DestinationSchedule?,
    diagnosticEntries: List<String>,
    updateCheckMessage: String?,
    updateCheckResult: pl.wraven.mpkfastroute.core.update.UpdateCheckResult?,
    isCheckingUpdates: Boolean,
    isDownloadingAppUpdate: Boolean,
    appUpdateDownloadProgressPercent: Int?,
    appUpdateDownloadError: String?,
    isAppUpdateDownloaded: Boolean,
    canInstallAppUpdates: Boolean,
    onRefresh: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onRequestInstallPermission: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onOpenLatestRelease: () -> Unit,
    onOpenCharityInfo: () -> Unit,
    taxDonationReminderEnabled: Boolean,
    taxDonationReminderSent: Boolean,
    onEnableTaxDonationReminder: () -> Unit,
    onCancelTaxDonationReminder: () -> Unit,
    onContactAuthor: () -> Unit,
    onReportBug: () -> Unit,
    onEditHomeSchedule: () -> Unit,
    onEditWorkSchedule: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onDiagnosticsCopied: () -> Unit,
    onDismiss: () -> Unit
) {
    var homeShortcutName by rememberSaveable(shortcutNames.home) { mutableStateOf(shortcutNames.home) }
    var workShortcutName by rememberSaveable(shortcutNames.work) { mutableStateOf(shortcutNames.work) }
    val clipboardManager = LocalClipboardManager.current
    val diagnosticsScrollState = rememberScrollState()
    LaunchedEffect(diagnosticEntries.lastOrNull()) {
        diagnosticsScrollState.animateScrollTo(diagnosticsScrollState.maxValue)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustawienia") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.app_description),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.app_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Text("Bez reklam, z opcją wsparcia", style = MaterialTheme.typography.titleSmall)
                Text(
                    "FastRoute powstał z niechęci do reklam, dlatego ich nie wyświetla. " +
                        "Jeśli aplikacja Ci się podoba, możesz dobrowolnie przekazać " +
                        "1,5% podatku na DT u Oli w Legionowie — inicjatywę zaproponowaną " +
                        "przez żonę autora."
                )
                Text("KRS: 0000270261")
                Text("Cel szczegółowy: DT u Oli w Legionowie 16057")
                TextButton(onClick = onOpenCharityInfo) {
                    Text("Poznaj inicjatywę i szczegóły przekazania 1,5%")
                }
                when {
                    taxDonationReminderSent -> {
                        Text("Przypomnienie o 1,5% podatku zostało już pokazane.")
                    }
                    taxDonationReminderEnabled -> {
                        Text("Przypomnienie w aplikacji pojawi się 15 lutego 2027 około 10:00.")
                        TextButton(onClick = onCancelTaxDonationReminder) {
                            Text("Anuluj przypomnienie")
                        }
                    }
                    else -> {
                        Text(
                            "Możesz włączyć jedno przypomnienie w aplikacji na 15 lutego 2027. " +
                                "Nie wymaga zgody Androida."
                        )
                        TextButton(onClick = onEnableTaxDonationReminder) {
                            Text("Przypomnij mi 15 lutego 2027")
                        }
                    }
                }
                HorizontalDivider()
                Text("Nazwy skrótów", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = homeShortcutName,
                    onValueChange = { homeShortcutName = it },
                    label = { Text("Nazwa skrótu do domu") },
                    singleLine = true,
                    enabled = !isSavingShortcutNames,
                    isError = homeShortcutName.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = workShortcutName,
                    onValueChange = { workShortcutName = it },
                    label = { Text("Nazwa skrótu do pracy") },
                    singleLine = true,
                    enabled = !isSavingShortcutNames,
                    isError = workShortcutName.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (homeShortcutName.isBlank() || workShortcutName.isBlank()) {
                    Text("Nazwy skrótów nie mogą być puste.", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        onSaveShortcutNames(DestinationShortcutNames(homeShortcutName, workShortcutName))
                    },
                    enabled = !isSavingShortcutNames &&
                        homeShortcutName.isNotBlank() && workShortcutName.isNotBlank() &&
                        (homeShortcutName.trim() != shortcutNames.home ||
                            workShortcutName.trim() != shortcutNames.work)
                ) {
                    Text(if (isSavingShortcutNames) "Zapisuję..." else "Zapisz nazwy skrótów")
                }
                shortcutNamesSaveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${shortcutNames.home}: ${homeStopName ?: "nie ustawiono"}")
                        Text(
                            scheduleSummary(homeSchedule),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                        onClick = onEditHomeSchedule,
                        enabled = hasHomeDestination
                    ) {
                        Text("Zmień")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${shortcutNames.work}: ${workStopName ?: "nie ustawiono"}")
                        Text(
                            scheduleSummary(workSchedule),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                        onClick = onEditWorkSchedule,
                        enabled = hasWorkDestination
                    ) {
                        Text("Zmień")
                    }
                }
                Button(onClick = onRefresh, enabled = !isImporting) {
                    Text(if (isImporting) "Aktualizuję..." else "Odśwież rozkład GTFS")
                }
                if (progressMessage.isNotBlank()) {
                    Text(progressMessage)
                }
                Button(onClick = onCheckUpdates, enabled = !isCheckingUpdates) {
                    Text(if (isCheckingUpdates) "Sprawdzam..." else "Sprawdź aktualizacje")
                }
                updateCheckMessage?.let { Text(it) }
                if (updateCheckResult?.updateAvailable == true) {
                    Button(
                        onClick = {
                            when {
                                isDownloadingAppUpdate -> Unit
                                isAppUpdateDownloaded && canInstallAppUpdates -> onInstallUpdate()
                                isAppUpdateDownloaded -> onRequestInstallPermission()
                                else -> onDownloadUpdate()
                            }
                        },
                        enabled = !isDownloadingAppUpdate
                    ) {
                        Text(
                            when {
                                isDownloadingAppUpdate -> appUpdateDownloadProgressPercent
                                    ?.let { "Pobieranie APK: $it%" }
                                    ?: "Pobieranie APK..."
                                isAppUpdateDownloaded && canInstallAppUpdates -> "Zainstaluj"
                                isAppUpdateDownloaded -> "Zezwól na instalowanie"
                                else -> "Pobierz i zainstaluj"
                            }
                        )
                    }
                    if (isAppUpdateDownloaded && !canInstallAppUpdates) {
                        Text(
                            "Android wymaga zgody na instalację aplikacji pobranych spoza sklepu.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    appUpdateDownloadError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { onOpenRelease(updateCheckResult.releaseUrl) }) {
                        Text("Szczegóły wydania ${updateCheckResult.latestVersion}")
                    }
                }
                HorizontalDivider()
                Text("Udostępnij aplikację")
                ReleaseQrCode(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(180.dp)
                )
                Text(
                    "Zeskanuj kod, aby otworzyć najnowsze wydanie na GitHub.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onOpenLatestRelease) {
                    Text("Otwórz stronę wydań")
                }
                TextButton(onClick = onContactAuthor) {
                    Text("Autor: lukasz@drozd.cc")
                }
                Button(onClick = onReportBug) {
                    Text("Zgłoś błąd")
                }
                Text("Terminal diagnostyczny wyszukiwania")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    if (diagnosticEntries.isEmpty()) {
                        Text("Brak wpisów.")
                    } else {
                        Text(
                            text = diagnosticEntries.joinToString("\n"),
                            modifier = Modifier.verticalScroll(diagnosticsScrollState),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(diagnosticEntries.joinToString("\n"))
                            )
                            onDiagnosticsCopied()
                        },
                        enabled = diagnosticEntries.isNotEmpty()
                    ) {
                        Text("Kopiuj")
                    }
                    TextButton(
                        onClick = onClearDiagnostics,
                        enabled = diagnosticEntries.isNotEmpty()
                    ) {
                        Text("Wyczyść")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Gotowe")
            }
        }
    )
}

@Composable
private fun ReleaseQrCode(modifier: Modifier = Modifier) {
    val qrBitmap = remember {
        val matrix = QRCodeWriter().encode(
            GitHubUpdateChecker.RELEASES_PAGE,
            BarcodeFormat.QR_CODE,
            360,
            360
        )
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    setPixel(
                        x,
                        y,
                        if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                    )
                }
            }
        }.asImageBitmap()
    }
    Image(
        bitmap = qrBitmap,
        contentDescription = stringResource(R.string.release_qr_description),
        modifier = modifier
    )
}

@Composable
private fun UpdateAvailableDialog(
    update: UpdateCheckResult,
    isDownloading: Boolean,
    downloadProgressPercent: Int?,
    downloadError: String?,
    isDownloaded: Boolean,
    canInstall: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRequestInstallPermission: () -> Unit,
    onOpenRelease: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dostępna aktualizacja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Wersja ${update.latestVersion} jest dostępna " +
                        "(zainstalowana: ${update.currentVersion})."
                )
                if (isDownloading) {
                    Text(
                        downloadProgressPercent?.let { "Pobieranie APK: $it%" }
                            ?: "Pobieranie APK..."
                    )
                } else if (isDownloaded) {
                    Text("APK pobrano i sprawdzono. Możesz przejść do instalacji.")
                    if (!canInstall) {
                        Text(
                            "Android wymaga zgody na instalację aplikacji pobranych spoza sklepu."
                        )
                    }
                }
                downloadError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onOpenRelease) {
                    Text("Szczegóły wydania na GitHubie")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        isDownloading -> Unit
                        isDownloaded && canInstall -> onInstall()
                        isDownloaded -> onRequestInstallPermission()
                        else -> onDownload()
                    }
                },
                enabled = !isDownloading
            ) {
                Text(
                    when {
                        isDownloading -> "Pobieranie..."
                        isDownloaded && canInstall -> "Zainstaluj"
                        isDownloaded -> "Zezwól na instalowanie"
                        else -> "Pobierz i zainstaluj"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Pomiń")
            }
        }
    )
}

@Composable
private fun JourneyTimePlanDialog(
    currentPlan: JourneyTimePlan?,
    onSave: (JourneyTimePlan?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mode by remember(currentPlan) {
        mutableStateOf(currentPlan?.mode ?: JourneyTimeMode.DEPART_AFTER)
    }
    var dateTime by remember(currentPlan) {
        val now = LocalDateTime.now()
        mutableStateOf(
            currentPlan?.dateTime?.takeIf { it.isAfter(now) }
                ?: now.plusMinutes(5).withSecond(0).withNano(0)
        )
    }
    var datePickerVisible by remember { mutableStateOf(false) }
    var timePickerVisible by remember { mutableStateOf(false) }
    val isDateTimeValid = dateTime.isAfter(LocalDateTime.now())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zaplanuj podróż") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Wybierz jeden sposób planowania:")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = mode == JourneyTimeMode.DEPART_AFTER,
                        onClick = { mode = JourneyTimeMode.DEPART_AFTER },
                        label = { Text("Odjazd po") }
                    )
                    FilterChip(
                        selected = mode == JourneyTimeMode.ARRIVE_BY,
                        onClick = { mode = JourneyTimeMode.ARRIVE_BY },
                        label = { Text("Przyjazd najpóźniej") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { datePickerVisible = true }) {
                        Text("Data: ${dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
                    }
                    TextButton(onClick = { timePickerVisible = true }) {
                        Text("Godzina: ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                    }
                }
                Text(
                    if (mode == JourneyTimeMode.DEPART_AFTER) {
                        "Szukam od wybranej godziny w ciągu 2 godzin."
                    } else {
                        "Szukam połączenia, które dotrze do celu przed wybraną godziną."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isDateTimeValid) {
                    Text(
                        "Wybierz przyszłą datę i godzinę.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(JourneyTimePlan(mode, dateTime)) },
                enabled = isDateTimeValid
            ) {
                Text("Zapisz plan")
            }
        },
        dismissButton = {
            Row {
                if (currentPlan != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("Od teraz")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Anuluj")
                }
            }
        }
    )

    if (datePickerVisible) {
        val picker = remember(dateTime) {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    dateTime = LocalDateTime.of(
                        LocalDate.of(year, month + 1, day),
                        dateTime.toLocalTime()
                    )
                    datePickerVisible = false
                },
                dateTime.year,
                dateTime.monthValue - 1,
                dateTime.dayOfMonth
            ).apply {
                datePicker.minDate = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                setOnDismissListener { datePickerVisible = false }
            }
        }
        LaunchedEffect(picker) {
            picker.show()
        }
    }

    if (timePickerVisible) {
        val picker = remember(dateTime) {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    dateTime = dateTime
                        .withHour(hour)
                        .withMinute(minute)
                        .withSecond(0)
                        .withNano(0)
                    timePickerVisible = false
                },
                dateTime.hour,
                dateTime.minute,
                true
            ).apply {
                setOnDismissListener { timePickerVisible = false }
            }
        }
        LaunchedEffect(picker) {
            picker.show()
        }
    }
}

@Composable
private fun DestinationScheduleDialog(
    destinationName: String,
    initialSchedule: DestinationSchedule,
    onSave: (DestinationSchedule) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var arrivalTime by remember(initialSchedule) { mutableStateOf(initialSchedule.arrivalTime) }
    var weekdays by remember(initialSchedule) { mutableStateOf(initialSchedule.weekdays) }
    var timePickerVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Harmonogram: $destinationName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("O której godzinie chcesz być na miejscu?")
                TextButton(onClick = { timePickerVisible = true }) {
                    Text("Godzina: ${arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                }
                Text("Dni tygodnia")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected = day in weekdays,
                            onClick = {
                                weekdays = if (day in weekdays) {
                                    weekdays - day
                                } else {
                                    weekdays + day
                                }
                            },
                            label = { Text(shortWeekday(day)) }
                        )
                    }
                }
                if (weekdays.isEmpty()) {
                    Text(
                        "Wybierz co najmniej jeden dzień.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(DestinationSchedule(arrivalTime, weekdays)) },
                enabled = weekdays.isNotEmpty()
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )

    if (timePickerVisible) {
        val picker = remember(timePickerVisible) {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    arrivalTime = LocalTime.of(hour, minute)
                    timePickerVisible = false
                },
                arrivalTime.hour,
                arrivalTime.minute,
                true
            ).apply {
                setOnDismissListener { timePickerVisible = false }
            }
        }
        LaunchedEffect(picker) {
            picker.show()
        }
    }
}

@Composable
private fun RadioManagementDialog(
    stations: List<RadioStation>,
    onSave: (List<RadioStation>) -> Unit,
    onDismiss: () -> Unit
) {
    var editedStation by remember { mutableStateOf<RadioStation?>(null) }
    var addingStation by remember { mutableStateOf(false) }
    var stationToDelete by remember { mutableStateOf<RadioStation?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stacje radiowe") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Edytuj nazwy i bezpośrednie adresy strumieni.")
                stations.forEach { station ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(station.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                station.streamUrl,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editedStation = station }) {
                                    Text("Edytuj")
                                }
                                TextButton(
                                    onClick = { stationToDelete = station },
                                    enabled = stations.size > 1
                                ) {
                                    Text("Usuń")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    addingStation = true
                    editedStation = RadioStation(
                        id = UUID.randomUUID().toString(),
                        name = "",
                        streamUrl = ""
                    )
                }
            ) {
                Text("Dodaj stację")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Gotowe")
            }
        }
    )

    editedStation?.let { station ->
        RadioStationEditorDialog(
            station = station,
            onDismiss = {
                editedStation = null
                addingStation = false
            },
            onSave = { updatedStation ->
                val updatedStations = if (addingStation) {
                    stations + updatedStation
                } else {
                    stations.map { current ->
                        if (current.id == updatedStation.id) updatedStation else current
                    }
                }
                onSave(updatedStations)
                editedStation = null
                addingStation = false
            }
        )
    }

    stationToDelete?.let { station ->
        AlertDialog(
            onDismissRequest = { stationToDelete = null },
            title = { Text("Usunąć stację?") },
            text = { Text("Stacja „${station.name}” zostanie usunięta z listy.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(stations.filterNot { it.id == station.id })
                        stationToDelete = null
                    }
                ) {
                    Text("Usuń")
                }
            },
            dismissButton = {
                TextButton(onClick = { stationToDelete = null }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Composable
private fun RadioStationEditorDialog(
    station: RadioStation,
    onDismiss: () -> Unit,
    onSave: (RadioStation) -> Unit
) {
    var name by remember(station) { mutableStateOf(station.name) }
    var streamUrl by remember(station) { mutableStateOf(station.streamUrl) }
    var error by remember(station) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (station.name.isBlank()) "Dodaj stację" else "Edytuj stację") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nazwa") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it; error = null },
                    label = { Text("Adres strumienia") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        name.isBlank() -> "Wpisz nazwę stacji."
                        !isHttpStreamUrl(streamUrl) ->
                            "Podaj poprawny adres strumienia HTTP lub HTTPS."
                        else -> null
                    }
                    if (error == null) {
                        onSave(station.copy(name = name.trim(), streamUrl = streamUrl.trim()))
                    }
                }
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

private fun isHttpStreamUrl(value: String): Boolean {
    val uri = try {
        URI(value.trim())
    } catch (_: URISyntaxException) {
        return false
    }
    return (
        uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)
        ) && !uri.host.isNullOrBlank()
}

private fun hasCommuteReminderPermissions(context: Context): Boolean {
    val hasForegroundLocation =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val hasBackgroundLocation =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val hasNotifications =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val notificationManager = context.getSystemService(
        android.app.NotificationManager::class.java
    )
    val notificationsEnabled =
        notificationManager.areNotificationsEnabled() &&
            notificationManager.getNotificationChannel(CommuteReminderScheduler.CHANNEL_ID)
                ?.importance != android.app.NotificationManager.IMPORTANCE_NONE
    return hasForegroundLocation && hasBackgroundLocation && hasNotifications &&
        notificationsEnabled
}

private fun requestBackgroundLocationPermission(
    context: Context,
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
    onOpenSettingsRequired: () -> Unit,
    onReady: () -> Unit
) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            onOpenSettingsRequired()
        } else {
            launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    } else {
        onReady()
    }
}

private fun scheduleSummary(schedule: DestinationSchedule?): String =
    schedule?.let {
        "${it.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · " +
            it.weekdays.sortedBy(DayOfWeek::getValue).joinToString(", ", transform = ::shortWeekday)
    } ?: "Harmonogram nieustawiony"

private fun shortWeekday(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Pon"
    DayOfWeek.TUESDAY -> "Wt"
    DayOfWeek.WEDNESDAY -> "Śr"
    DayOfWeek.THURSDAY -> "Czw"
    DayOfWeek.FRIDAY -> "Pt"
    DayOfWeek.SATURDAY -> "Sob"
    DayOfWeek.SUNDAY -> "Ndz"
}

private fun stopName(stops: List<StopEntity>, stopId: String): String =
    stops.firstOrNull { it.stopId == stopId }?.let { stop ->
        StopNameGrouper.mainName(stop.stopName)
    } ?: "Nieznany przystanek"

private fun distanceToStopGroup(place: GeoPlace, group: StopGroup): Double =
    group.stops.minOfOrNull { stop ->
        Haversine.distanceMeters(place.latitude, place.longitude, stop.stopLat, stop.stopLon)
    } ?: throw IllegalStateException("A stop group cannot be empty.")

internal fun formatGtfsTime(seconds: Int): String {
    val totalMinutes = Math.floorMod(seconds, 24 * 60 * 60) / 60
    return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
}

internal fun formatJourneyTime(seconds: Int, journeyDepartureTime: Int): String {
    val dayOffset = Math.floorDiv(seconds, 24 * 60 * 60) -
        Math.floorDiv(journeyDepartureTime, 24 * 60 * 60)
    return formatGtfsTime(seconds) + if (dayOffset > 0) " (+$dayOffset)" else ""
}

private fun formatDuration(seconds: Int): String {
    val totalMinutes = (seconds + 59) / 60
    return if (totalMinutes < 60) {
        "$totalMinutes min"
    } else {
        "${totalMinutes / 60} godz. ${totalMinutes % 60} min"
    }
}

private fun formatDistance(meters: Double): String = "${meters.roundToInt()} m"

private const val MIN_STOP_QUERY_LENGTH = 2
private const val MAX_STOP_SUGGESTIONS = 6
private const val MAX_RECENT_STOP_SUGGESTIONS = 3
private const val MAX_RECENT_ADDRESS_SUGGESTIONS = 3
private const val MAX_ADDRESS_SUGGESTIONS = 5
private const val MAX_DISPLAYED_DEPARTURES = 12
