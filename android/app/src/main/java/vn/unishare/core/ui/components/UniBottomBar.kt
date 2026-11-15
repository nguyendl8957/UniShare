package vn.unishare.core.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

// Đóng gói dữ liệu từng mục trên thanh điều hướng dưới (Data Class)
data class UniBottomBarItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

// Vector icon cơ bản dựng trực tiếp bằng Compose ImageVector để không phải sửa file build.gradle.kts của A01
private val HomeVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 20f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(-8f)
            horizontalLineToRelative(3f)
            lineTo(12f, 3f)
            lineTo(2f, 12f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(8f)
            close()
        }
    }.build()
}

private val SearchVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Search",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.5f, 14f)
            horizontalLineToRelative(-0.79f)
            lineToRelative(-0.28f, -0.27f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
            reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
            curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
            lineToRelative(0.27f, 0.28f)
            verticalLineToRelative(0.79f)
            lineToRelative(5f, 4.99f)
            lineTo(20.49f, 19f)
            lineToRelative(-4.99f, -5f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
            reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
            reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
            close()
        }
    }.build()
}

private val BagVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bag",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(18f, 6f)
            horizontalLineToRelative(-2f)
            curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f)
            reflectiveCurveTo(8f, 3.79f, 8f, 6f)
            horizontalLineTo(6f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(12f, 4f)
            curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
            horizontalLineToRelative(-4f)
            curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
            close()
        }
    }.build()
}

private val ManageDocVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "ManageDoc",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f)
            lineToRelative(-6f, -6f)
            close()
            moveTo(16f, 18f)
            horizontalLineTo(8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(2f)
            close()
            moveTo(16f, 14f)
            horizontalLineTo(8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(2f)
            close()
            moveTo(13f, 9f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9f)
            horizontalLineTo(13f)
            close()
        }
    }.build()
}

private val PersonVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Person",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 12f)
            curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
            reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
            reflectiveCurveToRelative(-4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            close()
            moveTo(12f, 14f)
            curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-2f)
            curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
            close()
        }
    }.build()
}

// Danh sách tab Buyer đo từ Figma node 82:145 & 101:2593
val buyerBottomBarItems = listOf(
    UniBottomBarItem("home", "Trang chủ", HomeVector),
    UniBottomBarItem("explore", "Khám phá", SearchVector),
    UniBottomBarItem("purchased", "Đã mua", BagVector),
    UniBottomBarItem("profile", "Cá nhân", PersonVector)
)

// Danh sách tab Seller đo từ Figma node 102:2611 (tab thứ 3 là "Quản lý")
val sellerBottomBarItems = listOf(
    UniBottomBarItem("home", "Trang chủ", HomeVector),
    UniBottomBarItem("explore", "Khám phá", SearchVector),
    UniBottomBarItem("manage", "Quản lý", ManageDocVector),
    UniBottomBarItem("profile", "Cá nhân", PersonVector)
)

val defaultBottomBarItems = buyerBottomBarItems

@Composable
fun UniBottomBar(
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    mode: UniAppMode = UniAppMode.BUYER,
    items: List<UniBottomBarItem> = if (mode == UniAppMode.SELLER) sellerBottomBarItems else buyerBottomBarItems
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.semantics {
            contentDescription = "Thanh điều hướng chính (${mode.name})"
        }
    ) {
        items.forEach { item ->
            val isSelected = selectedRoute == item.route
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = "Biểu tượng ${item.title}"
                    )
                },
                label = { Text(item.title) },
                selected = isSelected,
                onClick = { onItemSelected(item.route) }, // State Hoisting: trả route về NavHost
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color(0xFF70798A),
                    unselectedTextColor = Color(0xFF70798A)
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Mục ${item.title}"
                }
            )
        }
    }
}

@Preview(widthDp = 360, name = "Buyer - Small Screen (360dp)")
@Preview(widthDp = 412, name = "Buyer - Normal Screen (412dp)")
@Preview(fontScale = 1.5f, name = "Buyer - Large Font")
@Composable
private fun UniBottomBarPreviewBuyer() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        UniBottomBar(
            selectedRoute = "home",
            onItemSelected = {},
            mode = UniAppMode.BUYER
        )
    }
}

@Preview(widthDp = 412, name = "Seller Mode (412dp)")
@Composable
private fun UniBottomBarPreviewSeller() {
    UniShareTheme(mode = UniAppMode.SELLER) {
        UniBottomBar(
            selectedRoute = "manage",
            onItemSelected = {},
            mode = UniAppMode.SELLER
        )
    }
}

