package com.xipian.chatxp_android.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xipian.chatxp_android.data.remote.ApiException
import com.xipian.chatxp_android.data.repository.AuthRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onAction(action: AuthAction) {
        when (action) {
            is AuthAction.SelectTab -> selectTab(action.tab)
            is AuthAction.DisplayNameChanged -> _uiState.update {
                it.copy(displayName = action.value, displayNameError = null, globalError = null)
            }
            is AuthAction.EmailChanged -> _uiState.update {
                it.copy(email = action.value, emailError = null, globalError = null)
            }
            is AuthAction.PasswordChanged -> _uiState.update {
                it.copy(password = action.value, passwordError = null, globalError = null)
            }
            is AuthAction.ConfirmPasswordChanged -> _uiState.update {
                it.copy(
                    confirmPassword = action.value,
                    confirmPasswordError = null,
                    globalError = null
                )
            }
            AuthAction.TogglePasswordVisibility -> _uiState.update {
                it.copy(isPasswordVisible = !it.isPasswordVisible)
            }
            AuthAction.ToggleConfirmPasswordVisibility -> _uiState.update {
                it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible)
            }
            AuthAction.Submit -> submit()
            AuthAction.ForgotPassword -> _uiState.update { it.copy(showUnavailableNotice = true) }
            AuthAction.NoticeShown -> _uiState.update { it.copy(showUnavailableNotice = false) }
        }
    }

    private fun selectTab(tab: AuthTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                password = "",
                confirmPassword = "",
                isPasswordVisible = false,
                isConfirmPasswordVisible = false,
                displayNameError = null,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                globalError = null
            )
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || !validate(state)) return
        _uiState.update { it.copy(isSubmitting = true, globalError = null) }
        viewModelScope.launch {
            try {
                if (state.selectedTab == AuthTab.LOGIN) {
                    repository.login(state.email.trim(), state.password)
                } else {
                    repository.register(
                        state.displayName.trim(),
                        state.email.trim(),
                        state.password
                    )
                }
                _uiState.update { current ->
                    current.copy(isSubmitting = false, isAuthenticated = true)
                }
            } catch (error: Throwable) {
                if (error is ApiException && error.code == "ALREADY_AUTHENTICATED") {
                    val refreshed = runCatching { repository.initializeUser() }.isSuccess
                    if (refreshed) {
                        _uiState.update { current ->
                            current.copy(isSubmitting = false, isAuthenticated = true)
                        }
                        return@launch
                    }
                }
                _uiState.update { current ->
                    current.copy(
                        isSubmitting = false,
                        globalError = error.toAuthGlobalError()
                    )
                }
            }
        }
    }

    private fun validate(state: AuthUiState): Boolean {
        val name = state.displayName.trim()
        val email = state.email.trim()
        val displayNameError = when {
            state.selectedTab == AuthTab.LOGIN -> null
            name.isEmpty() -> AuthFieldError.REQUIRED
            name.length !in 1..30 -> AuthFieldError.INVALID_USERNAME
            else -> null
        }
        val emailError = when {
            email.isEmpty() -> AuthFieldError.REQUIRED
            !EMAIL_REGEX.matches(email) -> AuthFieldError.INVALID_EMAIL
            else -> null
        }
        val passwordError = when {
            state.password.isEmpty() -> AuthFieldError.REQUIRED
            state.selectedTab == AuthTab.REGISTER && state.password.length !in 8..72 -> {
                AuthFieldError.INVALID_PASSWORD_LENGTH
            }
            else -> null
        }
        val confirmError = when {
            state.selectedTab == AuthTab.LOGIN -> null
            state.confirmPassword.isEmpty() -> AuthFieldError.REQUIRED
            state.confirmPassword != state.password -> AuthFieldError.PASSWORD_MISMATCH
            else -> null
        }
        _uiState.update {
            it.copy(
                displayNameError = displayNameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmError
            )
        }
        return listOf(displayNameError, emailError, passwordError, confirmError).all { it == null }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

        fun factory(repository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(repository) as T
                }
            }
    }
}

private fun Throwable.toAuthGlobalError(): AuthGlobalError = when (this) {
    is ApiException -> when (code) {
        "INVALID_CREDENTIALS" -> AuthGlobalError.INVALID_CREDENTIALS
        "EMAIL_ALREADY_REGISTERED" -> AuthGlobalError.EMAIL_ALREADY_REGISTERED
        "LOGIN_RATE_LIMITED" -> AuthGlobalError.RATE_LIMITED
        "AUTH_TRANSITION_BUSY" -> AuthGlobalError.TRANSITION_BUSY
        else -> AuthGlobalError.UNKNOWN
    }
    is IOException -> AuthGlobalError.NETWORK
    else -> AuthGlobalError.UNKNOWN
}
