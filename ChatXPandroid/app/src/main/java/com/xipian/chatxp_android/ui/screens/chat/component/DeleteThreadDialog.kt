package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.theme.ChatCorner

@Composable
fun DeleteThreadDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ChatCorner.Dialog,
        title = { Text(stringResource(R.string.thread_delete_title)) },
        text = { Text(stringResource(R.string.thread_delete_message)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@ChatComponentPreview
@Composable
private fun DeleteThreadDialogPreview() {
    ChatPreviewFrame {
        DeleteThreadDialog(onDismiss = {}, onConfirm = {})
    }
}
