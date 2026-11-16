package vn.unishare.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState

class UiFeedbackTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testStateTransition_LoadingToContent_and_ErrorRetry() {
        var retryCount = 0
        var currentState by mutableStateOf<UiFeedbackState>(UiFeedbackState.Loading)

        composeTestRule.setContent {
            UiFeedback(
                state = currentState,
                onRetry = { 
                    retryCount++ 
                    // Mô phỏng chuyển trạng thái lập tức về Loading khi bấm Thử lại
                    currentState = UiFeedbackState.Loading
                }
            )
        }

        // 1. Kiểm tra trạng thái Loading và semantics
        composeTestRule.onNodeWithContentDescription("Đang tải dữ liệu").assertExists()
        composeTestRule.onNodeWithText("Thử lại").assertDoesNotExist()

        // 2. Chuyển trạng thái sang Error
        currentState = UiFeedbackState.Error("Mất kết nối mạng")
        composeTestRule.waitForIdle()

        // Kiểm tra nút "Thử lại" xuất hiện, có thể click và có semantics đúng
        composeTestRule.onNodeWithContentDescription("Màn hình lỗi: Mất kết nối mạng").assertExists()
        val retryButton = composeTestRule.onNodeWithText("Thử lại")
        retryButton.assertExists().assertHasClickAction()
        
        // 3. Bấm nút Thử lại
        retryButton.performClick()
        
        // 4. Xác minh event gọi đúng 1 lần
        assert(retryCount == 1)
        
        composeTestRule.waitForIdle()
        // Nút thử lại phải biến mất vì state đã được đổi về Loading ở hàm mô phỏng
        composeTestRule.onNodeWithText("Thử lại").assertDoesNotExist()

        // 5. Chuyển sang trạng thái Empty
        currentState = UiFeedbackState.Empty("Không tìm thấy kết quả")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Màn hình rỗng: Không tìm thấy kết quả").assertExists()
    }
}
