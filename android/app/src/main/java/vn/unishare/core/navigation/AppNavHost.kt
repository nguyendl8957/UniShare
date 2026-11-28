package vn.unishare.core.navigation

import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.auth.ui.RETURN_TARGET_KEY
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.feature.explore.ui.DocumentDetailState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import vn.unishare.feature.profile.ui.HttpSellerDashboardRepository
import vn.unishare.feature.profile.ui.BuyerProfileRoleContract
import vn.unishare.core.ui.theme.UniAppMode
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

private val documentIdPattern = Regex("[A-Za-z0-9_-]{1,150}")
private const val API_URL = "https://asia-southeast1-unishare-db44a.cloudfunctions.net/api"
private const val PDF_PAGE = "pdf_page"

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val session by container.authRepository.session.collectAsStateWithLifecycle()
    val uid by rememberUpdatedState((session as? AuthSession.SignedIn)?.uid)
    var previousUid by rememberSaveable { mutableStateOf(uid) }

    val exploreViewModel: ExploreViewModel = viewModel(factory = viewModelFactory {
        initializer {
            ExploreViewModel(catalogRepository = FirestoreExploreCatalogRepository(apiBaseUrl = API_URL), typingDelayMillis = 250)
        }
    })
    val profileViewModel: ProfileViewModel = viewModel(factory = viewModelFactory {
        initializer {
            ProfileViewModel(
                authRepository = container.authRepository,
                profileRepository = container.profileRepository,
                sellerRoleContract = BuyerProfileRoleContract(apiBaseUrl = API_URL),
                sellerDashboardRepository = HttpSellerDashboardRepository(
                    firebaseAuth, API_URL, HttpSellerRevenueRepository(firebaseAuth, API_URL)
                )
            )
        }
    })
    val profile by profileViewModel.uiState.collectAsStateWithLifecycle()
    val mode by rememberUpdatedState(if (uid != null && profile.activeUid == uid && profile.sellerEnabled) {
        profile.currentMode
    } else {
        UniAppMode.BUYER
    })
    val libraryViewModel: PurchasedLibraryViewModel = viewModel(factory = viewModelFactory {
        initializer {
            PurchasedLibraryViewModel(
                container.authRepository,
                RealLibraryRepository(firebaseAuth, PurchasedLibraryRepositoryImpl(firebaseAuth, API_URL))
            )
        }
    })
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()

    fun navigateMainTab(route: String) {
        val destination = when (route) {
            "home" -> if (mode == UniAppMode.SELLER) "seller-home" else "home"
            "explore" -> if (mode == UniAppMode.SELLER) "seller-explore" else "home"
            "purchased", "library" -> "purchased"
            "profile" -> "profile"
            "manage" -> if (mode == UniAppMode.SELLER) "seller-documents" else return
            else -> return
        }
        if (route == "explore") exploreViewModel.openSearchMode()
        else if (destination == "home") exploreViewModel.backToHome()
        if (navController.currentDestination?.route == destination) return
        navController.navigate(destination) {
            val root = if (mode == UniAppMode.SELLER && destination != "seller-home" &&
                runCatching { navController.getBackStackEntry("seller-home") }.isSuccess
            ) "seller-home" else "home"
            popUpTo(root)
            launchSingleTop = true
        }
    }

    fun openAuth(target: String? = null, reauth: Boolean = false) {
        val route = if (target == null) "auth" else
            "auth?$RETURN_TARGET_KEY=${Uri.encode(target)}&reauth=$reauth"
        navController.navigate(route) { launchSingleTop = true }
    }

    fun purchasedTitle(docId: String): String? =
        (libraryState.takeIf { it.activeUid == uid }?.listState as? PurchasedLibraryListState.Content)
            ?.items?.firstOrNull { it.docId == docId }?.title

    LaunchedEffect(session) {
        if (session is AuthSession.Restoring) return@LaunchedEffect
        if (previousUid != null && previousUid != uid) {
            exploreViewModel.resetAllFiltersAndSearch()
            exploreViewModel.backToHome()
            navController.navigate("home") {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        previousUid = uid
    }

    var previousMode by rememberSaveable { mutableStateOf(UniAppMode.BUYER) }
    LaunchedEffect(mode, profile.isLoadingProfile, profile.activeUid, uid) {
        if (uid != null && (profile.isLoadingProfile || profile.activeUid != uid)) return@LaunchedEffect
        val current = navController.currentDestination?.route
        if (mode == UniAppMode.SELLER && previousMode != mode && current in setOf("home", "profile")) {
            navigateMainTab("home")
        } else if (mode == UniAppMode.BUYER && previousMode != mode &&
            current in setOf("seller-home", "seller-explore", "seller-documents", "seller/revenue", "seller-upload", "profile")
        ) {
            navigateMainTab("home")
        }
        previousMode = mode
    }

    UniShareTheme(mode) {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {

            for (route in listOf("home", "seller-explore")) composable(route) {
                ExploreScreen(
                    viewModel = exploreViewModel,
                    onDocumentClick = { docId ->
                        navController.navigate("document/$docId")
                    },
                    onProfileIconClick = {
                        navigateMainTab("profile")
                    },
                    onBottomNavSelected = ::navigateMainTab,
                    appMode = mode,
                    hasExtendedCatalogData = false,
                    onExitSearch = if (route == "seller-explore") ({ navigateMainTab("home") }) else null
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
                        if (uid == null) openAuth("document/$targetId")
                        else navController.navigate("checkout/$targetId") { launchSingleTop = true }
                    },
                    onReadClick = { targetId ->
                        navController.navigate("reader/$targetId") { launchSingleTop = true }
                    },
                    buyerUid = uid,
                    appMode = mode,
                    onShareClick = { detail ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${detail.title}\nUniShare")
                        }
                        context.startActivity(Intent.createChooser(intent, "Chia sẻ tài liệu"))
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
                                    apiBaseUrl = API_URL
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
            for (route in listOf("profile", "seller-home")) {
                composable(route) {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onLoginClick = { openAuth() },
                        onOpenPurchasedLibrary = { navigateMainTab("purchased") },
                        onOpenTransactionHistory = { navController.navigate("buyer/transactions") },
                        onOpenSellerManagement = { navigateMainTab("manage") },
                        onOpenSellerUpload = { navController.navigate("seller-upload") },
                        onOpenSellerRevenue = { navController.navigate("seller/revenue") },
                        onOpenPublicSellerProfile = { navController.navigate("seller-public/$it") },
                        onBottomNavSelected = ::navigateMainTab,
                        sellerProfile = route == "profile"
                    )
                }
            }
            composable("seller-documents") { entry ->
                val sellerUid = uid

                if (sellerUid == null || !profile.sellerEnabled) {
                    Text("Tài khoản chưa có quyền quản lý tài liệu.", Modifier.safeDrawingPadding())
                } else {
                    val managementViewModel: UploadWizardViewModel = viewModel(
                        viewModelStoreOwner = entry,
                        factory = viewModelFactory {
                            initializer {
                                val a04 = DocumentUploadRepositoryImpl(
                                    context = context.applicationContext,
                                    auth = firebaseAuth,
                                    storage = FirebaseStorage.getInstance(),
                                    apiBaseUrl = API_URL
                                )

                                val adapter = A04SellerRepositoryAdapter(
                                    a04 = a04,
                                    metadataProvider = { UploadWizardUiState() },
                                    auth = firebaseAuth,
                                    apiBaseUrl = API_URL
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
                        },
                        onBottomNavSelected = ::navigateMainTab
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
                                    apiBaseUrl = API_URL
                                )
                            )
                        }
                    }
                )

                if (mode == UniAppMode.SELLER) SellerRevenueScreen(
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
                val sellerUid = uid

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
                                            apiBaseUrl = API_URL
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
                                    exploreViewModel.invalidateCatalog()
                                    navController.navigate("seller-documents") {
                                        popUpTo("seller-upload") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }

                }
            }
            composable("purchased") {
                PurchasedLibraryScreen(
                    viewModel = libraryViewModel,
                    onOpenDocumentReader = { navController.navigate("reader/$it") },
                    onExploreClick = { navigateMainTab("explore") },
                    onLoginClick = { openAuth() },
                    onBottomNavSelected = ::navigateMainTab
                )
            }

            composable(
                route = "reader/{docId}?initialPage={initialPage}",
                arguments = listOf(
                    navArgument("docId") { type = NavType.StringType },
                    navArgument("initialPage") { type = NavType.IntType; defaultValue = 1 }
                )
            ) { entry ->
                val docId = entry.arguments?.getString("docId").orEmpty()
                val controller = remember(docId) {
                    PdfReaderController(context, firebaseAuth, PurchasedPdfApi(firebaseAuth, API_URL))
                }
                val citationPage by entry.savedStateHandle.getStateFlow(PDF_PAGE, 0).collectAsStateWithLifecycle()
                val exploreState by exploreViewModel.uiState.collectAsStateWithLifecycle()
                val catalogTitle = (exploreState.detailState as? DocumentDetailState.Content)?.detail
                    ?.takeIf { it.docId == docId }?.title
                val title = purchasedTitle(docId) ?: catalogTitle ?: "Tài liệu UniShare"
                RealPdfReaderScreen(
                    docId = docId,
                    title = title,
                    auth = firebaseAuth,
                    controller = controller,
                    initialPage = entry.arguments?.getInt("initialPage") ?: 1,
                    citationPage = citationPage,
                    onCitationConsumed = { entry.savedStateHandle[PDF_PAGE] = 0 },
                    onBackClick = { navController.popBackStack() },
                    onAskAiClick = { navController.navigate("ai-chat/$docId?title=${Uri.encode(title)}") },
                    onRequireLoginClick = { openAuth() },
                    onBottomNavSelected = ::navigateMainTab
                )
            }

            composable(
                route = "auth?$RETURN_TARGET_KEY={$RETURN_TARGET_KEY}&reauth={reauth}",
                arguments = listOf(
                    navArgument(RETURN_TARGET_KEY) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("reauth") { type = NavType.BoolType; defaultValue = false }
                )
            ) { entry ->
                val authViewModel: AuthViewModel = viewModel(
                    viewModelStoreOwner = entry,
                    factory = viewModelFactory {
                        initializer {
                            AuthViewModel(
                                authRepository = container.authRepository,
                                savedStateHandle = createSavedStateHandle(),
                                requireFreshSignIn = entry.arguments?.getBoolean("reauth") == true
                            )
                        }
                    }
                )

                AuthScreen(
                    viewModel = authViewModel,
                    onNavigateToTarget = { target ->
                        val checkoutDoc = target?.substringAfter("checkout:", "")
                            ?.takeIf(documentIdPattern::matches)
                        navController.popBackStack()
                        if (checkoutDoc != null) {
                            navController.popBackStack()
                            if (navController.currentDestination?.route != "document/{docId}") {
                                navController.navigate("document/$checkoutDoc") { launchSingleTop = true }
                            }
                        }
                    },
                    onAuthCanceled = {
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = "ai-chat/{docId}?title={title}",
                arguments = listOf(
                    navArgument("docId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "Tài liệu UniShare" }
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
                                    apiBaseUrl = API_URL
                                )
                            )
                        }
                    }
                )

                AiChatScreen(
                    docId = docId,
                    documentTitle = purchasedTitle(docId) ?: entry.arguments?.getString("title").orEmpty(),
                    viewModel = aiViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onCitationClick = { targetId, page ->
                        val previous = navController.previousBackStackEntry
                        if (previous?.arguments?.getString("docId") == targetId &&
                            previous.destination.route?.startsWith("reader/") == true
                        ) {
                            previous.savedStateHandle[PDF_PAGE] = page
                            navController.popBackStack()
                        } else {
                            navController.navigate("reader/$targetId?initialPage=$page")
                        }
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
                route = "checkout/{docId}?orderId={resumeOrderId}",
                arguments = listOf(
                    navArgument("docId") { type = NavType.StringType },
                    navArgument("resumeOrderId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                val docId = entry.arguments
                    ?.getString("docId")
                    .orEmpty()

                if (!documentIdPattern.matches(docId)) {
                    Text("Thiếu mã tài liệu.")
                } else {
                    val checkoutViewModel: CheckoutViewModel = viewModel(
                        viewModelStoreOwner = entry,
                        factory = viewModelFactory {
                            initializer {
                                CheckoutViewModel(
                                    docId = docId,
                                    orderRepository = container.createOrderRepository(
                                        apiBaseUrl = API_URL
                                    ),
                                    authRepository = container.authRepository,
                                    savedStateHandle = createSavedStateHandle(),
                                    resumeOrderId = entry.arguments?.getString("resumeOrderId")
                                )
                            }
                        }
                    )

                    CheckoutScreen(
                        viewModel = checkoutViewModel,
                        onNavigateToReader = { targetId ->
                            libraryViewModel.refreshLibrary()
                            navController.navigate("reader/$targetId") { launchSingleTop = true }
                        },
                        onNavigateToAuth = { target -> openAuth(target, reauth = uid != null) },
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
                                    apiBaseUrl = API_URL
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
                                    apiBaseUrl = API_URL
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
                        navController.navigate("checkout/$docId?orderId=${Uri.encode(orderId)}")
                    }
                )
            }
        }
    }
}
