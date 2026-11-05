package pl.wraven.mpkfastroute

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import pl.wraven.mpkfastroute.feature.search.FastRouteScreen
import pl.wraven.mpkfastroute.ui.theme.FastRouteTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FastRouteTheme {
                FastRouteScreen()
            }
        }
    }
}
