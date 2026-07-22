package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.rememberWordFlipToast
import com.wordflip.core.ui.apple.AppleUi

/**
 * 找回密码（规划项 REQ-AUTH；模拟验证码 gate，后端 reset API 待 B-07）。
 * 布局与注册页一致：TopBar 返回 + 紧凑品牌 + 单卡片（验证/新密码 + 主 CTA）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var account by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var accountTouched by remember { mutableStateOf(false) }
    var codeTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var confirmTouched by remember { mutableStateOf(false) }

    val isLoading by viewModel.isLoading.collectAsState()
    val codeCountdown by viewModel.codeCountdown.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AuthUiEvent.NavigateToLogin -> onResetSuccess()
                is AuthUiEvent.Error -> toast.show(event.message)
                is AuthUiEvent.Info -> toast.show(event.message)
                AuthUiEvent.Success -> Unit
            }
        }
    }

    val accountError = if (accountTouched) {
        AuthFormValidation.validateLoginAccount(account)
    } else {
        null
    }
    val codeError = if (codeTouched) {
        AuthFormValidation.validateVerificationCode(verificationCode)
    } else {
        null
    }
    val passwordError = if (passwordTouched) {
        AuthFormValidation.validatePassword(newPassword, forRegister = true)
    } else {
        null
    }
    val confirmError = if (confirmTouched) {
        AuthFormValidation.validateConfirmPassword(newPassword, confirmPassword)
    } else {
        null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppleUi.colors.canvas,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isLoading) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
    ) { innerPadding ->
        AuthScreenContainer(
            modifier = Modifier.padding(innerPadding),
        ) {
            AuthBrandHeader(
                style = AuthBrandStyle.RegisterCompact,
                pageTitle = "找回密码",
            )

            AuthFormCard(
                footer = {
                    AuthPrimaryButton(
                        text = "重置密码",
                        onClick = {
                            accountTouched = true
                            codeTouched = true
                            passwordTouched = true
                            confirmTouched = true
                            val accountErr = AuthFormValidation.validateLoginAccount(account)
                            val codeErr = AuthFormValidation.validateVerificationCode(verificationCode)
                            val passErr = AuthFormValidation.validatePassword(newPassword, forRegister = true)
                            val confirmErr = AuthFormValidation.validateConfirmPassword(newPassword, confirmPassword)
                            if (accountErr != null || codeErr != null || passErr != null || confirmErr != null) {
                                return@AuthPrimaryButton
                            }
                            viewModel.resetPassword(account, verificationCode, newPassword)
                        },
                        enabled = !isLoading,
                        isLoading = isLoading,
                    )
                },
            ) {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("邮箱或手机号") },
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
                VerificationCodeInput(
                    code = verificationCode,
                    onCodeChange = { verificationCode = it },
                    countdownSeconds = codeCountdown,
                    onSendClick = { viewModel.sendResetPasswordCode(account) },
                    enabled = !isLoading,
                    isError = codeError != null,
                    supportingText = codeError ?: "开发模式验证码见 Toast",
                    onBlur = { codeTouched = true },
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "设置新密码",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AuthPasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "新密码",
                    visible = passwordVisible,
                    onVisibilityToggle = { passwordVisible = !passwordVisible },
                    enabled = !isLoading,
                    isError = passwordError != null,
                    supportingText = passwordError,
                    onBlur = { passwordTouched = true },
                )
                AuthPasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "确认新密码",
                    visible = confirmVisible,
                    onVisibilityToggle = { confirmVisible = !confirmVisible },
                    enabled = !isLoading,
                    isError = confirmError != null,
                    supportingText = confirmError,
                    onBlur = { confirmTouched = true },
                )
            }

            AuthFooterLink(
                prompt = "想起密码了？",
                actionLabel = "返回登录",
                onClick = onNavigateBack,
                enabled = !isLoading,
            )
        }
    }
}
