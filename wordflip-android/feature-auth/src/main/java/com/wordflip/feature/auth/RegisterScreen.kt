package com.wordflip.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
 * 注册页（P0-A04）：TopBar 返回 + 紧凑品牌 + 单卡片（账号/验证码/密码 + 主 CTA）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var mode by remember { mutableStateOf(AuthFormValidation.RegisterMode.EMAIL) }
    var email by remember { mutableStateOf("") }
    var dialCode by remember { mutableStateOf("+86") }
    var localPhone by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
                AuthUiEvent.Success -> onRegisterSuccess()
                AuthUiEvent.NavigateToLogin -> onNavigateToLogin()
                is AuthUiEvent.Error -> toast.show(event.message)
                is AuthUiEvent.Info -> toast.show(event.message)
            }
        }
    }

    val accountError = when {
        !accountTouched -> null
        mode == AuthFormValidation.RegisterMode.EMAIL ->
            AuthFormValidation.validateRegisterEmail(email)
        else -> AuthFormValidation.validateLocalPhoneNumber(dialCode, localPhone)
    }
    val codeError = if (codeTouched) {
        AuthFormValidation.validateVerificationCode(verificationCode)
    } else {
        null
    }
    val passwordError = if (passwordTouched) {
        AuthFormValidation.validatePassword(password, forRegister = true)
    } else {
        null
    }
    val confirmError = if (confirmTouched) {
        AuthFormValidation.validateConfirmPassword(password, confirmPassword)
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
                    IconButton(onClick = onNavigateToLogin, enabled = !isLoading) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
    ) { innerPadding ->
        AuthScreenContainer(
            modifier = Modifier.padding(innerPadding),
        ) {
            AuthBrandHeader(style = AuthBrandStyle.RegisterCompact)

            AuthFormCard(
                footer = {
                    AuthPrimaryButton(
                        text = "注册",
                        onClick = {
                            accountTouched = true
                            codeTouched = true
                            passwordTouched = true
                            confirmTouched = true
                            val accountErr = when (mode) {
                                AuthFormValidation.RegisterMode.EMAIL ->
                                    AuthFormValidation.validateRegisterEmail(email)
                                AuthFormValidation.RegisterMode.PHONE ->
                                    AuthFormValidation.validateLocalPhoneNumber(dialCode, localPhone)
                            }
                            val codeErr = AuthFormValidation.validateVerificationCode(verificationCode)
                            val passErr = AuthFormValidation.validatePassword(password, forRegister = true)
                            val confirmErr = AuthFormValidation.validateConfirmPassword(password, confirmPassword)
                            if (accountErr != null || codeErr != null || passErr != null || confirmErr != null) {
                                return@AuthPrimaryButton
                            }
                            viewModel.register(mode, email, dialCode, localPhone, password, verificationCode)
                        },
                        enabled = !isLoading,
                        isLoading = isLoading,
                    )
                },
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == AuthFormValidation.RegisterMode.EMAIL,
                        onClick = { mode = AuthFormValidation.RegisterMode.EMAIL },
                        enabled = !isLoading,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("邮箱")
                    }
                    SegmentedButton(
                        selected = mode == AuthFormValidation.RegisterMode.PHONE,
                        onClick = { mode = AuthFormValidation.RegisterMode.PHONE },
                        enabled = !isLoading,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("手机")
                    }
                }

                if (mode == AuthFormValidation.RegisterMode.EMAIL) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("邮箱") },
                        isError = accountError != null,
                        supportingText = accountError?.let { { Text(it) } },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .authScrollOnFocus { accountTouched = true },
                    )
                } else {
                    PhoneNumberInput(
                        localNumber = localPhone,
                        onLocalNumberChange = { localPhone = it },
                        selectedDialCode = dialCode,
                        onDialCodeChange = { dialCode = it },
                        enabled = !isLoading,
                        isError = accountError != null,
                        supportingText = accountError,
                        onBlur = { accountTouched = true },
                    )
                }
                VerificationCodeInput(
                    code = verificationCode,
                    onCodeChange = { verificationCode = it },
                    countdownSeconds = codeCountdown,
                    onSendClick = {
                        viewModel.sendRegisterCode(mode, email, dialCode, localPhone)
                    },
                    enabled = !isLoading,
                    isError = codeError != null,
                    supportingText = codeError ?: "开发模式验证码见 Toast",
                    onBlur = { codeTouched = true },
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "设置密码",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                AuthPasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "确认密码",
                    visible = confirmVisible,
                    onVisibilityToggle = { confirmVisible = !confirmVisible },
                    enabled = !isLoading,
                    isError = confirmError != null,
                    supportingText = confirmError,
                    onBlur = { confirmTouched = true },
                )
            }

            AuthFooterLink(
                prompt = "已有账号？",
                actionLabel = "登录",
                onClick = onNavigateToLogin,
                enabled = !isLoading,
            )
        }
    }
}
