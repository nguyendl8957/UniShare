package vn.unishare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.unishare.core.di.AppContainer
import vn.unishare.core.navigation.AppNavHost
import vn.unishare.ui.theme.UniShareTheme

class MainActivity : ComponentActivity() {

    private val appContainer = AppContainer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UniShareTheme {
                AppNavHost(container = appContainer)
            }
        }
    }
}