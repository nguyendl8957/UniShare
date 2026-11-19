package vn.unishare.core.navigation

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.unishare.core.di.AppContainer
import vn.unishare.feature.payment.ui.CheckoutScreen
import vn.unishare.feature.payment.ui.CheckoutViewModel

private const val DEBUG_TEST_DOCUMENT = "a07_android_adapter_test"

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val isDebugBuild =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val isEmulator =
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.MODEL.contains("sdk_gphone", ignoreCase = true)

    val allowEmulatorCheckout = isDebugBuild && isEmulator

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = container.appName,
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    navController.navigate("second")
                }) {
                    Text("Đi tới màn hình 2")
                }

                if (allowEmulatorCheckout) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        navController.navigate(
                            "checkout/$DEBUG_TEST_DOCUMENT"
                        )
                    }) {
                        Text("Kiểm thử Checkout B04")
                    }
                }
            }
        }

        composable("second") {
            DemoScreen(
                title = "Màn hình thử nghiệm 2",
                buttonText = "Quay lại",
                onClick = { navController.popBackStack() }
            )
        }

        composable(
            route = "checkout/{docId}",
            arguments = listOf(
                navArgument("docId") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            val docId = entry.arguments
                ?.getString("docId")
                .orEmpty()

            if (!allowEmulatorCheckout) {
                Text("Checkout chưa được cấu hình cho môi trường này.")
            } else if (docId.isBlank()) {
                Text("Thiếu mã tài liệu.")
            } else {
                val checkoutViewModel: CheckoutViewModel = viewModel(
                    viewModelStoreOwner = entry,
                    factory = viewModelFactory {
                        initializer {
                            CheckoutViewModel(
                                docId = docId,
                                orderRepository = container.createOrderRepository(
                                    apiBaseUrl = "http://127.0.0.1:3000"
                                ),
                                authRepository = container.authRepository,
                                savedStateHandle = createSavedStateHandle()
                            )
                        }
                    }
                )

                CheckoutScreen(
                    viewModel = checkoutViewModel,
                    onNavigateToReader = {
                        // A06 reader is not yet connected.
                        navController.popBackStack()
                    },
                    onNavigateToAuth = {
                        // Auth return navigation will be integrated separately.
                        navController.popBackStack()
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
private fun DemoScreen(
    title: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}
