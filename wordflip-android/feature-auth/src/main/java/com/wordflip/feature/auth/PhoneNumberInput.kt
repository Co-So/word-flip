package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** 常用国际区号；默认中国大陆 +86 */
data class PhoneCountryOption(
    val dialCode: String,
    val label: String,
)

private val defaultCountryOptions = listOf(
    PhoneCountryOption("+86", "中国"),
    PhoneCountryOption("+852", "香港"),
    PhoneCountryOption("+1", "美国"),
)

/**
 * 手机号输入：紧凑区号下拉 + 本地号码，提交时拼 E.164。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNumberInput(
    localNumber: String,
    onLocalNumberChange: (String) -> Unit,
    selectedDialCode: String,
    onDialCodeChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean,
    supportingText: String?,
    modifier: Modifier = Modifier,
    onBlur: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded },
            modifier = Modifier.width(112.dp),
        ) {
            OutlinedTextField(
                value = selectedDialCode,
                onValueChange = {},
                readOnly = true,
                label = { Text("区号") },
                singleLine = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                defaultCountryOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.label} ${option.dialCode}") },
                        onClick = {
                            onDialCodeChange(option.dialCode)
                            expanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = localNumber,
            onValueChange = { raw ->
                onLocalNumberChange(raw.filter { it.isDigit() }.take(15))
            },
            label = { Text("手机号") },
            placeholder = { Text("13800138000") },
            isError = isError,
            supportingText = supportingText?.let {
                {
                    Text(
                        text = it,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .authScrollOnFocus(onBlur = onBlur),
        )
    }
}

/**
 * 验证码输入：全宽字段 + 尾部「发送」TextButton（避免并排按钮换行）。
 */
@Composable
fun VerificationCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    countdownSeconds: Int,
    onSendClick: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
    supportingText: String?,
    modifier: Modifier = Modifier,
    onBlur: () -> Unit = {},
) {
    OutlinedTextField(
        value = code,
        onValueChange = { onCodeChange(it.filter { it.isDigit() }.take(6)) },
        label = { Text("验证码") },
        placeholder = { Text("6 位数字") },
        isError = isError,
        supportingText = supportingText?.let {
            {
                Text(
                    text = it,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        trailingIcon = {
            TextButton(
                onClick = onSendClick,
                enabled = enabled && countdownSeconds == 0,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(
                    text = if (countdownSeconds > 0) "${countdownSeconds}s" else "发送",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (countdownSeconds > 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .authScrollOnFocus(onBlur = onBlur),
    )
}
