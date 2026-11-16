package vn.unishare.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Enum biểu diễn chế độ giao diện Buyer / Seller (áp dụng OOP Enum)
enum class UniAppMode {
    BUYER,
    SELLER;

    companion object {
        // Fallback an toàn về BUYER khi chuỗi mode chưa xác định
        fun fromString(value: String?): UniAppMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BUYER
        }
    }
}

// Màu đo từ Figma Page 1 (Buyer) & Page 3 (Sign up/in - node 82:86, 48:133)
private val BuyerColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),          // Xanh lá chủ đạo Buyer (#2E7D32)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1F8F2), // Nền chip/nhãn phụ (#F1F8F2)
    onPrimaryContainer = Color(0xFF116B31),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2937),     // Chữ tiêu đề chính (#1F2937)
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFF667085), // Chữ phụ/mô tả (#667085 / #738096)
    outline = Color(0xFFCAC4D0),          // Viền ô nhập (#CAC4D0)
    outlineVariant = Color(0xFFE2E6ED)    // Đường phân cách (#E2E6ED)
)

// Màu đo từ Figma Page 2 (Seller - node 98:1087)
private val SellerColorScheme = lightColorScheme(
    primary = Color(0xFF0863CE),          // Xanh dương chủ đạo Seller (#0863CE / #1966D2)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4F2FF), // Nền thẻ chỉ số Seller (#E4F2FF)
    onPrimaryContainer = Color(0xFF10244E),
    background = Color(0xFFF5F8FB),       // Nền tổng thể Seller (#F5F8FB)
    onBackground = Color(0xFF10244E),     // Chữ chính Seller (#10244E)
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10244E),
    onSurfaceVariant = Color(0xFF7789AE), // Chữ phụ Seller (#7789AE)
    outline = Color(0xFF6BA0E6),
    outlineVariant = Color(0xFFEAF0F5)    // Viền phân cách Seller (#EAF0F5)
)

@Composable
fun UniShareTheme(
    mode: UniAppMode = UniAppMode.BUYER,
    content: @Composable () -> Unit
) {
    val colorScheme = when (mode) {
        UniAppMode.BUYER -> BuyerColorScheme
        UniAppMode.SELLER -> SellerColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(), // Mặc định dùng họ font Roboto trên Android
        content = content
    )
}

