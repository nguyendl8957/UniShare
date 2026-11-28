package vn.unishare.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unishare.R
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

/**
 * Đóng gói dữ liệu từng mục trên thanh điều hướng dưới (Data Class).
 * Sử dụng trực tiếp Vector Asset trong res/drawable theo đúng Figma, không tự vẽ icon bằng code.
 */
data class UniBottomBarItem(
    val route: String,
    val title: String,
    @DrawableRes val selectedIconRes: Int,
    @DrawableRes val unselectedIconRes: Int
)

// Danh sách tab Buyer từ Figma node 82:145 & 101:2593 (sử dụng Vector Asset từ res/drawable)
val buyerBottomBarItems = listOf(
    UniBottomBarItem(
        route = "home",
        title = "Trang chủ",
        selectedIconRes = R.drawable.nav_buyer_home_selected,
        unselectedIconRes = R.drawable.nav_buyer_home_unselected
    ),
    UniBottomBarItem(
        route = "explore",
        title = "Khám phá",
        selectedIconRes = R.drawable.nav_buyer_search_selected,
        unselectedIconRes = R.drawable.nav_buyer_search_unselected_home
    ),
    UniBottomBarItem(
        route = "purchased",
        title = "Đã mua",
        selectedIconRes = R.drawable.nav_buyer_purchases_selected,
        unselectedIconRes = R.drawable.nav_buyer_purchases_unselected
    ),
    UniBottomBarItem(
        route = "profile",
        title = "Cá nhân",
        selectedIconRes = R.drawable.nav_buyer_profile_selected_green,
        unselectedIconRes = R.drawable.nav_buyer_profile_unselected
    )
)

// Danh sách tab Seller từ Figma node 102:2611 (tab thứ 3 là "Quản lý")
val sellerBottomBarItems = listOf(
    UniBottomBarItem(
        route = "home",
        title = "Trang chủ",
        selectedIconRes = R.drawable.nav_seller_home,
        unselectedIconRes = R.drawable.nav_buyer_home_unselected
    ),
    UniBottomBarItem(
        route = "explore",
        title = "Khám phá",
        selectedIconRes = R.drawable.nav_buyer_search_selected,
        unselectedIconRes = R.drawable.nav_buyer_search_unselected_home
    ),
    UniBottomBarItem(
        route = "manage",
        title = "Quản lý",
        selectedIconRes = R.drawable.nav_seller_documents_selected,
        unselectedIconRes = R.drawable.nav_seller_documents
    ),
    UniBottomBarItem(
        route = "profile",
        title = "Cá nhân",
        selectedIconRes = R.drawable.nav_buyer_profile_selected,
        unselectedIconRes = R.drawable.nav_buyer_profile_unselected
    )
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
    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.semantics {
                contentDescription = "Thanh điều hướng chính (${mode.name})"
            }
    ) {
        NavigationBar(
            containerColor = Color.White,
            tonalElevation = 0.dp
        ) {
            items.forEach { item ->
                val isSelected = selectedRoute == item.route
                val iconRes = if (isSelected) item.selectedIconRes else item.unselectedIconRes
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = "Biểu tượng ${item.title}",
                            tint = if (isSelected && mode == UniAppMode.SELLER && item.route == "explore") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Unspecified
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selected = isSelected,
                    onClick = { onItemSelected(item.route) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Unspecified,
                        unselectedIconColor = Color.Unspecified,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = Color(0xFF70798A),
                        indicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Mục ${item.title}"
                    }
                )
            }
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
            selectedRoute = "profile",
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
            selectedRoute = "home",
            onItemSelected = {},
            mode = UniAppMode.SELLER
        )
    }
}


