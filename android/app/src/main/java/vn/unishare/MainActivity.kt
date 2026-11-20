package vn.unishare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
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
                UniShareStartup(
                    container = appContainer
                )
            }
        }
    }
}

@Composable
private fun UniShareStartup(
    container: AppContainer
) {
    var showSplash by rememberSaveable {
        mutableStateOf(true)
    }

    LaunchedEffect(showSplash) {
        if (showSplash) {
            delay(2000L)
            showSplash = false
        }
    }

    if (showSplash) {
        Image(
            painter = painterResource(
                id = R.drawable.unishare_splash
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        AppNavHost(container = container)
    }
}