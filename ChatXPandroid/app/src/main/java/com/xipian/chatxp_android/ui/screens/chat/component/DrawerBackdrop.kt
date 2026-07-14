package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.token.DrawerBackdropAlpha

@Composable
fun DrawerBackdrop(
    backdropAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha))
            .clickable(onClick = onClick)
    )
}

@ChatComponentPreview
@Composable
private fun DrawerBackdropPreview() {
    ChatPreviewFrame {
        DrawerBackdrop(
            backdropAlpha = DrawerBackdropAlpha,
            onClick = {}
        )
    }
}
