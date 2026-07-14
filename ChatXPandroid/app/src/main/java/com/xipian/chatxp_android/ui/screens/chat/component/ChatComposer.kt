package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.theme.ChatSendButton
import com.xipian.chatxp_android.ui.theme.ChatSendButtonOn
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme
import com.xipian.chatxp_android.ui.token.BorderWidth
import com.xipian.chatxp_android.ui.token.ComposerBottomPadding
import com.xipian.chatxp_android.ui.token.ComposerButtonSize
import com.xipian.chatxp_android.ui.token.ComposerHorizontalPadding
import com.xipian.chatxp_android.ui.token.ComposerShellInnerPadding
import com.xipian.chatxp_android.ui.token.ComposerShellMinHeight
import com.xipian.chatxp_android.ui.token.ComposerTextHorizontalPadding
import com.xipian.chatxp_android.ui.token.ComposerTextMaxHeight
import com.xipian.chatxp_android.ui.token.ComposerTextMinHeight
import com.xipian.chatxp_android.ui.token.ComposerTopPadding
import com.xipian.chatxp_android.ui.token.DisabledAlpha
import com.xipian.chatxp_android.ui.token.IconSize
import com.xipian.chatxp_android.ui.token.SendButtonSize

@Composable
fun ChatComposer(
    modifier: Modifier = Modifier,
    composerText: String = "",
    onComposerTextChange: (String) -> Unit = {},
    composerPlaceholder: String = stringResource(R.string.chat_composer_placeholder),
    isEnabled: Boolean = true,
    isSendEnabled: Boolean = true,
    onAttachClick: () -> Unit = {},
    onSendClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ComposerHorizontalPadding,
                    top = ComposerTopPadding,
                    end = ComposerHorizontalPadding,
                    bottom = ComposerBottomPadding
                )
                .heightIn(min = ComposerShellMinHeight),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(BorderWidth, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ComposerShellInnerPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ComposerIconButton(
                    icon = painterResource(R.drawable.plus),
                    contentDescription = stringResource(R.string.icon_add),
                    enabled = isEnabled,
                    onClick = onAttachClick
                )
                BasicTextField(
                    value = composerText,
                    onValueChange = onComposerTextChange,
                    enabled = isEnabled,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(
                            min = ComposerTextMinHeight,
                            max = ComposerTextMaxHeight
                        )
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = ComposerTextHorizontalPadding),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (composerText.isEmpty()) {
                                Text(
                                    text = composerPlaceholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                SendIconButton(
                    enabled = isEnabled && isSendEnabled && composerText.isNotBlank(),
                    onClick = onSendClick
                )
            }
        }
    }
}

@Composable
private fun ComposerIconButton(
    icon: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(ComposerButtonSize)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize)
        )
    }
}

@Composable
private fun SendIconButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(SendButtonSize)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = ChatSendButton,
        contentColor = ChatSendButtonOn
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.send),
                contentDescription = stringResource(R.string.icon_send),
                tint = ChatSendButtonOn,
                modifier = Modifier.size(IconSize)
            )
        }
    }
}

@Preview(name = "Composer light", showBackground = true, showSystemUi = true)
@Composable
private fun ChatComposerLightPreview() {
    ChatComposerPreview(darkTheme = false)
}

@Preview(name = "Composer dark", showBackground = true, showSystemUi = true)
@Composable
private fun ChatComposerDarkPreview() {
    ChatComposerPreview(darkTheme = true)
}

@Composable
private fun ChatComposerPreview(darkTheme: Boolean) {
    var composerText by rememberSaveable { mutableStateOf("") }

    ChatXPandroidTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.BottomCenter
            ) {
                ChatComposer(
                    composerText = composerText,
                    onComposerTextChange = { composerText = it }
                )
            }
        }
    }
}
