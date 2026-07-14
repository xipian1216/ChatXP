package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.screens.chat.preview.previewChatSessions
import com.xipian.chatxp_android.ui.token.SmallIconSize
import com.xipian.chatxp_android.ui.token.Space2
import com.xipian.chatxp_android.ui.token.Space4
import com.xipian.chatxp_android.ui.token.ThreadItemHorizontalPadding
import com.xipian.chatxp_android.ui.token.ThreadItemVerticalPadding

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadItem(
    title: String,
    isPinned: Boolean,
    isSelected: Boolean,
    isDeleteEnabled: Boolean,
    onClick: () -> Unit,
    onRenameRequest: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { isMenuExpanded = true }
                ),
            shape = MaterialTheme.shapes.medium,
            color = if (isSelected || isPinned) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = ThreadItemHorizontalPadding,
                    vertical = ThreadItemVerticalPadding
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.pin),
                        contentDescription = stringResource(R.string.icon_pinned),
                        modifier = Modifier.size(SmallIconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Space2))
                }
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ThreadSessionMenu(
            expanded = isMenuExpanded,
            isPinned = isPinned,
            isDeleteEnabled = isDeleteEnabled,
            onDismiss = { isMenuExpanded = false },
            onRenameClick = {
                isMenuExpanded = false
                onRenameRequest()
            },
            onTogglePinClick = {
                isMenuExpanded = false
                onTogglePin()
            },
            onDeleteClick = {
                isMenuExpanded = false
                onDeleteRequest()
            }
        )
    }
}

@ChatComponentPreview
@Composable
private fun ThreadItemPreview() {
    val session = previewChatSessions().first()

    ChatPreviewFrame(contentAlignment = Alignment.TopCenter) {
        ThreadItem(
            title = session.sessionTitle,
            isPinned = true,
            isSelected = false,
            isDeleteEnabled = true,
            onClick = {},
            onRenameRequest = {},
            onTogglePin = {},
            onDeleteRequest = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space4)
        )
    }
}
