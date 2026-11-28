package vn.unishare.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import vn.unishare.domain.repository.ProfileRepository
import java.io.IOException

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val sellerRoleContract: SellerRoleContract,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val sellerDashboardRepository: SellerDashboardRepository? = null
) : ViewModel() {

    constructor(
        environment: FakeProfileEnvironment = FakeProfileEnvironment(),
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    ) : this(
        authRepository = environment,
        profileRepository = environment,
        sellerRoleContract = environment,
        mainDispatcher = mainDispatcher
    )

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var activeProfileJob: Job? = null
    private var activeProfileSaveJob: Job? = null
    private var activeSellerDashboardJob: Job? = null
    private var activePublicProfileJob: Job? = null
    private var profileLoadSequence: Long = 0L

    init {
        viewModelScope.launch(mainDispatcher) {
            authRepository.session.collect { session ->
                handleSessionChanged(session)
            }
        }
    }

    private fun handleSessionChanged(session: AuthSession) {
        when (session) {
            is AuthSession.Restoring -> {
                clearPrivateProfileState(isRestoring = true, isGuest = false)
            }

            is AuthSession.Guest -> {
                clearPrivateProfileState(isRestoring = false, isGuest = true)
            }

            is AuthSession.SignedIn -> {
                val previousUid = _uiState.value.activeUid
                if (previousUid != session.uid) {
                    // Làm sạch toàn bộ dữ liệu của UID cũ ngay lập tức để không bị nháy thông tin cũ
                    clearPrivateProfileState(
                        isRestoring = false,
                        isGuest = false,
                        newActiveUid = session.uid
                    )
                }
                loadCurrentUserProfile(session.uid)
            }
        }
    }

    private fun clearPrivateProfileState(
        isRestoring: Boolean,
        isGuest: Boolean,
        newActiveUid: String? = null
    ) {
        activeProfileJob?.cancel()
        activeSellerDashboardJob?.cancel()
        activeProfileSaveJob?.cancel()
        profileLoadSequence++
        val preservedPublicId = _uiState.value.selectedPublicSellerId
        val preservedPublicState = _uiState.value.publicSellerState
        _uiState.value = ProfileUiState(
            isRestoringSession = isRestoring,
            isGuest = isGuest,
            activeUid = newActiveUid,
            isLoadingProfile = newActiveUid != null,
            selectedPublicSellerId = preservedPublicId,
            publicSellerState = preservedPublicState
        )
    }

    fun reloadMyProfile() {
        val uid = _uiState.value.activeUid ?: return
        loadCurrentUserProfile(uid)
    }

    private fun loadCurrentUserProfile(uid: String) {
        val requestId = ++profileLoadSequence
        activeProfileJob?.cancel()

        _uiState.update {
            it.copy(
                isLoadingProfile = true,
                loadError = null
            )
        }

        activeProfileJob = viewModelScope.launch(mainDispatcher) {
            val profileResult = profileRepository.getMyProfile()
            val permissionResult = sellerRoleContract.getSellerPermission(uid)

            if (requestId != profileLoadSequence || _uiState.value.activeUid != uid) {
                return@launch
            }

            profileResult.fold(
                onSuccess = { profile ->
                    val name = profile?.displayName.orEmpty()
                    val school = profile?.school.orEmpty()
                    val faculty = profile?.faculty.orEmpty()
                    val avatar = profile?.avatarUrl
                    val needsSetup = name.isBlank() || school.isBlank() || faculty.isBlank()

                    val permission = permissionResult.getOrNull()
                    val sellerAllowed = permission?.sellerEnabled == true
                    val initialMode = if (sellerAllowed && permission?.lastMode == UniAppMode.SELLER) {
                        UniAppMode.SELLER
                    } else {
                        UniAppMode.BUYER
                    }

                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            loadError = null,
                            displayName = name,
                            school = school,
                            faculty = faculty,
                            avatarPath = avatar,
                            editDisplayName = name,
                            editSchool = school,
                            editFaculty = faculty,
                            editAvatarPath = avatar,
                            isEditingProfile = needsSetup,
                            sellerEnabled = sellerAllowed,
                            currentMode = initialMode
                        )
                    }
                    if (initialMode == UniAppMode.SELLER) {
                        loadSellerDashboard()
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            loadError = mapErrorMessage(error)
                        )
                    }
                }
            )
        }
    }

    // --- Chỉnh sửa & lưu hồ sơ cá nhân ---

    fun startEditingProfile() {
        if (_uiState.value.isGuest || _uiState.value.activeUid == null) return
        _uiState.update {
            it.copy(
                isEditingProfile = true,
                editDisplayName = it.displayName,
                editSchool = it.school,
                editFaculty = it.faculty,
                editAvatarPath = it.avatarPath,
                fieldErrors = ProfileFieldErrors(),
                saveStatus = ProfileSaveStatus.Idle
            )
        }
    }

    fun cancelEditingProfile() {
        val current = _uiState.value
        if (current.saveStatus is ProfileSaveStatus.Saving) return
        val hasSavedProfile = current.displayName.isNotBlank() &&
            current.school.isNotBlank() &&
            current.faculty.isNotBlank()
        if (!hasSavedProfile) return

        _uiState.update {
            it.copy(
                isEditingProfile = false,
                editDisplayName = it.displayName,
                editSchool = it.school,
                editFaculty = it.faculty,
                editAvatarPath = it.avatarPath,
                fieldErrors = ProfileFieldErrors(),
                saveStatus = ProfileSaveStatus.Idle
            )
        }
    }

    fun onEditDisplayNameChange(value: String) {
        _uiState.update {
            it.copy(
                editDisplayName = value,
                fieldErrors = it.fieldErrors.copy(displayNameError = null),
                saveStatus = ProfileSaveStatus.Idle
            )
        }
    }

    fun onEditSchoolChange(value: String) {
        _uiState.update {
            it.copy(
                editSchool = value,
                fieldErrors = it.fieldErrors.copy(schoolError = null),
                saveStatus = ProfileSaveStatus.Idle
            )
        }
    }

    fun onEditFacultyChange(value: String) {
        _uiState.update {
            it.copy(
                editFaculty = value,
                fieldErrors = it.fieldErrors.copy(facultyError = null),
                saveStatus = ProfileSaveStatus.Idle
            )
        }
    }

    fun onEditAvatarChange(avatarUri: String) {
        if (!_uiState.value.isEditingProfile || _uiState.value.activeUid == null) return
        _uiState.update {
            it.copy(
                editAvatarPath = avatarUri,
                saveStatus = ProfileSaveStatus.Idle
            )
        }
    }

    fun saveProfile() {
        val current = _uiState.value
        if (current.isGuest || current.activeUid == null) return
        if (current.saveStatus is ProfileSaveStatus.Saving) return

        val trimmedName = current.editDisplayName.trim()
        val trimmedSchool = current.editSchool.trim()
        val trimmedFaculty = current.editFaculty.trim()

        val nameError = when {
            trimmedName.isEmpty() -> "Vui lòng nhập họ tên của bạn."
            trimmedName.length < 2 -> "Họ tên phải có ít nhất 2 ký tự."
            trimmedName.length > 60 -> "Họ tên không vượt quá 60 ký tự."
            else -> null
        }
        val schoolError = when {
            trimmedSchool.isEmpty() -> "Vui lòng chọn trường học."
            trimmedSchool !in ProfileUiState.ALLOWED_SCHOOLS -> "Trường học không nằm trong danh sách hỗ trợ."
            else -> null
        }
        val facultyError = when {
            trimmedFaculty.isEmpty() -> "Vui lòng chọn khoa."
            trimmedFaculty !in ProfileUiState.ALLOWED_FACULTIES -> "Khoa không nằm trong danh sách hỗ trợ."
            else -> null
        }

        if (nameError != null || schoolError != null || facultyError != null) {
            _uiState.update {
                it.copy(
                    fieldErrors = ProfileFieldErrors(
                        displayNameError = nameError,
                        schoolError = schoolError,
                        facultyError = facultyError
                    )
                )
            }
            return
        }

        val targetUid = current.activeUid
        _uiState.update {
            it.copy(
                fieldErrors = ProfileFieldErrors(),
                saveStatus = ProfileSaveStatus.Saving
            )
        }

        activeProfileSaveJob = viewModelScope.launch(mainDispatcher) {
            val selectedImage = current.editAvatarPath?.takeIf { it.startsWith("content://") }
            val result: Result<String?> = if (selectedImage != null) {
                profileRepository.updateMyProfileWithAvatar(
                    trimmedName, trimmedSchool, trimmedFaculty, selectedImage, current.avatarPath
                )
            } else {
                profileRepository.updateMyProfile(trimmedName, trimmedSchool, trimmedFaculty)
                    .map { current.avatarPath }
            }
            if (_uiState.value.activeUid != targetUid) return@launch

            result.fold(
                onSuccess = { avatar ->
                    _uiState.update {
                        it.copy(
                            displayName = trimmedName,
                            school = trimmedSchool,
                            faculty = trimmedFaculty,
                            avatarPath = avatar,
                            editAvatarPath = avatar,
                            isEditingProfile = false,
                            saveStatus = ProfileSaveStatus.Saved()
                        )
                    }
                },
                onFailure = { error ->
                    // Giữ nguyên dữ liệu đã nhập trong editDisplayName/editSchool/editFaculty để thử lại
                    _uiState.update {
                        it.copy(
                            saveStatus = ProfileSaveStatus.Error(mapErrorMessage(error))
                        )
                    }
                }
            )
        }
    }

    // --- Chuyển chế độ Buyer <-> Seller & kiểm quyền từ server ---

    fun switchMode(targetMode: UniAppMode) {
        val current = _uiState.value
        val uid = current.activeUid
        if (current.isGuest || uid == null) return

        if (targetMode == UniAppMode.BUYER) {
            _uiState.update {
                it.copy(
                    currentMode = UniAppMode.BUYER,
                    enrollmentState = SellerEnrollmentState.Idle
                )
            }
            viewModelScope.launch(mainDispatcher) {
                sellerRoleContract.updateLastMode(uid, UniAppMode.BUYER)
            }
            return
        }

        // Chuyển sang SELLER: phải được server xác nhận sellerEnabled = true
        if (!current.sellerEnabled) {
            _uiState.update {
                it.copy(
                    currentMode = UniAppMode.BUYER,
                    enrollmentState = SellerEnrollmentState.Denied(
                        "Tài khoản chưa có quyền Người bán trên hệ thống. Vui lòng đăng ký kích hoạt quyền Người bán."
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                currentMode = UniAppMode.SELLER,
                enrollmentState = SellerEnrollmentState.Idle
            )
        }
        loadSellerDashboard()
        viewModelScope.launch(mainDispatcher) {
            sellerRoleContract.updateLastMode(uid, UniAppMode.SELLER)
        }
    }

    fun requestSellerEnrollment(confirmTerms: Boolean = true) {
        val current = _uiState.value
        val uid = current.activeUid
        if (current.isGuest || uid == null) return
        if (current.enrollmentState is SellerEnrollmentState.Enrolling) return

        _uiState.update { it.copy(enrollmentState = SellerEnrollmentState.Enrolling) }

        viewModelScope.launch(mainDispatcher) {
            val result = sellerRoleContract.requestSellerEnrollment(uid, confirmTerms)
            if (_uiState.value.activeUid != uid) return@launch

            result.fold(
                onSuccess = { snapshot ->
                    if (snapshot.sellerEnabled) {
                        _uiState.update {
                            it.copy(
                                sellerEnabled = true,
                                currentMode = UniAppMode.SELLER,
                                enrollmentState = SellerEnrollmentState.Idle
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                sellerEnabled = false,
                                currentMode = UniAppMode.BUYER,
                                enrollmentState = SellerEnrollmentState.Denied(
                                    "Máy chủ chưa cấp quyền Người bán cho tài khoản này."
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sellerEnabled = false,
                            currentMode = UniAppMode.BUYER,
                            enrollmentState = SellerEnrollmentState.Denied(mapErrorMessage(error))
                        )
                    }
                }
            )
        }
    }

    fun dismissEnrollmentNotice() {
        _uiState.update { it.copy(enrollmentState = SellerEnrollmentState.Idle) }
    }

    fun tryOpenProtectedSellerAction(onAllowed: () -> Unit): Boolean {
        val current = _uiState.value
        val allowed = !current.isGuest &&
            current.activeUid != null &&
            current.sellerEnabled &&
            current.currentMode == UniAppMode.SELLER

        return if (allowed) {
            onAllowed()
            true
        } else {
            _uiState.update {
                it.copy(
                    enrollmentState = SellerEnrollmentState.Denied(
                        "Thao tác này chỉ dành cho tài khoản đã được cấp quyền Người bán."
                    )
                )
            }
            false
        }
    }

    fun loadSellerDashboard() {
        val current = _uiState.value
        val uid = current.activeUid ?: return

        if (!current.sellerEnabled ||
            current.currentMode != UniAppMode.SELLER
        ) return

        if (activeSellerDashboardJob?.isActive == true) return

        _uiState.update {
            it.copy(
                sellerDashboardLoading = true,
                sellerDashboardError = null
            )
        }

        activeSellerDashboardJob = viewModelScope.launch(mainDispatcher) {
            val result = sellerDashboardRepository?.load()
                ?: Result.failure(
                    IllegalStateException("Seller dashboard API unavailable")
                )

            if (_uiState.value.activeUid != uid ||
                _uiState.value.currentMode != UniAppMode.SELLER
            ) return@launch

            result.fold(
                onSuccess = { dashboard ->
                    _uiState.update {
                        it.copy(
                            sellerDashboard = dashboard,
                            sellerDashboardLoading = false,
                            sellerDashboardLoaded = true,
                            sellerDashboardError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sellerDashboardLoading = false,
                            sellerDashboardLoaded = false,
                            sellerDashboardError = mapErrorMessage(error)
                        )
                    }
                }
            )
        }
    }
    fun signOut() {
        viewModelScope.launch(mainDispatcher) {
            authRepository.signOut()
        }
    }

    // --- Hồ sơ công khai của người bán (SellerPublicProfileScreen) ---

    fun loadSellerPublicProfile(sellerId: String) {
        activePublicProfileJob?.cancel()
        _uiState.update {
            it.copy(
                selectedPublicSellerId = sellerId,
                publicSellerState = SellerPublicProfileLoadState.Loading
            )
        }

        activePublicProfileJob = viewModelScope.launch(mainDispatcher) {
            sellerRoleContract.getSellerPublicProfile(sellerId).fold(
                onSuccess = { profile ->
                    val nextState = if (profile == null) {
                        SellerPublicProfileLoadState.Empty
                    } else {
                        SellerPublicProfileLoadState.Content(profile)
                    }
                    _uiState.update { it.copy(publicSellerState = nextState) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            publicSellerState = SellerPublicProfileLoadState.Error(mapErrorMessage(error))
                        )
                    }
                }
            )
        }
    }

    fun retrySellerPublicProfile() {
        val sellerId = _uiState.value.selectedPublicSellerId ?: return
        loadSellerPublicProfile(sellerId)
    }

    private fun mapErrorMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            error is IOException ||
                raw.contains("network", ignoreCase = true) ||
                raw.contains("mạng", ignoreCase = true) ->
                "Mất kết nối mạng. Vui lòng kiểm tra Internet và thử lại."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi. Vui lòng thử lại."
        }
    }
}
