package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 注册页（REQ-AUTH-2~4）；Mock 阶段校验通过后直接进入主页。
 */
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(AuthFormValidation.RegisterMode.EMAIL) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var accountTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val accountError = when {
        !accountTouched -> null
        mode == AuthFormValidation.RegisterMode.EMAIL ->
            AuthFormValidation.validateRegisterEmail(email)
        else -> AuthFormValidation.validateRegisterPhone(phone)
    }
    val passwordError = if (passwordTouched) {
        AuthFormValidation.validatePassword(password, forRegister = true)
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "创建账号",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "邮箱或手机号二选一注册",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(32.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == AuthFormValidation.RegisterMode.EMAIL,
                onClick = { mode = AuthFormValidation.RegisterMode.EMAIL },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("邮箱")
            }
            SegmentedButton(
                selected = mode == AuthFormValidation.RegisterMode.PHONE,
                onClick = { mode = AuthFormValidation.RegisterMode.PHONE },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("手机")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (mode == AuthFormValidation.RegisterMode.EMAIL) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱") },
                isError = accountError != null,
                supportingText = accountError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) accountTouched = true },
            )
        } else {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("手机号") },
                placeholder = { Text("+8613800138000") },
                isError = accountError != null,
                supportingText = accountError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) accountTouched = true },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) passwordTouched = true },
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                accountTouched = true
                passwordTouched = true
                val accountErr = when (mode) {
                    AuthFormValidation.RegisterMode.EMAIL ->
                        AuthFormValidation.validateRegisterEmail(email)
                    AuthFormValidation.RegisterMode.PHONE ->
                        AuthFormValidation.validateRegisterPhone(phone)
                }
                val passErr = AuthFormValidation.validatePassword(password, forRegister = true)
                if (accountErr != null || passErr != null) return@Button
                scope.launch {
                    isSubmitting = true
                    delay(300)
                    isSubmitting = false
                    onRegisterSuccess()
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text("注册")
        }
        TextButton(onClick = onNavigateToLogin) {
            Text("已有账号？登录")
        }
    }
}
