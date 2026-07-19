package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.theme.ChatSendButton
import com.xipian.chatxp_android.ui.theme.ChatSendButtonOn
import com.xipian.chatxp_android.ui.token.DrawerBottomBarBottomPadding
import com.xipian.chatxp_android.ui.token.DrawerBottomBarHorizontalPadding
import com.xipian.chatxp_android.ui.token.DrawerBottomBarTopPadding
import com.xipian.chatxp_android.ui.token.IconButtonSize
import com.xipian.chatxp_android.ui.token.IconSize
import com.xipian.chatxp_android.ui.token.ProfileButtonSize
import com.xipian.chatxp_android.ui.token.Space2
import com.xipian.chatxp_android.ui.token.Space4

@Composable
fun ThreadDrawerBottomBar(
    chatButtonText: String,
    profileLabel: String,
    showGuestIcon: Boolean = false,
    onNewChatClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DrawerBottomBarHorizontalPadding,
                top = DrawerBottomBarTopPadding,
                end = DrawerBottomBarHorizontalPadding,
                bottom = DrawerBottomBarBottomPadding
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .heightIn(min = IconButtonSize)
                .clickable(onClick = onNewChatClick),
            shape = MaterialTheme.shapes.medium,
            color = ChatSendButton,
            contentColor = ChatSendButtonOn
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Space4),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.new_session),
                    contentDescription = stringResource(R.string.icon_add),
                    modifier = Modifier.size(IconSize),
                    tint = ChatSendButtonOn
                )
                Spacer(modifier = Modifier.width(Space2))
                Text(text = chatButtonText, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            modifier = Modifier
                .size(ProfileButtonSize)
                .clickable(onClick = onProfileClick),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showGuestIcon) {
                    Icon(
                        painter = painterResource(R.drawable.tourist),
                        contentDescription = stringResource(R.string.auth_open),
                        modifier = Modifier.size(IconSize)
                    )
                } else {
                    Text(text = profileLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@ChatComponentPreview
@Composable
private fun ThreadDrawerBottomBarPreview() {
    ChatPreviewFrame(contentAlignment = Alignment.BottomCenter) {
        ThreadDrawerBottomBar(
            chatButtonText = stringResource(R.string.drawer_chat_button),
            profileLabel = stringResource(R.string.drawer_profile_label),
            showGuestIcon = true,
            onNewChatClick = {},
            onProfileClick = {}
        )
    }
}
