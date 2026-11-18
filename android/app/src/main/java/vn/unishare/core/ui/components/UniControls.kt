package vn.unishare.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        shape = RoundedCornerShape(12.dp), // Bo góc 12dp đúng chuẩn Figma (node 82:127)
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp) // Touch target >= 48dp chuẩn Accessibility & Figma (82:125)
            .semantics {
                contentDescription = if (isLoading) "Đang xử lý: $text" else text
                if (isLoading) stateDescription = "Đang tải"
            },
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
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
    isLoading: Boolean = false,
    @DrawableRes leadingIconRes: Int? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color(0xFF738096)
                )
            },
            leadingIcon = if (leadingIconRes != null) {
                {
                    Icon(
                        painter = painterResource(id = leadingIconRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                null
            },
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(12.dp), // Bo góc 12dp theo Figma (node 82:113)
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFCAC4D0),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            ),
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
            UniButton(text = "Đăng nhập", onClick = {})
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
            UniTextField(value = "", onValueChange = {}, label = "Email")
            UniTextField(value = "Sai định dạng", onValueChange = {}, label = "Email", errorMessage = "Email không hợp lệ")
        }
    }
}

