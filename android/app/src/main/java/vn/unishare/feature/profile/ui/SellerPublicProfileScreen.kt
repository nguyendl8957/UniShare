package vn.unishare.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unishare.R
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

private val PublicProfileBg = Color(0xFFF5F8FB)
private val PublicProfilePrimaryBlue = Color(0xFF0758B4)
private val PublicProfileNavy = Color(0xFF10244E)
private val PublicProfileMuted = Color(0xFF7789AE)
private val PublicProfileCardBorder = Color(0xFFE5EDF5)
private val PublicProfileAvatarBg = Color(0xFFD5E0F2)
private val PublicProfileBadgeBlue = Color(0xFF1966D2)
private val PublicProfileStarGold = Color(0xFFF4B400)

// Màn hình Hồ sơ công khai của người bán
@Composable
fun SellerPublicProfileScreen(
    sellerId: String,
    viewModel: ProfileViewModel,
    onBackClick: () -> Unit = {},
    onBackToListingClick: () -> Unit = onBackClick,
    onDocumentClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sellerId) {
        viewModel.loadSellerPublicProfile(sellerId)
    }

    UniShareTheme(mode = UniAppMode.SELLER) {
        SellerPublicProfileContent(
            loadState = uiState.publicSellerState,
            onBackClick = onBackClick,
            onBackToListingClick = onBackToListingClick,
            onRetryClick = viewModel::retrySellerPublicProfile,
            onDocumentClick = onDocumentClick,
            modifier = modifier
        )
    }
}

