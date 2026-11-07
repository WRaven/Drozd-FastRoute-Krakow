package pl.wraven.mpkfastroute.feature.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.BuildConfig

internal const val BUG_REPORT_ADDRESS = "lukasz@drozd.cc"
private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
private const val MAX_IMAGE_PIXELS = 2_000_000L
private const val CACHE_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1_000

internal fun bugReportBody(description: String, steps: String, hasScreenshot: Boolean): String {
    require(description.isNotBlank()) { "Opisz błąd przed otwarciem poczty." }
    return buildString {
        appendLine("Drozd FastRoute - zgłoszenie błędu")
        appendLine()
        appendLine("Opis błędu:")
        appendLine(description.trim())
        appendLine()
        appendLine("Kroki odtworzenia:")
        appendLine(steps.trim().ifEmpty { "Nie podano." })
        appendLine()
        appendLine("Wersja aplikacji: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        append("Zrzut ekranu: ${if (hasScreenshot) "dołączony" else "bez załącznika"}")
    }
}

internal fun createBugReportEmailChooser(
    context: Context,
    title: String,
    description: String,
    steps: String,
    screenshot: File?
): Intent {
    require(title.isNotBlank()) { "Podaj tytuł zgłoszenia." }
    val subject = "[Drozd FastRoute] ${title.lineSequence().first().trim()}"
    val body = bugReportBody(description, steps, screenshot != null)
    val packages = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
        PackageManager.MATCH_DEFAULT_ONLY
    ).map { it.activityInfo.packageName }.distinct()
    if (packages.isEmpty()) {
        throw ActivityNotFoundException("Nie znaleziono aplikacji pocztowej. Zainstaluj lub skonfiguruj pocztę.")
    }
    val email = if (screenshot == null) {
        Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:$BUG_REPORT_ADDRESS?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        )
    } else {
        if (!screenshot.isFile) throw IOException("Zrzut nie jest już dostępny. Dodaj go ponownie.")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", screenshot)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Zrzut ekranu", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BUG_REPORT_ADDRESS))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    val targets = packages.map { Intent(email).setPackage(it) }.filter { target ->
        screenshot == null || context.packageManager.queryIntentActivities(
            target, PackageManager.MATCH_DEFAULT_ONLY
        ).isNotEmpty()
    }
    if (targets.isEmpty()) {
        throw ActivityNotFoundException(
            "Poczta nie obsługuje tego załącznika. Wybierz inną aplikację pocztową lub usuń zrzut."
        )
    }
    return Intent.createChooser(targets.first(), "Wyślij zgłoszenie przez pocztę").apply {
        putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
        if (screenshot != null) {
            clipData = email.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

internal suspend fun captureBugReportScreenshot(view: View): File = withContext(Dispatchers.Main.immediate) {
    if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
        throw IOException("Widok aplikacji nie jest gotowy do wykonania zrzutu.")
    }
    var context: Context = view.context
    while (context is ContextWrapper && context !is Activity) context = context.baseContext
    val activity = context as? Activity ?: throw IOException("Nie można odczytać okna aplikacji.")
    val position = IntArray(2).also(view::getLocationInWindow)
    val rect = Rect(position[0], position[1], position[0] + view.width, position[1] + view.height)
    val scale = sqrt(MAX_IMAGE_PIXELS.toDouble() / (view.width.toLong() * view.height)).coerceAtMost(1.0)
    val bitmap = Bitmap.createBitmap(
        (view.width * scale).toInt().coerceAtLeast(1),
        (view.height * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    try {
        // The activity window excludes the report dialog and its private draft text.
        val result = suspendCoroutine<Int> { continuation ->
            PixelCopy.request(activity.window, rect, bitmap, { continuation.resume(it) }, Handler(Looper.getMainLooper()))
        }
        if (result != PixelCopy.SUCCESS) throw IOException("Nie udało się wykonać zrzutu (kod $result).")
        withContext(Dispatchers.IO) { saveBugReportImage(view.context, bitmap) }
    } finally {
        bitmap.recycle()
    }
}

internal suspend fun importBugReportScreenshot(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val temporary = File.createTempFile("import-", ".tmp", bugReportCache(context))
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Nie można odczytać wybranego obrazu.")
        input.use { source -> temporary.outputStream().use { copyBugReportImage(source, it) } }
        val bitmap = decodeBugReportImage(temporary, MAX_IMAGE_PIXELS)
        try {
            saveBugReportImage(context, bitmap)
        } finally {
            bitmap.recycle()
        }
    } finally {
        if (!temporary.delete()) Log.w("BugReport", "Nie usunięto tymczasowego obrazu.")
    }
}

internal fun copyBugReportImage(input: InputStream, output: OutputStream) {
    var total = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > MAX_IMAGE_BYTES) throw IOException("Wybrany obraz jest większy niż 10 MB.")
        output.write(buffer, 0, count)
    }
    if (total == 0L) throw IOException("Wybrany obraz jest pusty.")
}

internal fun decodeBugReportImage(file: File, maxPixels: Long = 250_000): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Nie można odczytać obrazu.")
    options.inJustDecodeBounds = false
    options.inSampleSize = 1
    while (options.outWidth.toLong() / options.inSampleSize * (options.outHeight / options.inSampleSize) > maxPixels) {
        options.inSampleSize *= 2
    }
    return BitmapFactory.decodeFile(file.path, options) ?: throw IOException("Nie można odczytać obrazu.")
}

private fun saveBugReportImage(context: Context, bitmap: Bitmap): File {
    val file = File.createTempFile("screenshot-", ".png", bugReportCache(context))
    var complete = false
    try {
        file.outputStream().use {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Nie zapisano zrzutu.")
        }
        if (file.length() > MAX_IMAGE_BYTES) throw IOException("Zrzut jest większy niż 10 MB.")
        complete = true
        return file
    } finally {
        if (!complete && !file.delete()) Log.w("BugReport", "Nie usunięto niekompletnego zrzutu.")
    }
}

private fun bugReportCache(context: Context): File {
    val directory = File(context.cacheDir, "bug-reports")
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Nie można utworzyć katalogu zrzutów.")
    val cutoff = System.currentTimeMillis() - CACHE_RETENTION_MILLIS
    val files = directory.listFiles() ?: throw IOException("Nie można odczytać katalogu zrzutów.")
    files.filter { it.isFile && it.lastModified() < cutoff }.forEach {
        if (!it.delete()) Log.w("BugReport", "Nie usunięto starego załącznika.")
    }
    return directory
}
