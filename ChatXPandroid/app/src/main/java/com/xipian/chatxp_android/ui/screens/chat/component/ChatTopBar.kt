package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme
import com.xipian.chatxp_android.ui.token.AppBarActionAreaWidth
import com.xipian.chatxp_android.ui.token.AppBarHeight
import com.xipian.chatxp_android.ui.token.AppBarHorizontalPadding
import com.xipian.chatxp_android.ui.token.BorderWidth
import com.xipian.chatxp_android.ui.token.IconButtonSize
import com.xipian.chatxp_android.ui.token.IconSize
import com.xipian.chatxp_android.ui.token.Space1
import com.xipian.chatxp_android.ui.token.Space2

@Composable
fun ChatTopBar(
    modelName: String,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
    onModelClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(AppBarHeight),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(BorderWidth, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppBarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StaticIconButton(
                    icon = painterResource(R.drawable.menu),
                    contentDescription = stringResource(R.string.icon_menu),
                    onClick = onMenuClick
                )
                Surface(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .height(IconButtonSize)
                        .clickable(onClick = onModelClick),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = Space2, vertical = Space1),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = modelName,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.width(AppBarActionAreaWidth),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row {
                        StaticIconButton(
                            icon = painterResource(R.drawable.new_session),
                            contentDescription = stringResource(R.string.icon_add),
                            onClick = onNewChatClick,
                            showBackground = false
                        )
                        StaticIconButton(
                            icon = painterResource(R.drawable.more),
                            contentDescription = stringResource(R.string.icon_more),
                            onClick = onMoreClick,
                            showBackground = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true
) {
    Surface(
        modifier = modifier
            .size(IconButtonSize)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (showBackground) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            Color.Transparent
        },
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(
    name = "Top bar light",
    widthDp = 360,
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun ChatTopBarLightPreview() {
    ChatTopBarPreview(darkTheme = false)
}

@Preview(
    name = "Top bar dark",
    widthDp = 360,
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun ChatTopBarDarkPreview() {
    ChatTopBarPreview(darkTheme = true)
}

@Composable
private fun ChatTopBarPreview(darkTheme: Boolean) {
    ChatXPandroidTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            ChatTopBar(
                modelName = stringResource(R.string.chat_model_name),
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