@Composable
fun SellerPublicProfileContent(
    loadState: SellerPublicProfileLoadState,
    onBackClick: () -> Unit,
    onBackToListingClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDocumentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("seller_public_profile_screen"),
        containerColor = PublicProfileBg,
        topBar = {
            SellerPublicTopBar(
                onBackClick = onBackClick,
                onBackToListingClick = onBackToListingClick
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            when (loadState) {
                is SellerPublicProfileLoadState.Idle,
                is SellerPublicProfileLoadState.Loading -> {
                    UiFeedback(state = UiFeedbackState.Loading)
                }

                is SellerPublicProfileLoadState.Error -> {
                    UiFeedback(
                        state = UiFeedbackState.Error(loadState.message),
                        onRetry = onRetryClick
                    )
                }

                is SellerPublicProfileLoadState.Empty -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("public_seller_empty_view")
                    ) {
                        UiFeedback(
                            state = UiFeedbackState.Empty(
                                "Không tìm thấy hồ sơ người bán này hoặc người bán đã ngừng hoạt động."
                            ),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBackToListingClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PublicProfilePrimaryBlue),
                            modifier = Modifier.testTag("btn_empty_return_to_listing")
                        ) {
                            Text("Quay về tài liệu đang xem", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                is SellerPublicProfileLoadState.Content -> {
                    SellerPublicProfileBody(
                        profile = loadState.profile,
                        onBackToListingClick = onBackToListingClick,
                        onDocumentClick = onDocumentClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerPublicTopBar(
    onBackClick: () -> Unit,
    onBackToListingClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("btn_public_seller_back")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left),
                        contentDescription = "Quay lại",
                        tint = PublicProfileNavy,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.unishare_wordmark_seller_profile),
                    contentDescription = "UniShare Hồ sơ người bán",
                    modifier = Modifier
                        .width(124.dp)
                        .height(38.dp)
                )
            }

            OutlinedButton(
                onClick = onBackToListingClick,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, PublicProfilePrimaryBlue.copy(alpha = 0.35f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .testTag("btn_back_to_listing")
            ) {
                Text(
                    text = "Về tài liệu",
                    color = PublicProfilePrimaryBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SellerPublicProfileBody(
    profile: SellerPublicProfile,
    onBackToListingClick: () -> Unit,
    onDocumentClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("public_seller_content_list")
    ) {
        // Thẻ nhận diện công khai của người bán
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, PublicProfileCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Box(modifier = Modifier.size(78.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(PublicProfileAvatarBg)
                        ) {
                            Text(
                                text = profile.displayName.take(1).uppercase(),
                                color = PublicProfileNavy,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = PublicProfileBadgeBlue,
                            shape = CircleShape,
                            border = BorderStroke(2.dp, Color.White),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = profile.verifiedBadge,
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = profile.displayName,
                        color = PublicProfileNavy,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("txt_public_seller_name")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${profile.school} • ${profile.faculty}",
                        color = PublicProfileMuted,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("txt_public_seller_school")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = profile.joinedLabel,
                        color = PublicProfileMuted,
                        fontSize = 11.sp
                    )

                    if (profile.achievements.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            profile.achievements.forEach { badge ->
                                AchievementBadgePill(badge = badge)
                            }
                        }
                    }
                }
            }
        }

        // 3 chỉ số thống kê công khai
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PublicSellerStatCard(
                    label = "Đánh giá",
                    value = "${profile.rating} (${profile.reviewCount})",
                    showStar = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_public_seller_rating")
                )
                PublicSellerStatCard(
                    label = "Tài liệu",
                    value = "${profile.totalDocuments}",
                    showStar = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_public_seller_docs")
                )
                PublicSellerStatCard(
                    label = "Đã bán",
                    value = "${profile.totalSold}",
                    showStar = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_public_seller_sold")
                )
            }
        }

        // Phần giới thiệu
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PublicProfileCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "Giới thiệu",
                        color = PublicProfileNavy,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = profile.bio,
                        color = PublicProfileMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.testTag("txt_public_seller_bio")
                    )
                }
            }
        }

        // Tiêu đề danh sách tài liệu đang bán
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tài liệu đang bán (${profile.listings.size})",
                    color = PublicProfileNavy,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Danh sách tài liệu công khai của người bán
        items(
            items = profile.listings,
            key = { it.docId }
        ) { item ->
            PublicSellerListingCard(
                item = item,
                onClick = { onDocumentClick(item.docId) }
            )
        }

        // Nút quay lại tài liệu đang xem
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onBackToListingClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PublicProfilePrimaryBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("btn_return_to_listing")
            ) {
                Text(
                    text = "Quay về tài liệu đang xem",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PublicSellerStatCard(
    label: String,
    value: String,
    showStar: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PublicProfileCardBorder),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showStar) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_star),
                        contentDescription = null,
                        tint = PublicProfileStarGold,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = value,
                    color = PublicProfileNavy,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = label,
                color = PublicProfileMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PublicSellerListingCard(
    item: SellerPublicListingItem,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PublicProfileCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("public_listing_${item.docId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE4F2FF))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_file_text),
                    contentDescription = null,
                    tint = PublicProfileBadgeBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    color = PublicProfileNavy,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(item.categoryBgHex),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = item.categoryLabel,
                            color = Color(item.categoryTextHex),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_star),
                            contentDescription = null,
                            tint = PublicProfileStarGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${item.rating} (${item.reviewCount})",
                            color = PublicProfileMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF0F4F8))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${item.viewCount} lượt xem • ${item.soldCount} đã bán",
                        color = PublicProfileMuted,
                        fontSize = 10.5.sp
                    )
                    Text(
                        text = formatProfileVnd(item.priceVnd),
                        color = PublicProfilePrimaryBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Seller Public Profile - 360dp")
@Composable
private fun PreviewSellerPublicProfile360() {
    val sampleProfile = SellerPublicProfile(
        sellerId = "seller_hung_01",
        displayName = "Nguyễn Tuấn Hùng",
        school = "ĐH Bách Khoa TP.HCM",
        faculty = "Khoa Khoa học máy tính",
        bio = "Tác giả bộ tài liệu Lập trình C++ và Cấu trúc dữ liệu giải thuật dành cho sinh viên CNTT.",
        verifiedBadge = "TP",
        rating = 4.9,
        reviewCount = 124,
        totalDocuments = 6,
        totalSold = 532,
        joinedLabel = "Tham gia 08/2025",
        achievements = ProfileUiState.defaultProfileBadges(),
        listings = SellerDashboardSummary.defaultSample().myDocuments
    )
    UniShareTheme(mode = UniAppMode.SELLER) {
        SellerPublicProfileContent(
            loadState = SellerPublicProfileLoadState.Content(sampleProfile),
            onBackClick = {},
            onBackToListingClick = {},
            onRetryClick = {},
            onDocumentClick = {}
        )
    }
}

@Preview(widthDp = 412, heightDp = 890, fontScale = 1.5f, name = "Seller Public Profile - 412dp Font 1.5x")
@Composable
private fun PreviewSellerPublicProfileLargeFont() {
    val sampleProfile = SellerPublicProfile(
        sellerId = "uid_student_01",
        displayName = "Đặng Lâm Nguyên",
        school = "Trường đại học Giao thông vận tải TPHCM",
        faculty = "Khoa CNTT",
        bio = "Sinh viên năm 3 chuyên ngành Công nghệ phần mềm. Chia sẻ ghi chú môn học, sơ đồ tư duy và mã nguồn đồ án thực hành đạt điểm A+.",
        verifiedBadge = "A+",
        rating = 4.9,
        reviewCount = 124,
        totalDocuments = 8,
        totalSold = 532,
        joinedLabel = "Tham gia 09/2025",
        achievements = ProfileUiState.defaultProfileBadges(),
        listings = SellerDashboardSummary.defaultSample().myDocuments
    )
    UniShareTheme(mode = UniAppMode.SELLER) {
        SellerPublicProfileContent(
            loadState = SellerPublicProfileLoadState.Content(sampleProfile),
            onBackClick = {},
            onBackToListingClick = {},
            onRetryClick = {},
            onDocumentClick = {}
        )
    }
}
