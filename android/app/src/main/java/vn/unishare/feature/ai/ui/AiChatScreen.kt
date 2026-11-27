package vn.unishare.feature.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unishare.R
import vn.unishare.core.ui.components.UniBottomBar
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

private val AiScreenBg = Color(0xFFF7F9FB)
private val AiPrimaryGreen = Color(0xFF2E7D32)
private val AiSoftGreenBg = Color(0xFFE7F3E9)
private val AiNavyText = Color(0xFF253047)
private val AiBodyText = Color(0xFF334155)
private val AiMutedText = Color(0xFF70798A)
private val AiBorderColor = Color(0xFFE2E6ED)

// Màn hình Trợ lý AI hỏi đáp theo tài liệu đã mua
@Composable
fun AiChatScreen(
    docId: String,
    documentTitle: String,
    viewModel: AiChatViewModel,
    onBackClick: () -> Unit = {},
    onCitationClick: (docId: String, page: Int) -> Unit = { _, _ -> },
    onLoginClick: () -> Unit = {},
    onBottomNavSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(docId, documentTitle) {
        viewModel.bindDocument(docId = docId, documentTitle = documentTitle)
    }

    BackHandler(onBack = onBackClick)

    UniShareTheme(mode = UniAppMode.BUYER) {
        AiChatScreenContent(
            uiState = uiState,
            onBackClick = onBackClick,
            onInputChanged = viewModel::onInputChanged,
            onSendClick = viewModel::sendQuestion,
            onSuggestionClick = viewModel::sendSuggestion,
            onRetryClick = viewModel::retryLastFailedMessage,
            onCitationClick = { page ->
                viewModel.onCitationClicked(page)
                onCitationClick(docId, page)
            },
            onLoginClick = onLoginClick,
            onBottomNavSelected = onBottomNavSelected,
            modifier = modifier
        )
    }
}

