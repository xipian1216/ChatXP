package com.xipian.chatxp_android.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.data.model.AccountType
import com.xipian.chatxp_android.data.model.AuthUser
import com.xipian.chatxp_android.ui.theme.ChatCorner
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme
import com.xipian.chatxp_android.ui.token.DialogActionSpacing
import com.xipian.chatxp_android.ui.token.DialogActionsTopMargin
import com.xipian.chatxp_android.ui.token.DialogButtonMinHeight
import com.xipian.chatxp_android.ui.token.DialogPadding
import com.xipian.chatxp_android.ui.token.IconButtonSize
import com.xipian.chatxp_android.ui.token.IconSize
import com.xipian.chatxp_android.ui.token.Space2

@Composable
fun AccountDialog(
    user: AuthUser,
    errorText: String?,
    onDismiss: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ChatCorner.Dialog,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(DialogPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(IconButtonSize),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = user.avatarText.orEmpty(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(Space2))
                    Column {
                        Text(
                            text = user.displayName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = user.email.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                errorText?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Space2)
                    )
                }
                Spacer(modifier = Modifier.height(DialogActionsTopMargin))
                TextButton(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DialogButtonMinHeight)
                ) {
                    Text(
                        text = stringResource(R.string.account_logout),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun LogoutConfirmationDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Surface(
            shape = ChatCorner.Dialog,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(DialogPadding)) {
                Text(
                    text = stringResource(R.string.account_logout_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(Space2))
                Text(
                    text = stringResource(R.string.account_logout_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DialogActionsTopMargin),
                    horizontalArrangement = Arrangement.spacedBy(DialogActionSpacing, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(IconSize),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.account_logout))
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Account light", showBackground = true, showSystemUi = true)
@Composable
private fun AccountDialogLightPreview() {
    AccountDialogPreview(darkTheme = false)
}

@Preview(name = "Account dark", showBackground = true, showSystemUi = true)
@Composable
private fun AccountDialogDarkPreview() {
    AccountDialogPreview(darkTheme = true)
}

@Preview(name = "Logout confirmation light", showBackground = true, showSystemUi = true)
@Composable
private fun LogoutConfirmationLightPreview() {
    LogoutConfirmationPreview(darkTheme = false)
}

@Preview(name = "Logout confirmation dark", showBackground = true, showSystemUi = true)
@Composable
private fun LogoutConfirmationDarkPreview() {
    LogoutConfirmationPreview(darkTheme = true)
}

@Composable
private fun LogoutConfirmationPreview(darkTheme: Boolean) {
    ChatXPandroidTheme(darkTheme = darkTheme) {
        Scaffold { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {}
            LogoutConfirmationDialog(isSubmitting = false, onDismiss = {}, onConfirm = {})
        }
    }
}

@Composable
private fun AccountDialogPreview(darkTheme: Boolean) {
    ChatXPandroidTheme(darkTheme = darkTheme) {
        Scaffold { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {}
        }
        AccountDialog(
            user = AuthUser(
                id = "preview",
                accountType = AccountType.REGISTERED,
                displayName = stringResource(R.string.auth_preview_display_name),
                email = stringResource(R.string.auth_email_placeholder),
                avatarText = stringResource(R.string.auth_preview_avatar)
            ),
            errorText = null,
            onDismiss = {},
            onLogoutClick = {}
        )
    }
}
