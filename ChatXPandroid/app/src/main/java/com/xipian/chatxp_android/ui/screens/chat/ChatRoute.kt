package com.xipian.chatxp_android.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xipian.chatxp_android.app.ChatXpApplication

@Composable
fun ChatRoute(
    identityKey: String = "default",
    profileLabel: String = "",
    showGuestIcon: Boolean = true,
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as ChatXpApplication
    val container = application.container
    val factory = remember(container) {
        ChatViewModel.factory(container.chatRepository, container.authStore)
    }
    val viewModel: ChatViewModel = viewModel(key = "chat-$identityKey", factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatScreen(
        uiState = uiState,
        onAction = { action ->
            if (action == ChatAction.ProfileClick) onProfileClick() else viewModel.onAction(action)
        },
        profileLabel = profileLabel,
        showGuestIcon = showGuestIcon,
        modifier = modifier
    )
}
