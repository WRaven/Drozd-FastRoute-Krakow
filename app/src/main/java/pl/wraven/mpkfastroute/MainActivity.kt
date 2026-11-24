package pl.wraven.mpkfastroute

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import pl.wraven.mpkfastroute.feature.search.FastRouteScreen
import pl.wraven.mpkfastroute.ui.theme.FastRouteTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            FastRouteTheme(darkTheme = darkTheme) {
                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                LaunchedEffect(backgroundColor, darkTheme) {
                    window.setBackgroundDrawable(backgroundColor.toDrawable())
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                FastRouteScreen()
            }
        }
    }
}
