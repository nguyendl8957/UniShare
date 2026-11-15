package vn.unishare.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vn.unishare.core.ui.theme.UniShareTheme

@Composable
fun UniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp) // Touch target >= 48dp chuẩn Accessibility
            .semantics {
                contentDescription = if (isLoading) "Đang xử lý: $text" else text
                if (isLoading) stateDescription = "Đang tải"
            },
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text)
    }
}

@Composable
fun UniTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    isLoading: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = "Ô nhập liệu cho $label"
                    if (errorMessage != null) stateDescription = "Lỗi: $errorMessage"
                },
            enabled = enabled && !isLoading,
            isError = errorMessage != null,
            singleLine = true
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Preview(widthDp = 360, name = "Small Screen (360dp)")
@Preview(widthDp = 412, name = "Normal Screen (412dp)")
@Preview(fontScale = 1.5f, name = "Large Font")
@Composable
private fun UniButtonPreview() {
    UniShareTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UniButton(text = "Nút bấm thường", onClick = {})
            UniButton(text = "Đang tải", onClick = {}, isLoading = true)
            UniButton(text = "Vô hiệu hóa", onClick = {}, enabled = false)
        }
    }
}

@Preview(widthDp = 360, name = "Small Screen (360dp)")
@Preview(widthDp = 412, name = "Normal Screen (412dp)")
@Preview(fontScale = 1.5f, name = "Large Font")
@Composable
private fun UniTextFieldPreview() {
    UniShareTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UniTextField(value = "Dữ liệu mẫu", onValueChange = {}, label = "Nhập tên")
            UniTextField(value = "Sai định dạng", onValueChange = {}, label = "Email", errorMessage = "Email không hợp lệ")
        }
    }
}
