package com.xipian.chatxp_android.ui.screens.auth

enum class AuthTab { LOGIN, REGISTER }

enum class AuthFieldError {
    REQUIRED,
    INVALID_EMAIL,
    INVALID_USERNAME,
    INVALID_PASSWORD_LENGTH,
    PASSWORD_MISMATCH
}

enum class AuthGlobalError {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_REGISTERED,
    RATE_LIMITED,
    TRANSITION_BUSY,
    NETWORK,
    UNKNOWN
}

data class AuthUiState(
    val selectedTab: AuthTab = AuthTab.LOGIN,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val displayNameError: AuthFieldError? = null,
    val emailError: AuthFieldError? = null,
    val passwordError: AuthFieldError? = null,
    val confirmPasswordError: AuthFieldError? = null,
    val globalError: AuthGlobalError? = null,
    val isSubmitting: Boolean = false,
    val showUnavailableNotice: Boolean = false,
    val isAuthenticated: Boolean = false
)

sealed interface AuthAction {
    data class SelectTab(val tab: AuthTab) : AuthAction
    data class DisplayNameChanged(val value: String) : AuthAction
    data class EmailChanged(val value: String) : AuthAction
    data class PasswordChanged(val value: String) : AuthAction
    data class ConfirmPasswordChanged(val value: String) : AuthAction
    data object TogglePasswordVisibility : AuthAction
    data object ToggleConfirmPasswordVisibility : AuthAction
    data object Submit : AuthAction
    data object ForgotPassword : AuthAction
    data object NoticeShown : AuthAction
}
