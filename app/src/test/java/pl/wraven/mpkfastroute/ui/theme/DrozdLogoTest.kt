package pl.wraven.mpkfastroute.ui.theme

import java.nio.file.Path
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class DrozdLogoTest {
    @Test
    fun croppedLogoIsSquareWithTransparentCornersAndVisibleArtwork() {
        val image = ImageIO.read(resourcePath("drawable-nodpi", "drozd_logo.png").toFile())
        assertEquals(512, image.width)
        assertEquals(512, image.height)
        assertTrue(image.colorModel.hasAlpha())
        listOf(0 to 0, 511 to 0, 0 to 511, 511 to 511).forEach { (x, y) ->
            assertEquals(0, image.getRGB(x, y) ushr 24)
        }
        assertEquals(255, image.getRGB(256, 256) ushr 24)
        assertTrue(image.getRGB(256, 64) != image.getRGB(256, 256))
    }

    @Test
    fun adaptiveForegroundFitsInsideTheSafeCircleWithoutCroppingTheBadge() {
        val image = ImageIO.read(
            resourcePath("drawable-xxxhdpi", "ic_launcher_foreground.png").toFile()
        )
        assertEquals(432, image.width)
        assertEquals(432, image.height)
        var visiblePixels = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 > 16) {
                    visiblePixels += 1
                    val dx = x + 0.5 - 216
                    val dy = y + 0.5 - 216
                    assertTrue("Artwork outside safe circle at $x,$y", dx * dx + dy * dy <= 132 * 132)
                }
            }
        }
        assertTrue(visiblePixels > 30_000)
    }

    @Test
    fun legacyStartupKeepsTheProfileBackgroundBehindTheSameLogo() {
        val items = drawableItems("launch_background.xml")
        assertEquals(
            "@color/fast_route_window_background",
            items[0].getAttribute("android:drawable")
        )
        assertCenteredLogo(items[1])
    }

    @Test
    fun android12SplashUsesAPaddedCanvasAndTheSameCenteredLogo() {
        val items = drawableItems("ic_splash_logo.xml")
        assertEquals("288dp", items[0].getAttribute("android:width"))
        assertEquals("288dp", items[0].getAttribute("android:height"))
        assertCenteredLogo(items[1])
    }

    private fun assertCenteredLogo(item: Element) {
        assertEquals("@drawable/drozd_logo", item.getAttribute("android:drawable"))
        assertEquals("152dp", item.getAttribute("android:width"))
        assertEquals("152dp", item.getAttribute("android:height"))
        assertEquals("center", item.getAttribute("android:gravity"))
    }

    private fun drawableItems(file: String): List<Element> {
        val nodes = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(resourcePath("drawable", file).toFile())
            .getElementsByTagName("item")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun resourcePath(directory: String, file: String): Path =
        Path.of("src", "main", "res", directory, file)
}
