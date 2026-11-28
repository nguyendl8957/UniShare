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
import vn.unishare.feature.ai.ui.AiChatScreen
import vn.unishare.feature.ai.ui.AiChatViewModel
import vn.unishare.feature.ai.ui.HttpDocumentAiRepository
import vn.unishare.feature.explore.ui.ExploreScreen
import vn.unishare.feature.explore.ui.ExploreViewModel
import vn.unishare.feature.explore.ui.FirestoreExploreCatalogRepository
import vn.unishare.feature.explore.ui.DocumentDetailScreen
import vn.unishare.feature.profile.ui.ProfileScreen
import vn.unishare.feature.profile.ui.SellerPublicProfileScreen
import vn.unishare.feature.profile.ui.ProfileViewModel
import vn.unishare.feature.profile.ui.BuyerProfileRoleContract
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.navigationBarsPadding
import vn.unishare.core.ui.components.UniBottomBar
import vn.unishare.core.ui.theme.UniAppMode
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import androidx.compose.runtime.produceState
import kotlinx.coroutines.tasks.await
import vn.unishare.data.repository.DocumentUploadRepositoryImpl
import vn.unishare.feature.seller.ui.A04SellerRepositoryAdapter
import vn.unishare.feature.seller.ui.UploadWizardScreen
import vn.unishare.feature.seller.ui.SellerDocumentsScreen
import vn.unishare.feature.revenue.ui.SellerRevenueScreen
import vn.unishare.feature.revenue.ui.SellerRevenueViewModel
import vn.unishare.feature.revenue.ui.HttpSellerRevenueRepository
import vn.unishare.feature.seller.ui.UploadWizardUiState
import vn.unishare.feature.seller.ui.UploadWizardViewModel

