package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.token.EmptyStatePadding
import com.xipian.chatxp_android.ui.token.EmptyStateTextMaxWidth
import com.xipian.chatxp_android.ui.token.EmptyStateTitleBottomMargin

@Composable
fun EmptyChatState(
    modifier: Modifier = Modifier,
    emptyTitle: String = stringResource(R.string.chat_empty_title),
    emptyDescription: String = stringResource(R.string.chat_empty_description)
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(EmptyStatePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = emptyTitle,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = EmptyStateTitleBottomMargin)
        )
        Text(
            text = emptyDescription,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = EmptyStateTextMaxWidth)
        )
    }
}

@Preview(name = "Empty state light", widthDp = 360, heightDp = 640, showBackground = true, showSystemUi = true)
@Composable
private fun EmptyChatStateLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        EmptyChatState()
    }
}

@Preview(name = "Empty state dark", widthDp = 360, heightDp = 640, showBackground = true, showSystemUi = true)
@Composable
private fun EmptyChatStateDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        EmptyChatState()
    }
}
