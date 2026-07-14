package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatComponentPreview
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.token.BorderWidth
import com.xipian.chatxp_android.ui.token.ComposerTextMinHeight
import com.xipian.chatxp_android.ui.token.DrawerSearchBottomMargin
import com.xipian.chatxp_android.ui.token.DrawerSearchHorizontalMargin
import com.xipian.chatxp_android.ui.token.IconButtonSize
import com.xipian.chatxp_android.ui.token.Space3

@Composable
fun ThreadSearchField(
    searchQuery: String,
    placeholder: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DrawerSearchHorizontalMargin,
                end = DrawerSearchHorizontalMargin,
                bottom = DrawerSearchBottomMargin
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(BorderWidth, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = IconButtonSize)
                .padding(start = Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ComposerTextMinHeight),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(IconButtonSize)
                        .clickable(onClick = onClearSearchClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.icon_clear),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@ChatComponentPreview
@Composable
private fun ThreadSearchFieldPreview() {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    ChatPreviewFrame(contentAlignment = Alignment.TopCenter) {
        ThreadSearchField(
            searchQuery = searchQuery,
            placeholder = stringResource(R.string.drawer_search_placeholder),
            onSearchQueryChange = { searchQuery = it },
            onClearSearchClick = { searchQuery = "" },
            modifier = Modifier.padding(top = Space3)
        )
    }
}
