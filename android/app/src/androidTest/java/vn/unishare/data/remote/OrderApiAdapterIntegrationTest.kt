package vn.unishare.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OrderApiAdapterIntegrationTest {

    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: OrderApiAdapter

    private val docId = "a07_android_adapter_test"

    @Before
    fun setup() {
        auth = FirebaseAuth.getInstance()
        auth.useEmulator("127.0.0.1", 9099)
        auth.signOut()

        adapter = OrderApiAdapter(
            auth = auth,
            apiBaseUrl = "http://127.0.0.1:3000"
        )
    }

    @After
    fun cleanup() {
        auth.signOut()
    }

    @Test
    fun buyerCreatesAndReadsRealOrder() = runBlocking {
        val email = "a07-${UUID.randomUUID()}@example.test"

        val user = auth.createUserWithEmailAndPassword(
            email,
            "EmulatorOnly_12345!"
        ).await().user

        assertNotNull(user)

        val key = UUID.randomUUID().toString()

        val first = adapter.createOrGetOrder(docId, key)

        assertTrue(first.orderId.startsWith("ord_"))
        assertEquals(docId, first.docId)
        assertEquals(12000L, first.amountVnd)
        assertEquals("PENDING", first.status)
        assertEquals("VND", first.currency)
        assertFalse(first.paymentCode.isBlank())

        val repeated = adapter.createOrGetOrder(docId, key)

        assertEquals(first.orderId, repeated.orderId)

        val fetched = adapter.getOrderStatus(first.orderId)

        assertEquals(first.orderId, fetched.orderId)
        assertEquals("PENDING", fetched.status)
        assertEquals(12000L, fetched.amountVnd)

        // Backend dang chay voi cau hinh ngan hang gia lap chua day du.
        // Khong duoc hien thi so tai khoan tu tao.
        assertNull(fetched.transferInstructions)

        println("PASS: Android creates PENDING via real API")
        println("PASS: Android idempotency")
        println("PASS: Android reads server order status")
        println("PASS: No fabricated bank instructions")
    }
}
