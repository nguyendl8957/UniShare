package vn.unishare.core.ui.feedback

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vn.unishare.R
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

// Áp dụng Đa hình / Sealed Interface cho các trạng thái phản hồi giao diện
sealed interface UiFeedbackState {
    data object Skeleton : UiFeedbackState
    data object Loading : UiFeedbackState
    data class Empty(
        val message: String,
        val subtitle: String? = null,
        @param:DrawableRes val illustrationRes: Int = R.drawable.ic_search_empty_illustration
    ) : UiFeedbackState
    data class Error(
        val message: String = "Đã xảy ra lỗi mạng. Vui lòng thử lại!"
    ) : UiFeedbackState
    data object Splash : UiFeedbackState
}

@Composable
fun UiFeedback(
    state: UiFeedbackState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is UiFeedbackState.Skeleton -> {
                SkeletonListPlaceholder()
            }
            is UiFeedbackState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Đang tải dữ liệu" },
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is UiFeedbackState.Empty -> {
                EmptyScreen(state.message, state.subtitle, state.illustrationRes)
            }
            is UiFeedbackState.Error -> {
                ErrorScreen(message = state.message, onRetry = onRetry)
            }
            is UiFeedbackState.Splash -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics { contentDescription = "Màn hình chào mừng UniShare" }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.unishare_wordmark),
                        contentDescription = "Biểu trưng UniShare",
                        modifier = Modifier
                            .width(134.dp)
                            .height(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Chia sẻ tri thức - Cùng nhau tiến bộ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonListPlaceholder() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .semantics { contentDescription = "Đang tải khung giao diện" },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(count = 4, key = { index -> "skeleton_$index" }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

// Đối chiếu Figma node 48:133 ("2-Trạng thái trống") & 50:197 ("3-Không có kết quả")
@Composable
private fun EmptyScreen(
    message: String,
    subtitle: String?,
    @DrawableRes illustrationRes: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 30.dp, vertical = 24.dp)
            .semantics { contentDescription = "Màn hình rỗng: $message" }
    ) {
        Image(
            painter = painterResource(id = illustrationRes),
            contentDescription = null,
            modifier = Modifier
                .width(132.dp)
                .height(108.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(24.dp)
            .semantics { contentDescription = "Màn hình lỗi: $message" }
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        UniButton(
            text = "Thử lại",
            onClick = onRetry,
            modifier = Modifier.widthIn(min = 160.dp)
        )
    }
}

@Preview(widthDp = 360, name = "Error - Small Screen (360dp)")
@Preview(widthDp = 412, name = "Error - Normal Screen (412dp)")
@Preview(fontScale = 1.5f, name = "Error - Large Font")
@Composable
private fun PreviewUiFeedbackError() {
    UniShareTheme {
        Surface {
            UiFeedback(state = UiFeedbackState.Error())
        }
    }
}

@Preview(widthDp = 360, name = "Empty - Figma 48:133 (360dp)")
@Composable
private fun PreviewUiFeedbackEmpty() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        Surface {
            UiFeedback(
                state = UiFeedbackState.Empty(
                    message = "Hãy nhập từ khóa để tìm kiếm",
                    subtitle = "Bạn có thể tìm theo tên tài liệu, môn học, hoặc nội dung cần tìm."
                )
            )
        }
    }
}

@Preview(widthDp = 412, name = "Skeleton Lazy List (412dp)")
@Composable
private fun PreviewUiFeedbackSkeleton() {
    UniShareTheme(mode = UniAppMode.SELLER) {
        Surface {
            UiFeedback(state = UiFeedbackState.Skeleton)
        }
    }
}


