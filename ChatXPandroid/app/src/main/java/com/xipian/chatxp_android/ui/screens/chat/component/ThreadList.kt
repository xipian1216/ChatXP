package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.ChatSession
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.screens.chat.preview.PreviewSelectedSessionId
import com.xipian.chatxp_android.ui.screens.chat.preview.previewChatSessions
import com.xipian.chatxp_android.ui.token.DrawerListBottomPadding
import com.xipian.chatxp_android.ui.token.DrawerListHorizontalPadding
import com.xipian.chatxp_android.ui.token.Space1
import com.xipian.chatxp_android.ui.token.Space4

@Composable
fun ThreadList(
    sessions: List<ChatSession>,
    selectedSessionId: String?,
    isGenerating: Boolean,
    emptySearchText: String,
    onSessionSelected: (String) -> Unit,
    onSessionRenameRequested: (String) -> Unit,
    onSessionPinToggled: (String) -> Unit,
    onSessionDeleteRequested: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(Space4),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptySearchText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = DrawerListHorizontalPadding,
            end = DrawerListHorizontalPadding,
            bottom = DrawerListBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(Space1)
    ) {
        items(sessions, key = { it.sessionId }) { session ->
            ThreadItem(
                title = session.sessionTitle,
                isPinned = session.isPinned,
                isSelected = session.sessionId == selectedSessionId,
                isDeleteEnabled = !isGenerating || session.sessionId != selectedSessionId,
                onClick = { onSessionSelected(session.sessionId) },
                onRenameRequest = { onSessionRenameRequested(session.sessionId) },
                onTogglePin = { onSessionPinToggled(session.sessionId) },
                onDeleteRequest = { onSessionDeleteRequested(session.sessionId) }
            )
        }
    }
}

@ChatComponentPreview
@Composable
private fun ThreadListPreview() {
    ChatPreviewFrame {
        ThreadList(
            sessions = previewChatSessions(),
            selectedSessionId = PreviewSelectedSessionId,
            isGenerating = false,
            emptySearchText = stringResource(R.string.drawer_search_empty),
            onSessionSelected = {},
            onSessionRenameRequested = {},
            onSessionPinToggled = {},
            onSessionDeleteRequested = {}
        )
    }
}
