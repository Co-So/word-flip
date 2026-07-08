package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.rememberWordFlipToast

/**
 * 登录页（P0-A05）：品牌区 + 单卡片表单（字段 + 忘记密码 + 主 CTA）；Natural Sage A 类布局。
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit = {},
    onNavigateToForgotPassword: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var accountTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }

    val isLoading by viewModel.isLoading.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AuthUiEvent.Success -> onLoginSuccess()
                is AuthUiEvent.Error -> toast.show(event.message)
                is AuthUiEvent.Info -> toast.show(event.message)
                AuthUiEvent.NavigateToLogin -> Unit
            }
        }
    }

    val accountError = if (accountTouched) {
        AuthFormValidation.validateLoginAccount(account)
    } else {
        null
    }
    val passwordError = if (passwordTouched) {
        AuthFormValidation.validatePassword(password)
    } else {
        null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
    ) { innerPadding ->
        AuthScreenContainer(
            modifier = Modifier.padding(innerPadding),
            centerContent = true,
        ) {
            AuthBrandHeader(style = AuthBrandStyle.Login)

            AuthFormCard(
                footer = {
                    AuthPrimaryButton(
                        text = "登录",
                        onClick = {
                            accountTouched = true
                            passwordTouched = true
                            val accountErr = AuthFormValidation.validateLoginAccount(account)
                            val passErr = AuthFormValidation.validatePassword(password)
                            if (accountErr == null && passErr == null) {
                                viewModel.login(account, password)
                            }
                        },
                        enabled = !isLoading,
                        isLoading = isLoading,
                    )
                },
            ) {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("账号") },
                    placeholder = { Text("邮箱或 11 位手机号") },
                    isError = accountError != null,
                    supportingText = accountError?.let { { Text(it) } },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .authScrollOnFocus { accountTouched = true },
                )
                AuthPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    visible = passwordVisible,
                    onVisibilityToggle = { passwordVisible = !passwordVisible },
                    enabled = !isLoading,
                    isError = passwordError != null,
                    supportingText = passwordError,
                    onBlur = { passwordTouched = true },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onNavigateToForgotPassword,
                        enabled = !isLoading,
                    ) {
                        Text("忘记密码？")
                    }
                }
            }

            AuthFooterLink(
                prompt = "没有账号？",
                actionLabel = "注册",
                onClick = onNavigateToRegister,
                enabled = !isLoading,
            )
        }
    }
}