@Composable
fun AiChatScreenContent(
    uiState: AiChatUiState,
    onBackClick: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onSuggestionClick: (AiQuickSuggestion) -> Unit,
    onRetryClick: () -> Unit,
    onCitationClick: (Int) -> Unit,
    onLoginClick: () -> Unit,
    onBottomNavSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_chat_screen"),
        containerColor = AiScreenBg,
        topBar = {
            AiChatTopBar(
                documentTitle = uiState.documentTitle.ifBlank { "Tài liệu học tập" },
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                if (uiState.canCompose) {
                    AiComposerArea(
                        uiState = uiState,
                        onInputChanged = onInputChanged,
                        onSendClick = onSendClick,
                        onSuggestionClick = onSuggestionClick
                    )
                }
                UniBottomBar(
                    selectedRoute = "purchased",
                    onItemSelected = onBottomNavSelected,
                    mode = UniAppMode.BUYER,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isRestoringSession -> {
                    UiFeedback(state = UiFeedbackState.Loading)
                }

                uiState.isGuest -> {
                    AiGuestNotice(onLoginClick = onLoginClick)
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AiStatusErrorBanner(
                            errorState = uiState.errorState,
                            onRetryClick = onRetryClick,
                            onLoginClick = onLoginClick,
                            onBackClick = onBackClick
                        )

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("ai_chat_timeline")
                        ) {
                            item(key = "ai_welcome_card") {
                                AiWelcomeBanner(
                                    documentTitle = uiState.documentTitle.ifBlank { uiState.docId },
                                    isLocked = uiState.isAccessLocked
                                )
                            }

                            items(
                                items = uiState.messages,
                                key = { it.id }
                            ) { message ->
                                when (message.role) {
                                    AiChatRole.USER -> UserMessageBubble(
                                        message = message,
                                        onRetryClick = onRetryClick
                                    )

                                    AiChatRole.ASSISTANT -> AssistantMessageBubble(
                                        message = message,
                                        onCitationClick = onCitationClick
                                    )
                                }
                            }

                            if (uiState.isSending) {
                                item(key = "ai_sending_row") {
                                    AiSendingBubble()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatTopBar(
    documentTitle: String,
    onBackClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("btn_ai_back")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_left),
                            contentDescription = "Quay lại trình đọc",
                            tint = AiNavyText,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.unishare_wordmark),
                        contentDescription = "Biểu trưng UniShare",
                        modifier = Modifier
                            .width(114.dp)
                            .height(38.dp)
                            .testTag("img_ai_header_logo")
                    )
                }

                Surface(
                    color = AiSoftGreenBg,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bot),
                            contentDescription = null,
                            tint = AiPrimaryGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Trợ lý AI",
                            color = AiPrimaryGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = AiBorderColor)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAFCFB))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_file_text),
                    contentDescription = null,
                    tint = AiPrimaryGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = documentTitle,
                    color = AiNavyText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AiWelcomeBanner(
    documentTitle: String,
    isLocked: Boolean
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AiBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_file_text),
                contentDescription = null,
                tint = AiPrimaryGreen,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isLocked) {
                        "Không thể truy cập trợ lý AI cho tài liệu này"
                    } else {
                        "Hỏi đáp bám sát nội dung: $documentTitle"
                    },
                    color = AiNavyText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isLocked) {
                        "Chỉ tài khoản đã mua tài liệu mới được sử dụng trợ lý AI."
                    } else {
                        "Chọn gợi ý nhanh bên dưới hoặc nhập câu hỏi của bạn. Các trích dẫn sẽ dẫn về số trang tương ứng."
                    },
                    color = AiMutedText,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun AiStatusErrorBanner(
    errorState: AiChatErrorState?,
    onRetryClick: () -> Unit,
    onLoginClick: () -> Unit,
    onBackClick: () -> Unit
) {
    when (errorState) {
        null -> Unit

        is AiChatErrorState.Forbidden -> {
            Surface(
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("ai_denied_banner")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lock_keyhole),
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorState.message,
                            color = Color(0xFF991B1B),
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = onBackClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_ai_denied_back")
                    ) {
                        Text("Quay lại", fontSize = 12.sp, color = AiNavyText)
                    }
                }
            }
        }

        is AiChatErrorState.Unauthorized -> {
            Surface(
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("ai_unauth_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = errorState.message,
                        color = Color(0xFF991B1B),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onLoginClick) {
                        Text("Đăng nhập", color = AiPrimaryGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        is AiChatErrorState.QuotaExceeded -> {
            Surface(
                color = Color(0xFFFFF7ED),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("ai_quota_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_warning),
                        contentDescription = null,
                        tint = Color(0xFFEA580C),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = errorState.message,
                        color = Color(0xFF9A3412),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        is AiChatErrorState.Transient -> {
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("ai_retry_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_warning),
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = errorState.message,
                        color = Color(0xFF991B1B),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRetryClick,
                        modifier = Modifier.testTag("btn_ai_retry_last")
                    ) {
                        Text(
                            text = "Thử lại",
                            color = AiPrimaryGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: AiChatMessage,
    onRetryClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = AiPrimaryGreen,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = 14.dp,
                bottomEnd = 4.dp
            ),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        if (message.deliveryState == AiMessageDeliveryState.FAILED) {
            Text(
                text = "Gửi thất bại • Bấm thử lại",
                color = Color(0xFFDC2626),
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = onRetryClick)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantMessageBubble(
    message: AiChatMessage,
    onCitationClick: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AiSoftGreenBg)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bot),
                contentDescription = null,
                tint = AiPrimaryGreen,
                modifier = Modifier.size(16.dp)
            )
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 14.dp,
                bottomStart = 14.dp,
                bottomEnd = 14.dp
            ),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Color(0x14253752),
                    spotColor = Color(0x14253752)
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text(
                    text = message.text,
                    color = AiBodyText,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )

                if (message.citations.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.citations.forEach { citation ->
                            Surface(
                                color = AiSoftGreenBg,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onCitationClick(citation.page) }
                                    .testTag("citation_${message.id}_${citation.page}")
                            ) {
                                Text(
                                    text = citation.label,
                                    color = AiPrimaryGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSendingBubble() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_sending_indicator")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AiSoftGreenBg)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bot),
                contentDescription = null,
                tint = AiPrimaryGreen,
                modifier = Modifier.size(16.dp)
            )
        }
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AiBorderColor)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                CircularProgressIndicator(
                    color = AiPrimaryGreen,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Trợ lý AI đang phân tích tài liệu...",
                    color = AiMutedText,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

@Composable
private fun AiComposerArea(
    uiState: AiChatUiState,
    onInputChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onSuggestionClick: (AiQuickSuggestion) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .imePadding()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            uiState.suggestions.forEach { suggestion ->
                Surface(
                    color = AiSoftGreenBg,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, AiPrimaryGreen.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = !uiState.isSending) {
                            onSuggestionClick(suggestion)
                        }
                        .testTag("chip_suggest_${suggestion.id}")
                ) {
                    Text(
                        text = suggestion.label,
                        color = AiPrimaryGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        if (!uiState.inputError.isNullOrBlank()) {
            Text(
                text = uiState.inputError,
                color = Color(0xFFDC2626),
                fontSize = 11.5.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("txt_ai_input_error")
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = onInputChanged,
                enabled = !uiState.isSending,
                isError = !uiState.inputError.isNullOrBlank(),
                placeholder = {
                    Text(
                        text = "Hỏi về nội dung tài liệu...",
                        color = AiMutedText,
                        fontSize = 13.sp
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AiPrimaryGreen,
                    unfocusedBorderColor = AiBorderColor,
                    focusedContainerColor = AiScreenBg,
                    unfocusedContainerColor = AiScreenBg
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_ai_question")
            )

            Button(
                onClick = onSendClick,
                enabled = uiState.canSend,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPrimaryGreen,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE2E8F0),
                    disabledContentColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_ai_send")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send),
                    contentDescription = "Gửi câu hỏi",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AiGuestNotice(onLoginClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
            .testTag("ai_guest_view")
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lock_keyhole),
            contentDescription = null,
            tint = AiPrimaryGreen,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Đăng nhập để dùng Trợ lý AI",
            color = AiNavyText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Trợ lý AI chỉ hỗ trợ hỏi đáp trên các tài liệu bạn đã mua.",
            color = AiMutedText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        UniButton(
            text = "Đăng nhập / Đăng ký",
            onClick = onLoginClick,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .testTag("btn_ai_guest_login")
        )
    }
}

@Preview(widthDp = 412, heightDp = 915, name = "AI Chat - 412dp")
@Composable
private fun PreviewAiChat412() {
    val state = AiChatUiState(
        isGuest = false,
        activeUid = FakeDocumentAiRepository.UID_BUYER_ONE,
        docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
        documentTitle = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
        messages = listOf(
            AiChatMessage(
                id = "m1",
                clientMessageId = "c1",
                role = AiChatRole.USER,
                text = "Hãy tóm tắt những ý chính quan trọng nhất của tài liệu này."
            ),
            AiChatMessage(
                id = "m2",
                clientMessageId = "c1",
                role = AiChatRole.ASSISTANT,
                text = "Tài liệu trình bày điều kiện áp dụng Binary Search, mã giả tránh tràn kiểu int và biến thể tìm biên trái/phải.",
                citations = listOf(AiCitation(1), AiCitation(2))
            )
        )
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        AiChatScreenContent(
            uiState = state,
            onBackClick = {},
            onInputChanged = {},
            onSendClick = {},
            onSuggestionClick = {},
            onRetryClick = {},
            onCitationClick = {},
            onLoginClick = {},
            onBottomNavSelected = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, fontScale = 1.5f, name = "AI Chat - 360dp Large Font")
@Composable
private fun PreviewAiChat360LargeFont() {
    val state = AiChatUiState(
        isGuest = false,
        activeUid = FakeDocumentAiRepository.UID_BUYER_ONE,
        docId = FakeDocumentAiRepository.DOC_MACRO_ECON,
        documentTitle = "Tóm tắt Kinh tế vĩ mô - Chương 1, 2, 3"
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        AiChatScreenContent(
            uiState = state,
            onBackClick = {},
            onInputChanged = {},
            onSendClick = {},
            onSuggestionClick = {},
            onRetryClick = {},
            onCitationClick = {},
            onLoginClick = {},
            onBottomNavSelected = {}
        )
    }
}
