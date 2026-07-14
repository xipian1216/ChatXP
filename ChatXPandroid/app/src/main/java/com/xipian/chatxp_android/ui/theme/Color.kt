package com.xipian.chatxp_android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val ChatBackground = Color(0xFFFFFFFF)
val ChatSurface = Color(0xFFF5F5F5)
val ChatSurfaceWarm = Color(0xFFFAFAFA)

val ChatTextPrimary = Color(0xFF0D0D0D)
val ChatTextStrong = Color(0xFF1A1A1A)
val ChatTextSecondary = Color(0xFF6E6E6E)
val ChatTextTertiary = Color(0xFF9B9B9B)

val ChatBorder = Color(0xFFE5E5E5)
val ChatBorderSoft = Color(0xFFEDEDED)

val ChatAccent = Color(0xFF10A37F)
val ChatAccentOn = Color(0xFFFFFFFF)
val ChatAccentHover = Color(0xFF0A7A5E)
val ChatSendButton = Color(0xFF2563EB)
val ChatSendButtonOn = Color(0xFFFFFFFF)

val ChatSuccess = Color(0xFF10A37F)
val ChatWarning = Color(0xFFF5A623)
val ChatDanger = Color(0xFFEF4146)

val ChatDarkBackground = Color(0xFF0D0D0D)
val ChatDarkSurface = Color(0xFF1A1A1A)
val ChatDarkSurfaceVariant = Color(0xFF2A2A2A)
val ChatDarkTextPrimary = Color(0xFFF5F5F5)
val ChatDarkTextSecondary = Color(0xFFB8B8B8)
val ChatDarkBorder = Color(0xFF3A3A3A)

val ChatLightColorScheme = lightColorScheme(
    primary = ChatAccent,
    onPrimary = ChatAccentOn,
    background = ChatBackground,
    onBackground = ChatTextPrimary,
    surface = ChatBackground,
    onSurface = ChatTextPrimary,
    surfaceVariant = ChatSurface,
    onSurfaceVariant = ChatTextSecondary,
    outline = ChatBorder,
    outlineVariant = ChatBorderSoft,
    error = ChatDanger,
    onError = Color.White
)

val ChatDarkColorScheme = darkColorScheme(
    primary = ChatAccent,
    onPrimary = ChatAccentOn,
    background = ChatDarkBackground,
    onBackground = ChatDarkTextPrimary,
    surface = ChatDarkSurface,
    onSurface = ChatDarkTextPrimary,
    surfaceVariant = ChatDarkSurfaceVariant,
    onSurfaceVariant = ChatDarkTextSecondary,
    outline = ChatDarkBorder,
    outlineVariant = ChatDarkSurfaceVariant,
    error = ChatDanger,
    onError = Color.White
)
