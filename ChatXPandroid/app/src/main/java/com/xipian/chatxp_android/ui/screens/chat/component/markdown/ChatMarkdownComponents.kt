package com.xipian.chatxp_android.ui.screens.chat.component.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.xipian.chatxp_android.R

@Composable
internal fun chatMarkdownComponents(): MarkdownComponents {
    val immediate = LocalInspectionMode.current

    return markdownComponents(
        codeFence = { model ->
            MarkdownHighlightedCodeFence(
                content = model.content,
                node = model.node,
                style = model.typography.code,
                showHeader = true,
                immediate = immediate
            )
        },
        codeBlock = { model ->
            MarkdownHighlightedCodeBlock(
                content = model.content,
                node = model.node,
                style = model.typography.code,
                showHeader = true,
                immediate = immediate
            )
        },
        image = { model ->
            extractImageAlt(
                content = model.content,
                startOffset = model.node.startOffset,
                endOffset = model.node.endOffset
            ).takeIf(String::isNotBlank)?.let { alt ->
                Text(
                    text = stringResource(R.string.markdown_image_alt, alt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        inlineImage = { }
    )
}

internal fun extractImageAlt(
    content: String,
    startOffset: Int,
    endOffset: Int
): String {
    val start = startOffset.coerceIn(0, content.length)
    val end = endOffset.coerceIn(start, content.length)
    val raw = content.substring(start, end)
    val labelStart = raw.indexOf("![")
    if (labelStart < 0) return ""

    val result = StringBuilder()
    var isEscaped = false
    for (character in raw.substring(labelStart + 2)) {
        when {
            isEscaped -> {
                result.append(character)
                isEscaped = false
            }
            character == '\\' -> isEscaped = true
            character == ']' -> return result.toString()
            else -> result.append(character)
        }
    }
    return result.toString()
}