private const val DEBUG_TEST_DOCUMENT = "draft_077e668cc1703bb4b3b8cd9c85ff9c8f704e282a102fa088ebe3538c033884d8"

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    // Centralized navigation for the four Buyer tabs.
    fun navigateMainTab(route: String) {
        val destination = when (route) {
            "home" -> "home"
            "explore" -> "explore"
            "purchased", "library" -> "purchased"
            "profile" -> "profile"
            else -> return
        }

        if (navController.currentDestination?.route == destination) {
            return
        }

        navController.navigate(destination) {
            popUpTo("home") {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    // C07 UI integration: uses sample catalog data for now.
    val exploreViewModel: ExploreViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ExploreViewModel(
                    catalogRepository = FirestoreExploreCatalogRepository()
                )
            }
        }
    )
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

        composable("explore") {
            ExploreScreen(
                viewModel = exploreViewModel,
                onDocumentClick = { docId ->
                    navController.navigate("document/$docId")
                },
                onProfileIconClick = {
                    navController.navigate("profile")
                },
                onBottomNavSelected = { route -> navigateMainTab(route) }
            )
        }

        composable(
            route = "document/{docId}",
            arguments = listOf(
                navArgument("docId") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            val docId = entry.arguments
                ?.getString("docId")
                .orEmpty()

            DocumentDetailScreen(
                docId = docId,
                viewModel = exploreViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onBuyClick = { targetId ->
                    if (allowCheckout) {
                        navController.navigate("checkout/$targetId")
                    }
                },
                onSellerClick = { sellerId ->
                    if (sellerId.matches(Regex("[A-Za-z0-9_-]{1,150}"))) {
                        navController.navigate("seller-public/$sellerId")
                    }
                },
                onBottomNavSelected = { route -> navigateMainTab(route) }
            )
        }
        composable(
            route = "seller-public/{sellerId}",
            arguments = listOf(
                navArgument("sellerId") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            val sellerId = entry.arguments
                ?.getString("sellerId")
                .orEmpty()

            val publicProfileViewModel: ProfileViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer {
                        ProfileViewModel(
                            authRepository = container.authRepository,
                            profileRepository = container.profileRepository,
                            sellerRoleContract = BuyerProfileRoleContract(
                                apiBaseUrl = c06ApiUrl
                            )
                        )
                    }
                }
            )

            SellerPublicProfileScreen(
                sellerId = sellerId,
                viewModel = publicProfileViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onBackToListingClick = {
                    navController.popBackStack()
                },
                onDocumentClick = { targetDocId ->
                    if (targetDocId.matches(Regex("[A-Za-z0-9_-]{1,150}"))) {
                        navController.navigate("document/$targetDocId")
                    }
                }
            )
        }
        composable("profile") { entry ->
            val profileViewModel: ProfileViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer {
                        ProfileViewModel(
                            authRepository = container.authRepository,
                            profileRepository = container.profileRepository,
                            sellerRoleContract = BuyerProfileRoleContract(apiBaseUrl = c06ApiUrl)
                        )
                    }
                }
            )

            ProfileScreen(
                viewModel = profileViewModel,
                onLoginClick = {
                    navController.navigate("auth")
                },
                onOpenPurchasedLibrary = {
                    navigateMainTab("purchased")
                },
                onOpenTransactionHistory = {
                    navController.navigate("buyer/transactions")
                },
                onOpenSellerManagement = {
                    navController.navigate("seller-documents")
                },
                onOpenSellerUpload = {
                    navController.navigate("seller-upload")
                },
                onBottomNavSelected = { route -> navigateMainTab(route) }
            )
        }
        composable("seller-documents") { entry ->
            val sellerUid = firebaseAuth.currentUser?.uid

            if (sellerUid == null) {
                Text("Vui lòng đăng nhập để quản lý tài liệu.")
            } else {
                val managementViewModel: UploadWizardViewModel = viewModel(
                    viewModelStoreOwner = entry,
                    factory = viewModelFactory {
                        initializer {
                            val a04 = DocumentUploadRepositoryImpl(
                                context = context.applicationContext,
                                auth = firebaseAuth,
                                storage = FirebaseStorage.getInstance(),
                                apiBaseUrl = c06ApiUrl
                            )

                            val adapter = A04SellerRepositoryAdapter(
                                a04 = a04,
                                metadataProvider = { UploadWizardUiState() },
                                auth = firebaseAuth,
                                apiBaseUrl = c06ApiUrl
                            )

                            UploadWizardViewModel(
                                repository = adapter,
                                autoLoadDocuments = true
                            )
                        }
                    }
                )

                SellerDocumentsScreen(
                    viewModel = managementViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onCreateDocumentClick = {
                        navController.navigate("seller-upload")
                    },
                    onViewDetailClick = { docId ->
                        navController.navigate("document/$docId")
                    },
                    onRevenueTabClick = {
                        navController.navigate("seller/revenue")
                    }
                )
            }
        }
        composable("seller/revenue") { entry ->
            val revenueViewModel: SellerRevenueViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer {
                        SellerRevenueViewModel(
                            authRepository = container.authRepository,
                            revenueRepository = HttpSellerRevenueRepository(
                                auth = firebaseAuth,
                                apiBaseUrl = c06ApiUrl
                            )
                        )
                    }
                }
            )

            SellerRevenueScreen(
                viewModel = revenueViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLoginClick = {
                    navController.navigate("auth")
                }
            )
        }
        composable("seller-upload") { entry ->
            val sellerUid = firebaseAuth.currentUser?.uid

            if (sellerUid == null) {
                Text("Vui lòng đăng nhập để đăng tài liệu.")
            } else {
                val sellerAllowed by produceState<Boolean?>(
                    initialValue = null,
                    key1 = sellerUid
                ) {
                    value = runCatching {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(sellerUid)
                            .get()
                            .await()
                            .getBoolean("sellerEnabled") == true
                    }.getOrDefault(false)
                }

                when (sellerAllowed) {
                    null -> Text("Đang kiểm tra quyền người bán...")

                    false -> Text(
                        "Tài khoản chưa có quyền Seller hoặc không thể xác minh quyền."
                    )

                    true -> {
                        val wizard: UploadWizardViewModel = viewModel(
                            viewModelStoreOwner = entry,
                            factory = viewModelFactory {
                                initializer {
                                    lateinit var instance: UploadWizardViewModel

                                    val a04 = DocumentUploadRepositoryImpl(
                                        context = context.applicationContext,
                                        auth = firebaseAuth,
                                        storage = FirebaseStorage.getInstance(),
                                        apiBaseUrl = c06ApiUrl
                                    )

                                    val adapter = A04SellerRepositoryAdapter(
                                        a04 = a04,
                                        metadataProvider = {
                                            instance.wizardState.value
                                        }
                                    )

                                    instance = UploadWizardViewModel(
                                        repository = adapter,
                                        autoLoadDocuments = false
                                    )

                                    instance
                                }
                            }
                        )

                        UploadWizardScreen(
                            viewModel = wizard,
                            onClose = {
                                navController.popBackStack()
                            },
                            onPublishSuccessNavigate = {
                                navController.navigate("seller-documents") {
                                    popUpTo("seller-upload") {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }

            }
        }
        composable("home") {
            UniShareBuyerHome(
                onExploreClick = { navigateMainTab("explore") },
                onPurchasedClick = { navigateMainTab("purchased") },
                onProfileClick = { navigateMainTab("profile") },
                onSellerUploadClick = {
                    navController.navigate("seller-upload")
                },
                onBottomNavSelected = { route -> navigateMainTab(route) }
            )
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
                    navController.navigate("explore") {
                        popUpTo("explore") { inclusive = false }
                    }
                },
                onLoginClick = {
                    navController.navigate("auth")
                },
                onBottomNavSelected = { route -> navigateMainTab(route) }
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
                    navController.navigate("ai-chat/$docId")
                },
                onRequireLoginClick = {
                    navController.navigate("auth")
                },
                onBottomNavSelected = { route ->
                    navigateMainTab(route)
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
                    if (!navController.popBackStack()) {
                        navigateMainTab("explore")
                    }
                },
                onAuthCanceled = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = "ai-chat/{docId}",
            arguments = listOf(
                navArgument("docId") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            val docId = entry.arguments
                ?.getString("docId")
                .orEmpty()

            val aiViewModel: AiChatViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer {
                        AiChatViewModel(
                            authRepository = container.authRepository,
                            aiRepository = HttpDocumentAiRepository(
                                auth = firebaseAuth,
                                apiBaseUrl = c06ApiUrl
                            )
                        )
                    }
                }
            )

            AiChatScreen(
                docId = docId,
                documentTitle = "Tài liệu UniShare",
                viewModel = aiViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onCitationClick = { _, _ ->
                    navController.popBackStack()
                },
                onLoginClick = {
                    navController.navigate("auth")
                },
                onBottomNavSelected = { route ->
                    navigateMainTab(route)
                }
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
                            transactionsRepository = container.createBuyerTransactionsRepository(
                                apiBaseUrl = c06ApiUrl
                            ),
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
                            transactionsRepository = container.createBuyerTransactionsRepository(
                                apiBaseUrl = c06ApiUrl
                            ),
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
private fun UniShareBuyerHome(
    onExploreClick: () -> Unit,
    onPurchasedClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSellerUploadClick: () -> Unit,
    onBottomNavSelected: (String) -> Unit
) {
    Scaffold(
        bottomBar = {
            UniBottomBar(
                selectedRoute = "home",
                onItemSelected = onBottomNavSelected,
                mode = UniAppMode.BUYER,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "UniShare",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Nền tảng chia sẻ tài liệu học tập.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onExploreClick) {
                Text("Khám phá tài liệu")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = onPurchasedClick) {
                Text("Thư viện đã mua")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = onProfileClick) {
                Text("Hồ sơ cá nhân")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = onSellerUploadClick) {
                Text("Đăng tài liệu (Seller)")
            }
        }
    }
}