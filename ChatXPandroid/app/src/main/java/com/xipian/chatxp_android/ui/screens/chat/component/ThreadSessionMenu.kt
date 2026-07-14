package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.theme.ChatCorner
import com.xipian.chatxp_android.ui.token.DisabledAlpha
import com.xipian.chatxp_android.ui.token.IconButtonSize
import com.xipian.chatxp_android.ui.token.SmallIconSize

@Composable
fun ThreadSessionMenu(
    expanded: Boolean,
    isPinned: Boolean,
    isDeleteEnabled: Boolean,
    onDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onTogglePinClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = ChatCorner.Dialog
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.thread_action_rename)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.rename),
                    contentDescription = null,
                    modifier = Modifier.size(SmallIconSize),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            onClick = onRenameClick
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (isPinned) R.string.thread_action_unpin else R.string.thread_action_pin
                    )
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.pin_to_top),
                    contentDescription = null,
                    modifier = Modifier.size(SmallIconSize),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            onClick = onTogglePinClick
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.thread_action_delete),
                    color = MaterialTheme.colorScheme.error.copy(
                        alpha = if (isDeleteEnabled) 1f else DisabledAlpha
                    )
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null,
                    modifier = Modifier.size(SmallIconSize),
                    tint = MaterialTheme.colorScheme.error.copy(
                        alpha = if (isDeleteEnabled) 1f else DisabledAlpha
                    )
                )
            },
            enabled = isDeleteEnabled,
            onClick = onDeleteClick
        )
    }
}

@ChatComponentPreview
@Composable
private fun ThreadSessionMenuPreview() {
    ChatPreviewFrame {
        Box(modifier = Modifier.size(IconButtonSize)) {
            ThreadSessionMenu(
                expanded = true,
                isPinned = false,
                isDeleteEnabled = true,
                onDismiss = {},
                onRenameClick = {},
                onTogglePinClick = {},
                onDeleteClick = {}
            )
        }
    }
}
