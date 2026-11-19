package vn.unishare.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.data.remote.OrderApiAdapter
import vn.unishare.feature.payment.ui.ApiOrderRepository
import vn.unishare.feature.payment.ui.CheckoutScreenContent
import vn.unishare.feature.payment.ui.CheckoutUiState
import vn.unishare.feature.payment.ui.OrderStatus
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CheckoutApiIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val auth = FirebaseAuth.getInstance()

    @After
    fun cleanup() {
        auth.signOut()
    }

    @Test
    fun realOrderWithoutBankConfigNeverShowsFakeAccount() = runBlocking {
        auth.useEmulator("127.0.0.1", 9099)
        auth.signOut()

        val email = "b04-${UUID.randomUUID()}@example.test"

        auth.createUserWithEmailAndPassword(
            email,
            "EmulatorOnly_12345!"
        ).await()

        val repository = ApiOrderRepository(
            OrderApiAdapter(
                auth = auth,
                apiBaseUrl = "http://127.0.0.1:3000"
            )
        )

        val key = UUID.randomUUID().toString()

        val first = repository.createOrGetOrder(
            docId = "a07_android_adapter_test",
            idempotencyKey = key
        ).getOrThrow()

        assertNotNull(first.orderId)
        assertEquals(OrderStatus.PENDING, first.status)
        assertEquals(12000L, first.amountVnd)
        assertNull(first.transferInfo)

        val repeated = repository.createOrGetOrder(
            docId = "a07_android_adapter_test",
            idempotencyKey = key
        ).getOrThrow()

        assertEquals(first.orderId, repeated.orderId)

        val fetched = repository.getOrderStatus(
            first.orderId
        ).getOrThrow()

        assertEquals(first.orderId, fetched.orderId)
        assertEquals(OrderStatus.PENDING, fetched.status)
        assertNull(fetched.transferInfo)

        composeRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        order = fetched,
                        status = fetched.status,
                        remainingSeconds = 850L
                    ),
                    onCopyField = { _, _ -> },
                    onManualRefresh = {},
                    onOpenReader = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(
            "text_bank_unavailable"
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(
            "section_bank_instructions"
        ).assertDoesNotExist()

        println("PASS: B04 uses real A07 Order API")
        println("PASS: B04 idempotency")
        println("PASS: B04 hides unavailable bank instructions")
    }
}
