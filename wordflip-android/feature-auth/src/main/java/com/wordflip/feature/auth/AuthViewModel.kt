package com.wordflip.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.auth.RegisterRequest
import com.wordflip.core.network.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Auth 页一次性事件 */
sealed interface AuthUiEvent {
    data object Success : AuthUiEvent
    /** 注册/重置成功后回登录 */
    data object NavigateToLogin : AuthUiEvent
    data class Error(val message: String) : AuthUiEvent
    data class Info(val message: String) : AuthUiEvent
}

/**
 * 认证 ViewModel（P0-A04~A05）：register/login/模拟验证码/找回密码。
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _codeCountdown = MutableStateFlow(0)
    val codeCountdown: StateFlow<Int> = _codeCountdown.asStateFlow()

    private val _events = MutableSharedFlow<AuthUiEvent>()
    val events: SharedFlow<AuthUiEvent> = _events.asSharedFlow()

    private var countdownJob: Job? = null

    fun login(account: String, password: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val normalized = AuthFormValidation.normalizeLoginAccount(account)
            val result = authRepository.login(normalized, password)
            _isLoading.value = false
            result.fold(
                onSuccess = { _events.emit(AuthUiEvent.Success) },
                onFailure = { _events.emit(AuthUiEvent.Error(it.message.orEmpty())) },
            )
        }
    }

    fun register(
        mode: AuthFormValidation.RegisterMode,
        email: String,
        dialCode: String,
        localPhone: String,
        password: String,
        verificationCode: String,
    ) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val accountKey = when (mode) {
                AuthFormValidation.RegisterMode.EMAIL -> email.trim()
                AuthFormValidation.RegisterMode.PHONE ->
                    AuthFormValidation.formatPhoneE164(dialCode, localPhone)
            }
            if (!MockVerificationService.verify(
                    accountKey,
                    MockVerificationService.Purpose.REGISTER,
                    verificationCode,
                )
            ) {
                _isLoading.value = false
                _events.emit(AuthUiEvent.Error("验证码错误或已过期"))
                return@launch
            }
            val request = when (mode) {
                AuthFormValidation.RegisterMode.EMAIL ->
                    RegisterRequest.Email(email = email.trim(), password = password)
                AuthFormValidation.RegisterMode.PHONE ->
                    RegisterRequest.Phone(
                        phone = AuthFormValidation.formatPhoneE164(dialCode, localPhone),
                        password = password,
                    )
            }
            val result = authRepository.register(request)
            _isLoading.value = false
            result.fold(
                onSuccess = { _events.emit(AuthUiEvent.Success) },
                onFailure = { _events.emit(AuthUiEvent.Error(it.message.orEmpty())) },
            )
        }
    }

    /** 发送注册验证码（模拟） */
    fun sendRegisterCode(
        mode: AuthFormValidation.RegisterMode,
        email: String,
        dialCode: String,
        localPhone: String,
    ) {
        if (_codeCountdown.value > 0) return
        val accountKey = when (mode) {
            AuthFormValidation.RegisterMode.EMAIL -> {
                val err = AuthFormValidation.validateRegisterEmail(email)
                if (err != null) {
                    viewModelScope.launch { _events.emit(AuthUiEvent.Error(err)) }
                    return
                }
                email.trim()
            }
            AuthFormValidation.RegisterMode.PHONE -> {
                val err = AuthFormValidation.validateLocalPhoneNumber(dialCode, localPhone)
                if (err != null) {
                    viewModelScope.launch { _events.emit(AuthUiEvent.Error(err)) }
                    return
                }
                AuthFormValidation.formatPhoneE164(dialCode, localPhone)
            }
        }
        val hint = MockVerificationService.sendCode(
            accountKey,
            MockVerificationService.Purpose.REGISTER,
        )
        // 先启动倒计时，避免连点时 UI 未及时禁用发送按钮
        startCountdown()
        viewModelScope.launch {
            _events.emit(AuthUiEvent.Info(hint))
        }
    }

    /** 发送找回密码验证码（模拟） */
    fun sendResetPasswordCode(account: String) {
        if (_codeCountdown.value > 0) return
        val err = AuthFormValidation.validateLoginAccount(account)
        if (err != null) {
            viewModelScope.launch { _events.emit(AuthUiEvent.Error(err)) }
            return
        }
        val normalized = AuthFormValidation.normalizeLoginAccount(account)
        val hint = MockVerificationService.sendCode(
            normalized,
            MockVerificationService.Purpose.RESET_PASSWORD,
        )
        startCountdown()
        viewModelScope.launch {
            _events.emit(AuthUiEvent.Info(hint))
        }
    }

    /**
     * 找回密码（模拟）：校验验证码后提示成功；后端 reset API 上线前不修改服务端密码。
     */
    fun resetPassword(
        account: String,
        verificationCode: String,
        newPassword: String,
    ) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val normalized = AuthFormValidation.normalizeLoginAccount(account)
            if (!MockVerificationService.verify(
                    normalized,
                    MockVerificationService.Purpose.RESET_PASSWORD,
                    verificationCode,
                )
            ) {
                _isLoading.value = false
                _events.emit(AuthUiEvent.Error("验证码错误或已过期"))
                return@launch
            }
            _isLoading.value = false
            _events.emit(
                AuthUiEvent.Info("密码已重置（模拟环境；后端接口上线后可真正修改密码）"),
            )
            _events.emit(AuthUiEvent.NavigateToLogin)
        }
    }

    private fun startCountdown(seconds: Int = 60) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _codeCountdown.value = seconds
            while (_codeCountdown.value > 0) {
                delay(1_000)
                _codeCountdown.value -= 1
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
