package com.xipian.chatxp_android.ui.screens.chat.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme

@Preview(
    name = "Light",
    widthDp = 360,
    heightDp = 640,
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Dark",
    widthDp = 360,
    heightDp = 640,
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class ChatComponentPreview

@Composable
fun ChatPreviewFrame(
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    ChatPreviewFrame(
        darkTheme = isSystemInDarkTheme(),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun ChatPreviewFrame(
    darkTheme: Boolean,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    ChatXPandroidTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = contentAlignment,
                content = content
            )
        }
    }
}
