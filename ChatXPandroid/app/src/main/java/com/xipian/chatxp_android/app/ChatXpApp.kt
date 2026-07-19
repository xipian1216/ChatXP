package com.xipian.chatxp_android.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.auth.AccountDialog
import com.xipian.chatxp_android.ui.screens.auth.AuthRoute
import com.xipian.chatxp_android.ui.screens.auth.LogoutConfirmationDialog
import com.xipian.chatxp_android.ui.screens.chat.ChatRoute
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme
import com.xipian.chatxp_android.ui.token.IconButtonSize

@Composable
fun ChatXpApp() {
    val application = LocalContext.current.applicationContext as ChatXpApplication
    val repository = application.container.authRepository
    val factory = remember(repository) { AppViewModel.factory(repository) }
    val viewModel: AppViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatXPandroidTheme {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            when {
                uiState.isInitializing -> Box(
                    modifier = contentModifier,
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(IconButtonSize))
                }
                uiState.destination == AppDestination.AUTH -> AuthRoute(
                    identityKey = "${uiState.user?.id}-${uiState.identityRevision}",
                    onBack = viewModel::closeAuth,
                    onAuthenticated = viewModel::completeAuthentication,
                    modifier = contentModifier
                )
                else -> ChatRoute(
                    identityKey = "${uiState.user?.id}-${uiState.identityRevision}",
                    profileLabel = uiState.user?.avatarText.orEmpty(),
                    showGuestIcon = uiState.user?.isRegistered != true,
                    onProfileClick = viewModel::onProfileClick,
                    modifier = contentModifier
                )
            }
        }

        val user = uiState.user
        if (uiState.isAccountPanelOpen && user?.isRegistered == true) {
            AccountDialog(
                user = user,
                errorText = uiState.accountError?.let {
                    stringResource(
                        when (it) {
                            AccountError.BUSY -> R.string.auth_error_transition_busy
                            AccountError.NETWORK -> R.string.account_error_network
                            AccountError.UNKNOWN -> R.string.account_error_unknown
                        }
                    )
                },
                onDismiss = viewModel::closeAccountPanel,
                onLogoutClick = viewModel::requestLogout
            )
        }
        if (uiState.isLogoutConfirmationOpen) {
            LogoutConfirmationDialog(
                isSubmitting = uiState.isLoggingOut,
                onDismiss = viewModel::cancelLogout,
                onConfirm = viewModel::logout
            )
        }
    }
}
