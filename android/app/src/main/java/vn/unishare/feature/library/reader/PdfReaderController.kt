package vn.unishare.feature.library.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vn.unishare.data.remote.PurchasedPdfApi
import java.io.File
import java.io.IOException

data class PdfReaderInfo(
    val docId: String,
    val pageCount: Int
)

/**
 * A06: Open and render purchased PDF files.
 *
 * Security:
 * - Downloads PDF only through the authenticated A06 backend.
 * - Uses temporary private storage.
 * - Deletes the temporary file after PdfRenderer opens it.
 * - Rejects rendering after account changes.
 * - Closes native resources when reading ends.
 *
 * This controller does not determine entitlement.
 * The backend remains the source of authorization.
 */
class PdfReaderController(
    context: Context,
    private val auth: FirebaseAuth,
    private val pdfApi: PurchasedPdfApi
) {
    private val cacheDir = context.applicationContext.cacheDir
    private val mutex = Mutex()

    private var renderer: PdfRenderer? = null
    private var activeUid: String? = null
    private var activeDocId: String? = null

    /**
     * Download and open the purchased document.
     *
     * pageCount is returned only after a real PDF is opened.
     */
    suspend fun openDocument(
        docId: String
    ): PdfReaderInfo = mutex.withLock {
        closeInternal()

        val uid = auth.currentUser?.uid
            ?: throw IOException("Authentication required")

        // The API authenticates the request and checks entitlement.
        val pdfBytes = pdfApi.downloadPurchasedPdf(docId)

        if (auth.currentUser?.uid != uid) {
            throw IOException("Account changed during PDF download")
        }

        withContext(Dispatchers.IO) {
            var temporaryFile: File? = null
            var descriptor: ParcelFileDescriptor? = null
            var newRenderer: PdfRenderer? = null

            try {
                temporaryFile = File.createTempFile(
                    "unishare_pdf_",
                    ".tmp",
                    cacheDir
                )

                temporaryFile.outputStream().use { output ->
                    output.write(pdfBytes)
                }

                descriptor = ParcelFileDescriptor.open(
                    temporaryFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )

                newRenderer = PdfRenderer(descriptor)
                descriptor = null

                val count = newRenderer.pageCount

                if (count <= 0) {
                    throw IOException("PDF contains no pages")
                }

                if (auth.currentUser?.uid != uid) {
                    throw IOException("Account changed during PDF opening")
                }

                renderer = newRenderer
                newRenderer = null

                activeUid = uid
                activeDocId = docId

                PdfReaderInfo(
                    docId = docId,
                    pageCount = count
                )
            } finally {
                newRenderer?.close()
                descriptor?.close()

                // The open file descriptor remains valid on Android.
                temporaryFile?.delete()
            }
        }
    }

    /**
     * Render a zero-based PDF page into a Bitmap.
     *
     * Bitmap is owned by the caller and must be released when unused.
     */
    suspend fun renderPage(
        pageIndex: Int
    ): Bitmap = mutex.withLock {
        val currentRenderer = renderer
            ?: throw IOException("No PDF is open")

        if (
            activeUid == null ||
            auth.currentUser?.uid != activeUid
        ) {
            closeInternal()
            throw IOException("PDF session is no longer valid")
        }

        if (pageIndex !in 0 until currentRenderer.pageCount) {
            throw IndexOutOfBoundsException("Invalid PDF page index")
        }

        withContext(Dispatchers.Default) {
            currentRenderer.openPage(pageIndex).use { page ->
                val originalWidth = page.width
                val originalHeight = page.height

                if (originalWidth <= 0 || originalHeight <= 0) {
                    throw IOException("Invalid PDF page dimensions")
                }

                // Limit bitmap memory for mobile devices.
                val maxDimension = 2048

                val scale = minOf(
                    2.0,
                    maxDimension.toDouble() /
                        maxOf(originalWidth, originalHeight)
                )

                val width = (originalWidth * scale)
                    .toInt()
                    .coerceAtLeast(1)

                val height = (originalHeight * scale)
                    .toInt()
                    .coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                try {
                    bitmap.eraseColor(Color.WHITE)

                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    bitmap
                } catch (error: Exception) {
                    bitmap.recycle()
                    throw error
                }
            }
        }
    }

    /**
     * Call when leaving the Reader or changing accounts.
     */
    suspend fun close() = mutex.withLock {
        closeInternal()
    }

    private fun closeInternal() {
        renderer?.close()
        renderer = null
        activeUid = null
        activeDocId = null
    }
}