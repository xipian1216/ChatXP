package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.token.DrawerHeaderBottomPadding
import com.xipian.chatxp_android.ui.token.DrawerHeaderHorizontalPadding
import com.xipian.chatxp_android.ui.token.DrawerHeaderTopPadding
import com.xipian.chatxp_android.ui.token.IconButtonSize

@Composable
fun ThreadDrawerHeader(
    title: String,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DrawerHeaderHorizontalPadding,
                top = DrawerHeaderTopPadding,
                end = DrawerHeaderHorizontalPadding,
                bottom = DrawerHeaderBottomPadding
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        Box(
            modifier = Modifier
                .size(IconButtonSize)
                .clickable(onClick = onCloseClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.icon_close),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@ChatComponentPreview
@Composable
private fun ThreadDrawerHeaderPreview() {
    ChatPreviewFrame(contentAlignment = Alignment.TopCenter) {
        ThreadDrawerHeader(
            title = stringResource(R.string.drawer_title),
            onCloseClick = {}
        )
    }
}
