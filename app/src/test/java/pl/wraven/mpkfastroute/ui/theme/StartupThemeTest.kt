package pl.wraven.mpkfastroute.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class StartupThemeTest {
    @Test
    fun manifestUsesTheAppLaunchTheme() {
        val application = document("AndroidManifest.xml")
            .getElementsByTagName("application").item(0) as Element
        assertEquals("@style/Theme.FastRoute", application.getAttribute("android:theme"))
    }

    @Test
    fun usesTheDrozdDisplayNameAndUnofficialDescription() {
        assertEquals(
            "Drozd FastRoute",
            resource("values", "strings.xml", "string", "app_name").textContent
        )
        assertEquals(
            "Drozd FastRoute",
            resource("values", "strings.xml", "string", "app_title").textContent
        )
        assertEquals(
            "Rozkład jazdy Kraków — aplikacja nieoficjalna",
            resource("values", "strings.xml", "string", "app_description").textContent
        )
        val debugName = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(
            Path.of("src", "debug", "res", "values", "strings.xml").toFile()
        ).getElementsByTagName("string").item(0).textContent
        assertEquals("Drozd FastRoute (test)", debugName)
    }

    @Test
    fun splashWindowAndSystemBarsShareTheProfileBackground() {
        val theme = resource("values", "themes.xml", "style", "Theme.FastRoute")
        val items = theme.getElementsByTagName("item")
        val values = (0 until items.length).associate { index ->
            val item = items.item(index) as Element
            item.getAttribute("name") to item.textContent.trim()
        }
        listOf(
            "android:colorBackground",
            "android:statusBarColor",
            "android:navigationBarColor",
            "android:windowSplashScreenBackground"
        ).forEach { attribute ->
            assertEquals(attribute, "@color/fast_route_window_background", values[attribute])
        }
        assertEquals("@drawable/launch_background", values["android:windowBackground"])
        assertEquals("@drawable/ic_splash_logo", values["android:windowSplashScreenAnimatedIcon"])
        listOf("android:windowLightStatusBar", "android:windowLightNavigationBar")
            .forEach { attribute ->
                assertEquals(attribute, "@bool/fast_route_light_system_bars", values[attribute])
            }
    }

    @Test
    fun nativeThemeAndSystemBarContrastFollowTheSystemProfile() {
        assertEquals(
            "@android:style/Theme.Material.Light.NoActionBar",
            resource("values", "themes.xml", "style", "Theme.FastRoute.Base")
                .getAttribute("parent")
        )
        assertEquals(
            "@android:style/Theme.Material.NoActionBar",
            resource("values-night", "themes.xml", "style", "Theme.FastRoute.Base")
                .getAttribute("parent")
        )
        assertEquals(
            "true",
            resource("values", "themes.xml", "bool", "fast_route_light_system_bars").textContent
        )
        assertEquals(
            "false",
            resource("values-night", "themes.xml", "bool", "fast_route_light_system_bars").textContent
        )
    }

    @Test
    fun fallbackBackgroundsMatchTheComposeColorSchemes() {
        assertEquals(lightColorScheme().background.toArgb(), backgroundArgb("values"))
        assertEquals(darkColorScheme().background.toArgb(), backgroundArgb("values-night"))
    }

    @Test
    fun dynamicBackgroundsFollowBothProfilesOnAndroid12And14() {
        mapOf(
            "values-v31" to "@android:color/system_neutral2_10",
            "values-night-v31" to "@android:color/system_neutral2_900",
            "values-v34" to "@android:color/system_background_light",
            "values-night-v34" to "@android:color/system_background_dark"
        ).forEach { (directory, expected) ->
            assertEquals(
                directory,
                expected,
                resource(directory, "colors.xml", "color", "fast_route_window_background")
                    .textContent
            )
        }
    }

    private fun backgroundArgb(directory: String): Int =
        resource(directory, "colors.xml", "color", "fast_route_window_background")
            .textContent.removePrefix("#").toLong(16).toInt() or 0xFF000000.toInt()

    private fun resource(directory: String, file: String, tag: String, name: String): Element {
        val elements = document("res", directory, file).getElementsByTagName(tag)
        return (0 until elements.length).map { elements.item(it) as Element }
            .single { it.getAttribute("name") == name }
    }

    private fun document(vararg path: String) =
        DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(Path.of("src", "main", *path).toFile())
}
