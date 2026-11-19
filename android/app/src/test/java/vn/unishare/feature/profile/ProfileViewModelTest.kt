package vn.unishare.feature.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.profile.ui.FakeProfileEnvironment
import vn.unishare.feature.profile.ui.ProfileSaveStatus
import vn.unishare.feature.profile.ui.ProfileViewModel
import vn.unishare.feature.profile.ui.SellerEnrollmentState
import vn.unishare.feature.profile.ui.SellerPublicProfileLoadState

class ProfileViewModelTest {

    @Test
    fun saveProfile_validAllowlistFields_updatesOnlyCurrentUidProfile() = runBlocking {
        val env = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_SELLER_READY)
        )
        val viewModel = ProfileViewModel(
            environment = env,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.startEditingProfile()
        viewModel.onEditDisplayNameChange("  Đặng Lâm Nguyên Mới  ")
        viewModel.onEditSchoolChange("ĐH Bách Khoa TP.HCM")
        viewModel.onEditFacultyChange("Khoa Khoa học máy tính")
        viewModel.saveProfile()

        val state = viewModel.uiState.value
        assertFalse(state.isEditingProfile)
        assertTrue(state.saveStatus is ProfileSaveStatus.Saved)
        assertEquals("Đặng Lâm Nguyên Mới", state.displayName)
        assertEquals("ĐH Bách Khoa TP.HCM", state.school)
        assertEquals("Khoa Khoa học máy tính", state.faculty)

        // Kiểm tra dữ liệu của UID thứ hai không bị ảnh hưởng
        val secondUidProfile = env.getStoredProfileForTest(FakeProfileEnvironment.UID_BUYER_ONLY)
        assertNotNull(secondUidProfile)
        assertEquals("Trần Minh Đức", secondUidProfile?.displayName)
    }

    @Test
    fun saveProfile_invalidOrOffline_preservesTypedInputAndSupportsRetry() = runBlocking {
        val env = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_SELLER_READY)
        )
        val viewModel = ProfileViewModel(
            environment = env,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.startEditingProfile()
        viewModel.onEditDisplayNameChange("A")
        viewModel.onEditSchoolChange("Trường ngoài danh sách")
        viewModel.onEditFacultyChange("Khoa ngoài danh sách")
        viewModel.saveProfile()

        val invalidState = viewModel.uiState.value
        assertNotNull(invalidState.fieldErrors.displayNameError)
        assertNotNull(invalidState.fieldErrors.schoolError)
        assertNotNull(invalidState.fieldErrors.facultyError)

        // Nhập hợp lệ nhưng mất mạng: giữ nguyên dữ liệu đã gõ để thử lại
        env.simulateOffline = true
        viewModel.onEditDisplayNameChange("Nguyễn Văn An")
        viewModel.onEditSchoolChange("ĐH Công nghệ Thông tin")
        viewModel.onEditFacultyChange("Khoa CNTT")
        viewModel.saveProfile()

        val offlineState = viewModel.uiState.value
        assertTrue(offlineState.isEditingProfile)
        assertTrue(offlineState.saveStatus is ProfileSaveStatus.Error)
        assertEquals("Nguyễn Văn An", offlineState.editDisplayName)
        assertEquals("ĐH Công nghệ Thông tin", offlineState.editSchool)
        assertEquals("Khoa CNTT", offlineState.editFaculty)

        // Có mạng trở lại và thử lại thành công
        env.simulateOffline = false
        viewModel.saveProfile()

        val recoveredState = viewModel.uiState.value
        assertFalse(recoveredState.isEditingProfile)
        assertTrue(recoveredState.saveStatus is ProfileSaveStatus.Saved)
        assertEquals("Nguyễn Văn An", recoveredState.displayName)
    }

    @Test
    fun switchMode_preservesSessionAndUid_andDeniedSellerBlocksProtectedActions() = runBlocking {
        val sellerEnv = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_SELLER_READY)
        )
        val sellerVm = ProfileViewModel(
            environment = sellerEnv,
            mainDispatcher = Dispatchers.Unconfined
        )

        val uidBeforeSwitch = sellerVm.uiState.value.activeUid
        assertEquals(UniAppMode.BUYER, sellerVm.uiState.value.currentMode)

        sellerVm.switchMode(UniAppMode.SELLER)
        assertEquals(UniAppMode.SELLER, sellerVm.uiState.value.currentMode)
        assertEquals(uidBeforeSwitch, sellerVm.uiState.value.activeUid)
        assertEquals(AuthSession.SignedIn(FakeProfileEnvironment.UID_SELLER_READY), sellerEnv.session.value)

        var openedProtectedAction = false
        assertTrue(sellerVm.tryOpenProtectedSellerAction { openedProtectedAction = true })
        assertTrue(openedProtectedAction)

        sellerVm.switchMode(UniAppMode.BUYER)
        assertEquals(UniAppMode.BUYER, sellerVm.uiState.value.currentMode)
        assertEquals(uidBeforeSwitch, sellerVm.uiState.value.activeUid)

        // Tài khoản chưa được cấp quyền Seller sẽ bị chặn
        val buyerOnlyEnv = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_BUYER_ONLY)
        )
        val buyerOnlyVm = ProfileViewModel(
            environment = buyerOnlyEnv,
            mainDispatcher = Dispatchers.Unconfined
        )

        buyerOnlyVm.switchMode(UniAppMode.SELLER)
        assertEquals(UniAppMode.BUYER, buyerOnlyVm.uiState.value.currentMode)
        assertTrue(buyerOnlyVm.uiState.value.enrollmentState is SellerEnrollmentState.Denied)

        var unauthorizedOpened = false
        assertFalse(buyerOnlyVm.tryOpenProtectedSellerAction { unauthorizedOpened = true })
        assertFalse(unauthorizedOpened)
    }

    @Test
    fun sellerEnrollment_whenApprovedEnablesSellerMode_whenDeniedKeepsBuyerMode() = runBlocking {
        val env = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_BUYER_ONLY),
            denySellerEnrollment = true
        )
        val viewModel = ProfileViewModel(
            environment = env,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.requestSellerEnrollment(confirmTerms = true)
        assertFalse(viewModel.uiState.value.sellerEnabled)
        assertEquals(UniAppMode.BUYER, viewModel.uiState.value.currentMode)
        assertTrue(viewModel.uiState.value.enrollmentState is SellerEnrollmentState.Denied)

        // Máy chủ chấp thuận đăng ký quyền Người bán
        env.denySellerEnrollment = false
        viewModel.requestSellerEnrollment(confirmTerms = true)
        assertTrue(viewModel.uiState.value.sellerEnabled)
        assertEquals(UniAppMode.SELLER, viewModel.uiState.value.currentMode)
        assertTrue(viewModel.uiState.value.enrollmentState is SellerEnrollmentState.Idle)
    }

    @Test
    fun logoutAndUidSwitch_clearsPreviousProfileStateImmediately() = runBlocking {
        val env = FakeProfileEnvironment(
            initialSession = AuthSession.SignedIn(FakeProfileEnvironment.UID_SELLER_READY)
        )
        val viewModel = ProfileViewModel(
            environment = env,
            mainDispatcher = Dispatchers.Unconfined
        )

        assertEquals("Đặng Lâm Nguyên", viewModel.uiState.value.displayName)
        viewModel.switchMode(UniAppMode.SELLER)
        assertEquals(UniAppMode.SELLER, viewModel.uiState.value.currentMode)

        // Kiểm tra phân tách quyền giữa 2 UID
        val crossReadResult = env.getSellerPermission(FakeProfileEnvironment.UID_BUYER_ONLY)
        assertTrue(crossReadResult.isFailure)

        // Chuyển sang UID 2 khi mất mạng để kiểm tra state cũ của UID 1 đã bị xóa sạch ngay lập tức
        env.simulateOffline = true
        env.setActiveSession(AuthSession.SignedIn(FakeProfileEnvironment.UID_BUYER_ONLY))

        val switchedState = viewModel.uiState.value
        assertEquals(FakeProfileEnvironment.UID_BUYER_ONLY, switchedState.activeUid)
        assertEquals("", switchedState.displayName)
        assertEquals("", switchedState.school)
        assertEquals(UniAppMode.BUYER, switchedState.currentMode)
        assertFalse(switchedState.sellerEnabled)

        // Đăng xuất: xóa sạch toàn bộ state cá nhân và chuyển về Guest
        viewModel.signOut()
        val guestState = viewModel.uiState.value
        assertTrue(guestState.isGuest)
        assertNull(guestState.activeUid)
        assertEquals("", guestState.displayName)
        assertEquals(UniAppMode.BUYER, guestState.currentMode)
    }

    @Test
    fun publicSellerProfile_loadsPublicProjectionOnly_andHandlesEmptyOrOfflineRetry() = runBlocking {
        val env = FakeProfileEnvironment(
            initialSession = AuthSession.Guest,
            simulateOffline = true
        )
        val viewModel = ProfileViewModel(
            environment = env,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.loadSellerPublicProfile("seller_hung_01")
        assertTrue(viewModel.uiState.value.publicSellerState is SellerPublicProfileLoadState.Error)

        env.simulateOffline = false
        viewModel.retrySellerPublicProfile()

        val loaded = viewModel.uiState.value.publicSellerState
        assertTrue(loaded is SellerPublicProfileLoadState.Content)
        val profile = (loaded as SellerPublicProfileLoadState.Content).profile
        assertEquals("Nguyễn Tuấn Hùng", profile.displayName)
        assertEquals("ĐH Bách Khoa TP.HCM", profile.school)
        assertTrue(profile.listings.isNotEmpty())

        // Seller không tồn tại trả về trạng thái Empty
        viewModel.loadSellerPublicProfile("seller_not_found")
        assertTrue(viewModel.uiState.value.publicSellerState is SellerPublicProfileLoadState.Empty)
    }
}
