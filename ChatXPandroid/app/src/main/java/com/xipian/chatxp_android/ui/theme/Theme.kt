package com.xipian.chatxp_android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ChatXPandroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ChatDarkColorScheme else ChatLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChatTypography,
        shapes = ChatShapes,
        content = content
    )
}
