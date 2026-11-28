package vn.unishare.feature.library

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unishare.data.remote.PdfApiException
import vn.unishare.data.remote.PurchasedPdfApi
import vn.unishare.feature.library.reader.PdfReaderController
import vn.unishare.feature.library.ui.RealPdfReaderScreen

@RunWith(AndroidJUnit4::class)
class PdfReaderControllerIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readerConsumesSavedCitationAfterPdfLoadsAndIgnoresOutOfRangePage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = FirebaseAuth.getInstance()
        auth.useEmulator("127.0.0.1", 9099)
        auth.signOut()

        val controller = PdfReaderController(
            context, auth, PurchasedPdfApi(auth, "http://127.0.0.1:3000")
        )
        val savedState = SavedStateHandle(mapOf("pdf_page" to 2))
        try {
            runBlocking {
                auth.signInWithEmailAndPassword(
                    "a06-reader-owner@example.test", "EmulatorOnly_12345!"
                ).await()
            }
            composeRule.setContent {
                val page by savedState.getStateFlow("pdf_page", 0).collectAsState()
                RealPdfReaderScreen(
                    docId = "a06_pixel_reader_fixture",
                    title = "PDF Fixture",
                    auth = auth,
                    controller = controller,
                    onBackClick = {},
                    citationPage = page,
                    onCitationConsumed = { savedState["pdf_page"] = 0 }
                )
            }
            composeRule.waitUntil(15_000) {
                savedState.get<Int>("pdf_page") == 0
            }
            composeRule.onNodeWithTag("reader_real_pdf_page").assertIsDisplayed()
            composeRule.onNodeWithText("Trang 2/2").assertIsDisplayed()

            composeRule.runOnIdle { savedState["pdf_page"] = 5 }
            composeRule.waitUntil(5_000) { savedState.get<Int>("pdf_page") == 0 }
            composeRule.onNodeWithText("Trang 2/2").assertIsDisplayed()
        } finally {
            runBlocking { controller.close() }
            auth.signOut()
        }
    }

    @Test
    fun purchasedPdfRendersTwoPagesAndRejectsOtherBuyer() = runBlocking {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val auth = FirebaseAuth.getInstance()
        auth.useEmulator("127.0.0.1", 9099)
        auth.signOut()

        val api = PurchasedPdfApi(
            auth = auth,
            apiBaseUrl = "http://127.0.0.1:3000"
        )

        val controller = PdfReaderController(
            context = context,
            auth = auth,
            pdfApi = api
        )

        val docId = "a06_pixel_reader_fixture"

        try {
            auth.signInWithEmailAndPassword(
                "a06-reader-owner@example.test",
                "EmulatorOnly_12345!"
            ).await()

            val info = controller.openDocument(docId)

            assertEquals(docId, info.docId)
            assertEquals(2, info.pageCount)

            println("PASS: Purchased PDF opens with 2 pages")

            val firstPage = controller.renderPage(0)
            val firstColor = firstPage.getPixel(
                firstPage.width / 2,
                firstPage.height / 2
            )

            assertTrue(firstPage.width > 0)
            assertTrue(firstPage.height > 0)
            firstPage.recycle()

            val secondPage = controller.renderPage(1)
            val secondColor = secondPage.getPixel(
                secondPage.width / 2,
                secondPage.height / 2
            )

            assertTrue(secondPage.width > 0)
            assertTrue(secondPage.height > 0)
            secondPage.recycle()

            assertNotEquals(firstColor, secondColor)

            println("PASS: Android PdfRenderer renders distinct pages")

            awaitClose(controller)

            val renderAfterClose = runCatching {
                controller.renderPage(0)
            }

            assertTrue(renderAfterClose.isFailure)

            println("PASS: Closed Reader rejects rendering")

            // Sign in as a different Buyer with no entitlement.
            auth.signOut()

            auth.signInWithEmailAndPassword(
                "a06-reader-other@example.test",
                "EmulatorOnly_12345!"
            ).await()

            val denied = runCatching {
                api.downloadPurchasedPdf(docId)
            }.exceptionOrNull()

            assertTrue(denied is PdfApiException)
            assertEquals(403, (denied as PdfApiException).httpStatus)

            println("PASS: Other Buyer cannot download purchased PDF")
            println("PASS: A06.9 ANDROID PDF INTEGRATION")
        } finally {
            awaitClose(controller)
            auth.signOut()
        }
    }

    @Test
    fun changingAccountInvalidatesOpenPdf() = runBlocking {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val auth = FirebaseAuth.getInstance()
        auth.useEmulator("127.0.0.1", 9099)
        auth.signOut()

        val controller = PdfReaderController(
            context = context,
            auth = auth,
            pdfApi = PurchasedPdfApi(
                auth = auth,
                apiBaseUrl = "http://127.0.0.1:3000"
            )
        )

        try {
            auth.signInWithEmailAndPassword(
                "a06-reader-owner@example.test",
                "EmulatorOnly_12345!"
            ).await()

            val info = controller.openDocument(
                "a06_pixel_reader_fixture"
            )

            assertEquals(2, info.pageCount)

            auth.signOut()

            val afterLogout = runCatching {
                controller.renderPage(0)
            }

            assertTrue(afterLogout.isFailure)

            auth.signInWithEmailAndPassword(
                "a06-reader-other@example.test",
                "EmulatorOnly_12345!"
            ).await()

            val afterAccountSwitch = runCatching {
                controller.renderPage(0)
            }

            assertTrue(afterAccountSwitch.isFailure)

            println("PASS: Logout invalidates open PDF")
            println("PASS: Another Buyer cannot reuse PDF renderer")
        } finally {
            controller.close()
            auth.signOut()
        }
    }
    private suspend fun awaitClose(
        controller: PdfReaderController
    ) {
        controller.close()
    }
}
