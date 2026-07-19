package com.xipian.chatxp_android.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xipian.chatxp_android.data.model.AccountType
import com.xipian.chatxp_android.data.model.AuthUser
import com.xipian.chatxp_android.data.remote.ApiException
import com.xipian.chatxp_android.data.repository.AuthRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppDestination { CHAT, AUTH }

enum class AccountError { BUSY, NETWORK, UNKNOWN }

data class AppUiState(
    val destination: AppDestination = AppDestination.CHAT,
    val user: AuthUser? = null,
    val isInitializing: Boolean = true,
    val isAccountPanelOpen: Boolean = false,
    val isLogoutConfirmationOpen: Boolean = false,
    val isLoggingOut: Boolean = false,
    val accountError: AccountError? = null,
    val identityRevision: Int = 0
)

class AppViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.currentUser.filterNotNull().collect { user ->
                _uiState.update { it.copy(user = user, isInitializing = false) }
            }
        }
        viewModelScope.launch {
            runCatching { repository.initializeUser() }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            user = state.user ?: AuthUser("guest", AccountType.GUEST),
                            isInitializing = false
                        )
                    }
                }
        }
    }

    fun onProfileClick() {
        val user = _uiState.value.user ?: return
        if (user.isRegistered) {
            _uiState.update { it.copy(isAccountPanelOpen = true, accountError = null) }
        } else {
            _uiState.update { it.copy(destination = AppDestination.AUTH) }
        }
    }

    fun closeAuth() = _uiState.update { it.copy(destination = AppDestination.CHAT) }

    fun completeAuthentication() {
        _uiState.update {
            it.copy(
                destination = AppDestination.CHAT,
                identityRevision = it.identityRevision + 1,
                isAccountPanelOpen = false
            )
        }
    }

    fun closeAccountPanel() = _uiState.update {
        it.copy(isAccountPanelOpen = false, accountError = null)
    }

    fun requestLogout() = _uiState.update {
        it.copy(isLogoutConfirmationOpen = true, accountError = null)
    }

    fun cancelLogout() = _uiState.update { it.copy(isLogoutConfirmationOpen = false) }

    fun logout() {
        if (_uiState.value.isLoggingOut) return
        _uiState.update { it.copy(isLoggingOut = true, accountError = null) }
        viewModelScope.launch {
            runCatching { repository.logout() }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            destination = AppDestination.CHAT,
                            isAccountPanelOpen = false,
                            isLogoutConfirmationOpen = false,
                            isLoggingOut = false,
                            identityRevision = state.identityRevision + 1
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoggingOut = false,
                            isLogoutConfirmationOpen = false,
                            accountError = error.toAccountError()
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(repository) as T
            }
    }
}

private fun Throwable.toAccountError(): AccountError = when (this) {
    is ApiException -> if (code == "AUTH_TRANSITION_BUSY") {
        AccountError.BUSY
    } else {
        AccountError.UNKNOWN
    }
    is IOException -> AccountError.NETWORK
    else -> AccountError.UNKNOWN
}
