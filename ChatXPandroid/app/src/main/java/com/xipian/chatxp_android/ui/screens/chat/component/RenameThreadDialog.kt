package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.theme.ChatCorner

@Composable
fun RenameThreadDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ChatCorner.Dialog,
        title = { Text(stringResource(R.string.thread_rename_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.thread_rename_field_label)) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim()) }
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    )
}

@ChatComponentPreview
@Composable
private fun RenameThreadDialogPreview() {
    ChatPreviewFrame {
        RenameThreadDialog(
            currentTitle = stringResource(R.string.preview_session_study_title),
            onDismiss = {},
            onConfirm = {}
        )
    }
}
