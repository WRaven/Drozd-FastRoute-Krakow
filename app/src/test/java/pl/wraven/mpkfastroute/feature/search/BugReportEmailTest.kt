package pl.wraven.mpkfastroute.feature.search

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory
import pl.wraven.mpkfastroute.BuildConfig

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36], application = Application::class)
class BugReportEmailTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ShadowBitmapFactory.setAllowInvalidImageData(false)
    }

    @Test
    fun opensOnlyEmailAppsWithTheRecipientAndEncodedUnicodeDraft() {
        addEmailApp("example.mail", supportsAttachments = false)
        val chooser = createBugReportEmailChooser(context, "Noc & trasa", "Opis\nDruga linia", "Szukaj", null)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val email = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SENDTO, email.action)
        assertEquals("example.mail", email.`package`)
        assertArrayEquals(arrayOf(BUG_REPORT_ADDRESS), email.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("[Drozd FastRoute] Noc & trasa", email.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(email.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Opis\nDruga linia"))
        assertTrue(email.getStringExtra(Intent.EXTRA_TEXT)!!.contains(BuildConfig.VERSION_NAME))
        val query = Uri.parse("https://example.test/?" + email.dataString!!.substringAfter('?'))
        assertEquals(email.getStringExtra(Intent.EXTRA_TEXT), query.getQueryParameter("body"))
        assertEquals(email.getStringExtra(Intent.EXTRA_SUBJECT), query.getQueryParameter("subject"))
        assertFalse(email.hasExtra(Intent.EXTRA_STREAM))
    }

    @Test
    fun sharesOnlyTheReportFileWithReadPermissionAndNoGenericShareTargets() {
        requireUnixFileProvider()
        addEmailApp("example.mail", supportsAttachments = true)
        addEmailApp("example.secondmail", supportsAttachments = true)
        addEmailApp("example.mailwithoutattachments", supportsAttachments = false)
        val directory = File(context.cacheDir, "bug-reports").apply { mkdirs() }
        val image = File(directory, "screenshot-test.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val chooser = createBugReportEmailChooser(context, "Błąd", "Opis", "", image)
        val email = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        val uri = email.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals(Intent.ACTION_SEND, email.action)
        assertEquals("image/png", email.type)
        assertEquals(uri, email.clipData!!.getItemAt(0).uri)
        assertEquals(uri, chooser.clipData!!.getItemAt(0).uri)
        assertTrue(email.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(email.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        context.contentResolver.openInputStream(uri)!!.use {
            assertArrayEquals(image.readBytes(), it.readBytes())
        }
        val others = chooser.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS)!!
        assertEquals(1, others.size)
        assertEquals("example.secondmail", (others.single() as Intent).`package`)
    }

    @Test
    fun explicitlyRejectsMissingMailApps() {
        assertThrows(ActivityNotFoundException::class.java) {
            createBugReportEmailChooser(context, "Błąd", "Opis", "", null)
        }
    }

    @Test
    fun explicitlyRejectsUnsupportedAttachmentsAndLostFiles() {
        requireUnixFileProvider()
        addEmailApp("example.mail", supportsAttachments = false)
        val directory = File(context.cacheDir, "bug-reports").apply { mkdirs() }
        val file = File(directory, "screenshot-test.png").apply { writeText("image") }
        assertThrows(ActivityNotFoundException::class.java) {
            createBugReportEmailChooser(context, "Błąd", "Opis", "", file)
        }
        assertThrows(IOException::class.java) {
            createBugReportEmailChooser(context, "Błąd", "Opis", "", File(directory, "missing.png"))
        }
    }

    @Test
    fun importsARealImageAsABoundedPngAndOnlyExpiresOldReportCacheFiles() {
        val reports = File(context.cacheDir, "bug-reports").apply { mkdirs() }
        val oldReport = File(reports, "screenshot-old.png").apply {
            writeText("old")
            assertTrue(setLastModified(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1_000))
        }
        val unrelated = File(context.cacheDir, "updates").apply { mkdirs() }
            .resolve("keep.apk").apply { writeText("keep") }
        val image = BufferedImage(1800, 1800, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { assertTrue(ImageIO.write(image, "png", it)) }.toByteArray()
        val uri = Uri.parse("content://picker/image/42")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        val attachment = runBlocking { importBugReportScreenshot(context, uri) }
        assertEquals(reports.canonicalFile, attachment.parentFile.canonicalFile)
        assertTrue(attachment.name.endsWith(".png"))
        val decoded = decodeBugReportImage(attachment, 2_000_000)
        assertTrue(decoded.width.toLong() * decoded.height <= 2_000_000)
        decoded.recycle()
        assertFalse(oldReport.exists())
        assertTrue(unrelated.exists())
        assertFalse(reports.listFiles()!!.any { it.extension == "tmp" })
    }

    private fun requireUnixFileProvider() {
        assumeTrue("Android FileProvider uses Unix paths; these cases run in Linux CI.", File.separatorChar == '/')
    }

    @Test
    fun requiresATitleAndDescriptionButAllowsReportsWithoutStepsOrScreenshots() {
        assertThrows(IllegalArgumentException::class.java) { bugReportBody(" ", "", false) }
        assertThrows(IllegalArgumentException::class.java) {
            createBugReportEmailChooser(context, " ", "Opis", "", null)
        }
        val body = bugReportBody("Opis", "", false)
        assertTrue(body.contains("Nie podano."))
        assertTrue(body.contains("bez załącznika"))
        assertTrue(body.contains("API "))
        assertFalse(body.contains("GPS"))
        assertFalse(body.contains("Logi"))
        assertTrue(bugReportBody("Opis", "", true).contains("dołączony"))
    }

    @Test
    fun limitsImageInputToExactlyTenMiBAndRejectsEmptyOrInvalidImages() {
        val limit = 10 * 1024 * 1024
        val output = ByteArrayOutputStream()
        copyBugReportImage(ByteArrayInputStream(ByteArray(limit)), output)
        assertEquals(limit, output.size())
        assertThrows(IOException::class.java) {
            copyBugReportImage(ByteArrayInputStream(ByteArray(limit + 1)), ByteArrayOutputStream())
        }
        assertThrows(IOException::class.java) {
            copyBugReportImage(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
        }
        val invalid = File(context.cacheDir, "not-an-image.txt").apply { writeText("invalid") }
        assertThrows(IOException::class.java) { decodeBugReportImage(invalid) }
    }

    private fun addEmailApp(packageName: String, supportsAttachments: Boolean) {
        val info = ResolveInfo().apply {
            isDefault = true
            activityInfo = ActivityInfo().apply {
                name = "$packageName.Compose"
                this.packageName = packageName
                exported = true
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        }
        val pm = shadowOf(context.packageManager)
        val lookup = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val existing = context.packageManager.queryIntentActivities(lookup, 0)
        pm.addResolveInfoForIntent(lookup, existing + info)
        if (supportsAttachments) {
            pm.addResolveInfoForIntent(Intent(Intent.ACTION_SEND).setType("image/png").setPackage(packageName), info)
        }
    }
}
