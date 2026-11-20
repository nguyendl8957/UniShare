package vn.unishare.feature.library.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.unishare.data.remote.PdfApiException
import vn.unishare.feature.library.reader.PdfReaderController

@Composable
fun RealPdfReaderScreen(
    docId: String,
    title: String,
    auth: FirebaseAuth,
    controller: PdfReaderController,
    onBackClick: () -> Unit,
    onAskAiClick: (String) -> Unit = {},
    onRequireLoginClick: () -> Unit = {},
    onBottomNavSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var uid by remember(auth) {
        mutableStateOf(auth.currentUser?.uid)
    }

    var readerState by remember(docId, uid) {
        mutableStateOf<PdfReaderLoadState>(
            PdfReaderLoadState.Loading
        )
    }

    var currentPage by remember(docId, uid) {
        mutableIntStateOf(1)
    }

    var bitmap by remember(docId, uid) {
        mutableStateOf<Bitmap?>(null)
    }

    var reload by remember(docId, uid) {
        mutableIntStateOf(0)
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // React to account switches and logout immediately.
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            uid = it.currentUser?.uid
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(docId, uid, reload, controller) {
        bitmap = null

        if (uid == null || auth.currentUser?.uid != uid) {
            readerState = PdfReaderLoadState.Unauthorized(
                "Vui lòng đăng nhập để đọc tài liệu."
            )
            controller.close()
            return@LaunchedEffect
        }

        readerState = PdfReaderLoadState.Loading

        try {
            val info = controller.openDocument(docId)

            if (auth.currentUser?.uid != uid) {
                throw SecurityException("Account changed")
            }

            currentPage = 1

            snapshotFlow { currentPage }
                .collectLatest { page ->
                    readerState = PdfReaderLoadState.Loading
                    bitmap = null

                    val rendered = controller.renderPage(page - 1)

                    if (auth.currentUser?.uid != uid) {
                        rendered.recycle()
                        throw SecurityException("Account changed")
                    }

                    bitmap = rendered

                    readerState = PdfReaderLoadState.Ready(
                        docId = docId,
                        title = title,
                        versionNumber = 1,
                        totalPages = info.pageCount,
                        currentPage = page,
                        pages = emptyList()
                    )
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            bitmap = null

            readerState = when (error) {
                is PdfApiException -> when (error.httpStatus) {
                    401 -> PdfReaderLoadState.Unauthorized(
                        "Phiên đăng nhập không hợp lệ."
                    )
                    403 -> PdfReaderLoadState.Denied(
                        "Bạn không có quyền đọc tài liệu này hoặc quyền đã bị thu hồi."
                    )
                    else -> PdfReaderLoadState.Error(
                        error.message ?: "Không tải được PDF."
                    )
                }
                is SecurityException ->
                    PdfReaderLoadState.Unauthorized(
                        "Phiên đăng nhập đã thay đổi."
                    )
                else -> PdfReaderLoadState.Error(
                    error.message ?: "Không thể mở PDF."
                )
            }
        } finally {
            bitmap = null

            // Close native PdfRenderer even when navigation cancels the task.
            withContext(NonCancellable) {
                controller.close()
            }
        }
    }

    // Release previous page bitmaps when no longer displayed.
    DisposableEffect(bitmap) {
        val currentBitmap = bitmap
        onDispose {
            currentBitmap?.recycle()
        }
    }

    val ready = readerState as? PdfReaderLoadState.Ready

    val handleBack: () -> Unit = onBackClick

    BackHandler(onBack = handleBack)

    PdfReaderContent(
        readerState = readerState,
        renderedPage = bitmap?.asImageBitmap(),
        snackbarHostState = snackbar,
        onBackClick = handleBack,
        onRetryClick = { reload++ },
        onPreviousPage = {
            if (currentPage > 1) {
                currentPage--
            }
        },
        onNextPage = {
            if (ready != null && currentPage < ready.totalPages) {
                currentPage++
            }
        },
        onJumpToFirstPage = {
            currentPage = 1
        },
        onJumpToLastPage = {
            if (ready != null) {
                currentPage = ready.totalPages
            }
        },
        onShareClick = {
            scope.launch {
                snackbar.showSnackbar(
                    "UniShare không hỗ trợ xuất PDF gốc."
                )
            }
        },
        onAskAiClick = {
            if (ready != null && auth.currentUser?.uid == uid) {
                onAskAiClick(docId)
            }
        },
        onRequireLoginClick = onRequireLoginClick,
        onBottomNavSelected = onBottomNavSelected,
        modifier = modifier
    )
}