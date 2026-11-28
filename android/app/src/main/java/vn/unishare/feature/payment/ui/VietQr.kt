package vn.unishare.feature.payment.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object VietQr {

    private fun isVietinBank(info: BankTransferInfo): Boolean {
        val code = info.bankCode.trim()
            .uppercase(Locale.ROOT)

        val name = info.bankName.lowercase(Locale.ROOT)

        return code in setOf(
            "ICB", "CTG", "VIETINBANK", "970415"
        ) || name.contains("vietinbank") ||
            name.contains("công thương")
    }

    fun transferContent(info: BankTransferInfo): String {
        val code = info.paymentCode.trim()

        return if (isVietinBank(info)) {
            "SEVQR $code"
        } else {
            code
        }
    }

    fun imageUrl(info: BankTransferInfo): String? {
        if (info.amountVnd <= 0L) return null
        if (info.accountNumber.isBlank()) return null
        if (!Regex("^UNI[0-9A-Fa-f]{6}$")
                .matches(info.paymentCode.trim())) {
            return null
        }

        val bank = if (isVietinBank(info)) {
            "VietinBank"
        } else {
            info.bankCode.trim()
        }

        if (bank.isBlank()) return null

        fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20")

        return "https://vietqr.app/img" +
            "?acc=${encode(info.accountNumber.trim())}" +
            "&bank=${encode(bank)}" +
            "&amount=${info.amountVnd}" +
            "&des=${encode(transferContent(info))}" +
            "&template=qronly"
    }
}

@Composable
fun VietQrSection(info: BankTransferInfo) {
    val url = remember(info) {
        VietQr.imageUrl(info)
    }

    var bitmap by remember(url) {
        mutableStateOf<Bitmap?>(null)
    }

    var loading by remember(url) {
        mutableStateOf(true)
    }

    var failed by remember(url) {
        mutableStateOf(false)
    }

    var retry by remember(url) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(url, retry) {
        bitmap = null
        loading = true
        failed = false

        try {
            bitmap = withContext(Dispatchers.IO) {
                if (url == null) {
                    error("Invalid QR information")
                }

                val connection = URL(url)
                    .openConnection() as HttpURLConnection

                try {
                    connection.connectTimeout = 10000
                    connection.readTimeout = 15000
                    connection.useCaches = false
                    connection.requestMethod = "GET"
                    connection.setRequestProperty(
                        "Accept", "image/*"
                    )

                    if (connection.responseCode != 200) {
                        error("QR request failed")
                    }

                    connection.inputStream.use {
                        BitmapFactory.decodeStream(it)
                            ?: error("Invalid QR image")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            loading -> {
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            failed -> {
                Text(
                    text = "Không tải được mã QR. Bạn có thể chuyển khoản thủ công.",
                    fontSize = 12.sp,
                    color = Color(0xFFB45309)
                )

                TextButton(onClick = { retry++ }) {
                    Text("Tải lại mã QR")
                }
            }

            else -> {
                bitmap?.let { image ->
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Mã VietQR thanh toán",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White)
                            .padding(12.dp)
                            .testTag("image_vietqr")
                    )
                }

                Text(
                    text = "Mở ứng dụng ngân hàng để quét mã",
                    fontSize = 12.sp,
                    color = Color(0xFF667085)
                )
            }
        }
    }
}