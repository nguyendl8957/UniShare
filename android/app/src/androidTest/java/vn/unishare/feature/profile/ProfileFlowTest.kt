package vn.unishare.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.profile.ui.FakeProfileEnvironment
import vn.unishare.feature.profile.ui.ProfileScreen
import vn.unishare.feature.profile.ui.ProfileViewModel
import vn.unishare.feature.profile.ui.SellerPublicProfileScreen

class ProfileFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun buyerToSellerSwitch_updatesBottomBarTabs_andOpensProtectedManagementWithPermission() {
        val env = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_SELLER_READY)
        )
        val viewModel = ProfileViewModel(environment = env)
        var openedSellerManagement = false
        var openedSellerUpload = false

        composeRule.setContent {
            ProfileScreen(
                viewModel = viewModel,
                onOpenSellerManagement = { openedSellerManagement = true },
                onOpenSellerUpload = { openedSellerUpload = true }
            )
        }

        composeRule.onNodeWithTag("profile_buyer_view").assertIsDisplayed()
        composeRule.onNodeWithTag("txt_profile_display_name")
            .assertTextContains("Đặng Lâm Nguyên")
        composeRule.onNodeWithText("Đã mua").assertIsDisplayed()

        // Chuyển sang chế độ Người bán
        composeRule.onNodeWithTag("btn_switch_to_seller").performClick()

        composeRule.onNodeWithTag("profile_seller_dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("txt_seller_greeting", useUnmergedTree = true)
            .assertTextContains("Xin chào, Đặng Lâm Nguyên!")
        composeRule.onNodeWithText("Quản lý").assertIsDisplayed()

        // Mở màn hình quản lý và đăng tài liệu được bảo vệ bởi quyền Seller
        composeRule.onNodeWithTag("btn_seller_new_upload").performClick()
        assertTrue(openedSellerUpload)

        composeRule.onNodeWithTag("btn_seller_manage_all").performClick()
        assertTrue(openedSellerManagement)

        // Quay lại chế độ Người mua mà không làm mất phiên đăng nhập
        composeRule.onNodeWithTag("btn_switch_to_buyer").performClick()
        composeRule.onNodeWithTag("profile_buyer_view").assertIsDisplayed()
        assertEquals(
            AuthSession.SignedIn(FakeProfileEnvironment.UID_SELLER_READY),
            env.session.value
        )
    }

    @Test
    fun uidSwitchAndLogout_neverFlashesPreviousUserProfile_andShowsGuestState() {
        val env = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_SELLER_READY)
        )
        val viewModel = ProfileViewModel(environment = env)

        composeRule.setContent {
            ProfileScreen(viewModel = viewModel)
        }

        composeRule.onNodeWithTag("txt_profile_display_name")
            .assertTextContains("Đặng Lâm Nguyên")

        // Chuyển sang UID 2: hiển thị ngay hồ sơ của UID 2, không còn dữ liệu của UID 1
        composeRule.runOnIdle {
            env.setActiveSession(AuthSession.SignedIn(FakeProfileEnvironment.UID_BUYER_ONLY))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("txt_profile_display_name")
            .assertTextContains("Trần Minh Đức")
        composeRule.onNodeWithTag("txt_profile_school_faculty")
            .assertTextContains("ĐH Bách Khoa TP.HCM / Khoa Khoa học máy tính")

        // Đăng xuất: xóa ngay hồ sơ cá nhân và chuyển sang giao diện Khách
        composeRule.onNodeWithTag("btn_profile_logout")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("Hủy").performClick()
        composeRule.onNodeWithTag("profile_buyer_view").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_profile_logout").performClick()
        composeRule.onNodeWithTag("btn_confirm_logout").performClick()
        composeRule.onNodeWithTag("profile_guest_view").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_profile_guest_login").assertIsDisplayed()
    }

    @Test
    fun deniedSellerAccount_blocksSellerSwitch_showsEnrollmentNotice_andOfflineSavePreservesForm() {
        val env = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_BUYER_ONLY),
            denySellerEnrollment = true
        )
        val viewModel = ProfileViewModel(environment = env)

        composeRule.setContent {
            ProfileScreen(viewModel = viewModel)
        }

        // Bấm chuyển sang Seller khi chưa có quyền -> bị chặn và hiển thị thông báo đăng ký
        composeRule.onNodeWithTag("btn_switch_to_seller").performClick()
        composeRule.onNodeWithTag("banner_seller_denied").assertIsDisplayed()

        // Máy chủ chỉ cấp quyền khi đã xác nhận điều khoản.
        composeRule.runOnIdle {
            env.denySellerEnrollment = false
        }
        composeRule.onNodeWithTag("btn_enroll_seller").performClick()
        composeRule.onNodeWithText("Đăng ký Người bán", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Tôi đồng ý").performClick()
        composeRule.onNodeWithTag("profile_seller_dashboard").assertIsDisplayed()

        // Quay lại Buyer để kiểm tra chỉnh sửa hồ sơ khi mất mạng
        composeRule.onNodeWithTag("btn_switch_to_buyer").performClick()
        composeRule.onNodeWithTag("btn_edit_profile").performClick()
        composeRule.onNodeWithTag("profile_setup_form").assertIsDisplayed()

        composeRule.runOnIdle {
            env.simulateOffline = true
        }
        composeRule.onNodeWithTag("input_profile_name").performTextClearance()
        composeRule.onNodeWithTag("input_profile_name").performTextInput("Trần Minh Đức CNTT")
        composeRule.onNodeWithTag("btn_profile_save").performScrollTo().performClick()

        // Lỗi mất mạng vẫn giữ nguyên dữ liệu đã nhập và cho phép Thử lại
        composeRule.onNodeWithTag("banner_profile_save_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("input_profile_name")
            .performScrollTo()
            .assertTextContains("Trần Minh Đức CNTT")

        composeRule.runOnIdle {
            env.simulateOffline = false
        }
        composeRule.onNodeWithTag("btn_retry_save_profile").performScrollTo().performClick()

        composeRule.onNodeWithTag("profile_buyer_view").assertIsDisplayed()
        composeRule.onNodeWithTag("txt_profile_display_name")
            .assertTextContains("Trần Minh Đức CNTT")
    }

    @Test
    fun sellerPublicProfile_rendersPublicFieldsAndListings_andReturnsToListing() {
        val env = FakeProfileEnvironment(initialSession = AuthSession.Guest)
        val viewModel = ProfileViewModel(environment = env)
        var returnedToListing = false
        var selectedDocId: String? = null

        composeRule.setContent {
            SellerPublicProfileScreen(
                sellerId = "seller_hung_01",
                viewModel = viewModel,
                onBackToListingClick = { returnedToListing = true },
                onDocumentClick = { docId -> selectedDocId = docId }
            )
        }

        composeRule.onNodeWithTag("seller_public_profile_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("txt_public_seller_name")
            .assertTextContains("Nguyễn Tuấn Hùng")
        composeRule.onNodeWithTag("txt_public_seller_school")
            .assertTextContains("ĐH Bách Khoa TP.HCM • Khoa Khoa học máy tính")
        composeRule.onNodeWithTag("stat_public_seller_rating").assertIsDisplayed()

        composeRule.onNodeWithTag("public_listing_doc_cpp_01").performClick()
        assertEquals("doc_cpp_01", selectedDocId)

        composeRule.onNodeWithTag("btn_back_to_listing").performClick()
        assertTrue(returnedToListing)
    }
}
