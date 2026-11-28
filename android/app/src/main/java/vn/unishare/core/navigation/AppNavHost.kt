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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.remember
import vn.unishare.data.remote.PurchasedPdfApi
import vn.unishare.data.repository.PurchasedLibraryRepositoryImpl
import vn.unishare.data.repository.RealLibraryRepository
import vn.unishare.feature.library.reader.PdfReaderController
import vn.unishare.feature.library.ui.PurchasedLibraryScreen
import vn.unishare.feature.library.ui.PurchasedLibraryViewModel
import vn.unishare.feature.library.ui.RealPdfReaderScreen
import vn.unishare.feature.library.ui.PurchasedLibraryListState
import vn.unishare.feature.auth.ui.AuthScreen
import vn.unishare.feature.auth.ui.AuthViewModel
import vn.unishare.feature.payment.history.BuyerTransactionDetailScreen
import vn.unishare.feature.payment.history.BuyerTransactionsScreen
import vn.unishare.feature.payment.history.BuyerTransactionsViewModel
import vn.unishare.feature.payment.ui.CheckoutScreen
import vn.unishare.feature.payment.ui.CheckoutViewModel

private const val DEBUG_TEST_DOCUMENT = "draft_077e668cc1703bb4b3b8cd9c85ff9c8f704e282a102fa088ebe3538c033884d8"

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val logoutScope = rememberCoroutineScope()
    val context = LocalContext.current

    val isDebugBuild =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val isEmulator =
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.MODEL.contains("sdk_gphone", ignoreCase = true)

    val allowEmulatorCheckout = isDebugBuild && isEmulator
    val allowCheckout = isDebugBuild
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val c06ApiUrl = "https://asia-southeast1-unishare-db44a.cloudfunctions.net/api"
    val enableCloudLibrary = true

    // C06 local backend is available only in Debug on Android Emulator.
    // Release must not silently connect to localhost.
    val libraryViewModel: PurchasedLibraryViewModel? =
        if (enableCloudLibrary) {

            viewModel(
                factory = viewModelFactory {
                    initializer {
                        PurchasedLibraryViewModel(
                            authRepository = container.authRepository,
                            libraryRepository = RealLibraryRepository(
                                auth = firebaseAuth,
                                source = PurchasedLibraryRepositoryImpl(
                                    auth = firebaseAuth,
                                    apiBaseUrl = c06ApiUrl
                                )
                            )
                        )
                    }
                }
            )
        } else {
            null
        }

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

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    navController.navigate("purchased")
                }) {
                    Text("Thư viện Đã mua")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = {
                    navController.navigate("auth")
                }) {
                    Text("Đăng nhập UniShare")
                }
                if (allowEmulatorCheckout) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = {
                        logoutScope.launch {
                            val result = container.authRepository.signOut()

                            if (result.isSuccess) {
                                navController.navigate("purchased") {
                                    popUpTo("home") {
                                        inclusive = false
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Đăng xuất (Debug)")
                    }
                }
                if (allowCheckout) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        navController.navigate(
                            "checkout/$DEBUG_TEST_DOCUMENT"
                        )
                    }) {
                        Text("Kiểm thử Checkout B04")
                    }
                }

                if (isDebugBuild) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = {
                        navController.navigate("buyer/transactions")
                    }) {
                        Text("Lịch sử mua (B05 Demo)")
                    }
                }
            }
        }

        // C06 is enabled only for local Emulator testing
        // until a production backend has been configured.
        if (libraryViewModel != null) {
        composable(route = "purchased") {
            if (libraryViewModel == null) {
                Text("Thư viện Đã mua chưa được cấu hình cho môi trường này.")
            } else {
            PurchasedLibraryScreen(
                viewModel = libraryViewModel,
                onOpenDocumentReader = { docId ->
                    navController.navigate("reader/$docId")
                },
                onExploreClick = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onLoginClick = {
                    navController.navigate("auth")
                },
                onBottomNavSelected = { route ->
                    if (route == "home") {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                }
            )
            }
        }
        composable(
            route = "reader/{docId}",
            arguments = listOf(
                navArgument("docId") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            if (libraryViewModel == null) {
                Text("Trình đọc PDF chưa được cấu hình cho môi trường này.")
            } else {
            val docId = entry.arguments
                ?.getString("docId")
                .orEmpty()

            val controller = remember(docId) {
                PdfReaderController(
                    context = context,
                    auth = firebaseAuth,
                    pdfApi = PurchasedPdfApi(
                        auth = firebaseAuth,
                        apiBaseUrl = c06ApiUrl
                    )
                )
            }

            val libraryState by libraryViewModel.uiState.collectAsState()
            val listState = libraryState.listState

            val title = (
                listState as? PurchasedLibraryListState.Content
            )?.items?.firstOrNull {
                it.docId == docId
            }?.title ?: "Tài liệu UniShare"

            RealPdfReaderScreen(
                docId = docId,
                title = title,
                auth = firebaseAuth,
                controller = controller,
                onBackClick = {
                    navController.popBackStack()
                },
                onAskAiClick = {
                    // C07 will implement real AI navigation.
                },
                onRequireLoginClick = {
                    navController.navigate("auth")
                },
                onBottomNavSelected = { route ->
                    if (route == "purchased") {
                        navController.popBackStack()
                    }
                }
            )
            }
        }
        } else {
            composable(route = "purchased") {
                Text("Thư viện Đã mua chưa được cấu hình.")
            }

            composable(
                route = "reader/{docId}",
                arguments = listOf(
                    navArgument("docId") {
                        type = NavType.StringType
                    }
                )
            ) {
                Text("Trình đọc PDF chưa được cấu hình.")
            }
        }
        composable(route = "auth") { entry ->
            val authViewModel: AuthViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer {
                        AuthViewModel(
                            authRepository = container.authRepository,
                            savedStateHandle = createSavedStateHandle()
                        )
                    }
                }
            )

            AuthScreen(
                viewModel = authViewModel,
                onNavigateToTarget = {
                    navController.navigate("purchased") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
                onAuthCanceled = {
                    navController.popBackStack()
                }
            )
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

            if (!allowCheckout) {
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
                                    apiBaseUrl = c06ApiUrl
                                ),
                                authRepository = container.authRepository,
                                savedStateHandle = createSavedStateHandle()
                            )
                        }
                    }
                )

                CheckoutScreen(
                    viewModel = checkoutViewModel,
                    onNavigateToReader = { targetId ->
                        navController.navigate("reader/$targetId")
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

        composable(route = "buyer/transactions") { entry ->
            val transactionsViewModel: BuyerTransactionsViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer {
                        BuyerTransactionsViewModel(
                            authRepository = container.authRepository,
                            transactionsRepository = container.createBuyerTransactionsRepository(),
                            savedStateHandle = createSavedStateHandle()
                        )
                    }
                }
            )

            BuyerTransactionsScreen(
                viewModel = transactionsViewModel,
                onNavigateToDetail = { orderId ->
                    navController.navigate("buyer/transactions/$orderId")
                },
                onNavigateToAuth = {
                    navController.navigate("auth")
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "buyer/transactions/{orderId}",
            arguments = listOf(
                navArgument("orderId") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            val orderId = entry.arguments
                ?.getString("orderId")
                .orEmpty()

            val detailViewModel: BuyerTransactionsViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer {
                        BuyerTransactionsViewModel(
                            authRepository = container.authRepository,
                            transactionsRepository = container.createBuyerTransactionsRepository(),
                            savedStateHandle = createSavedStateHandle()
                        )
                    }
                }
            )

            BuyerTransactionDetailScreen(
                viewModel = detailViewModel,
                orderId = orderId,
                onBackClick = {
                    navController.popBackStack()
                },
                onNavigateToReader = { docId ->
                    navController.navigate("reader/$docId")
                },
                onNavigateToCheckout = { docId ->
                    navController.navigate("checkout/$docId")
                }
            )
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
