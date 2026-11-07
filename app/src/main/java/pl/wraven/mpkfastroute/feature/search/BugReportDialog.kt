package pl.wraven.mpkfastroute.feature.search

import android.content.ActivityNotFoundException
import android.util.Log
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BugReportDialog(appView: View, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var screenshotPath by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var emailOpened by remember { mutableStateOf(false) }

    fun showError(exception: Exception) {
        Log.e("BugReport", "Nie udało się przygotować zgłoszenia.", exception)
        error = exception.message ?: "Nie można przygotować zgłoszenia. Spróbuj ponownie."
    }

    fun attachScreenshot(action: suspend () -> File) {
        scope.launch {
            busy = true
            error = null
            emailOpened = false
            try {
                val screenshot = action()
                preview = null
                screenshotPath = screenshot.path
            } catch (exception: IOException) {
                showError(exception)
            } catch (exception: SecurityException) {
                showError(exception)
            } catch (exception: IllegalArgumentException) {
                showError(exception)
            } finally {
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attachScreenshot { importBugReportScreenshot(context, uri) }
    }
    LaunchedEffect(screenshotPath) {
        preview = null
        screenshotPath?.let { path ->
            try {
                preview = withContext(Dispatchers.IO) { decodeBugReportImage(File(path)).asImageBitmap() }
            } catch (exception: IOException) {
                screenshotPath = null
                showError(exception)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Zgłoś błąd") },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Do: $BUG_REPORT_ADDRESS")
                Text(
                    "Poczta otworzy gotowy szkic. Sprawdź treść i załącznik, potem naciśnij Wyślij. " +
                        "Nie jest wymagane konto GitHub; nie powstaje automatycznie GitHub Issue.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it; emailOpened = false },
                    label = { Text("Tytuł błędu") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it; emailOpened = false },
                    label = { Text("Opis: co się stało i czego oczekujesz?") },
                    minLines = 3, maxLines = 6, enabled = !busy, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = steps, onValueChange = { steps = it; emailOpened = false },
                    label = { Text("Kroki odtworzenia (opcjonalnie)") },
                    minLines = 2, maxLines = 4, enabled = !busy, modifier = Modifier.fillMaxWidth()
                )
                Text("Tytuł i opis są wymagane. Wersje aplikacji i Androida dodamy automatycznie.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(
                    enabled = !busy,
                    onClick = { attachScreenshot { captureBugReportScreenshot(appView) } }
                ) { Text("Zrób zrzut widoku aplikacji") }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        try {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (exception: ActivityNotFoundException) {
                            showError(exception)
                        } catch (exception: SecurityException) {
                            showError(exception)
                        }
                    }
                ) { Text("Wybierz zrzut z telefonu") }
                Text(
                    "Zrzut jest opcjonalny (obraz do 10 MB). Własny zrzut obejmuje ekran pod formularzem. " +
                        "Sprawdź, czy nie zawiera prywatnych adresów lub innych danych. " +
                        "Nie dodajemy automatycznie lokalizacji ani logów.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (busy) CircularProgressIndicator()
                preview?.let {
                    Image(it, "Podgląd załączonego zrzutu",
                        Modifier.fillMaxWidth().heightIn(max = 160.dp).padding(4.dp))
                }
                if (screenshotPath != null) {
                    TextButton(enabled = !busy, onClick = {
                        preview = null
                        screenshotPath = null
                        error = null
                        emailOpened = false
                    }) { Text("Usuń załącznik") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (emailOpened) {
                    Text("Otwarto wybór poczty. Zgłoszenie zostanie wysłane dopiero po Twoim potwierdzeniu w poczcie.")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && title.isNotBlank() && description.isNotBlank() &&
                    (screenshotPath == null || preview != null),
                onClick = {
                    error = null
                    emailOpened = false
                    try {
                        context.startActivity(createBugReportEmailChooser(
                            context, title, description, steps, screenshotPath?.let(::File)
                        ))
                        emailOpened = true
                    } catch (exception: ActivityNotFoundException) {
                        showError(exception)
                    } catch (exception: SecurityException) {
                        showError(exception)
                    } catch (exception: IOException) {
                        showError(exception)
                    } catch (exception: IllegalArgumentException) {
                        showError(exception)
                    }
                }
            ) { Text("Otwórz pocztę") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Zamknij") } }
    )
}
