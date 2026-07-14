package com.xipian.chatxp_android.ui.screens.chat.component.markdown

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.markdown.compose.LocalMarkdownA11yLabels
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownA11yLabels
import com.mikepenz.markdown.model.rememberMarkdownState
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import java.net.URI
import java.util.Locale

@Composable
fun ChatMarkdown(
    content: String,
    modifier: Modifier = Modifier
) {
    if (content.isEmpty()) return

    val context = LocalContext.current
    val platformUriHandler = LocalUriHandler.current
    val safeUriHandler = remember(platformUriHandler) {
        SafeMarkdownUriHandler(platformUriHandler)
    }
    val labels = rememberMarkdownA11yLabels(context)
    val markdownState = rememberMarkdownState(
        content = content,
        retainState = true
    )
    val fallback: @Composable (Modifier) -> Unit = { fallbackModifier ->
        Text(
            text = content,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = fallbackModifier
        )
    }

    CompositionLocalProvider(
        LocalUriHandler provides safeUriHandler,
        LocalMarkdownA11yLabels provides labels
    ) {
        Markdown(
            markdownState = markdownState,
            colors = chatMarkdownColors(),
            typography = chatMarkdownTypography(),
            modifier = modifier,
            padding = chatMarkdownPadding(),
            dimens = chatMarkdownDimens(),
            components = chatMarkdownComponents(),
            loading = fallback,
            error = fallback
        )
    }
}

@Composable
private fun rememberMarkdownA11yLabels(context: Context): MarkdownA11yLabels {
    val blockquote = stringResource(R.string.markdown_blockquote)
    val codeBlock = stringResource(R.string.markdown_code_block)
    val fallbackLanguage = stringResource(R.string.markdown_code_language_default)
    val copyCode = stringResource(R.string.markdown_copy_code)

    return remember(context, blockquote, codeBlock, fallbackLanguage, copyCode) {
        MarkdownA11yLabels(
            blockquote = blockquote,
            codeBlock = codeBlock,
            codeBlockWithLanguage = { language ->
                context.getString(R.string.markdown_code_block_with_language, language)
            },
            codeLanguage = { language ->
                context.getString(R.string.markdown_code_language, language)
            },
            codeFallbackLanguage = fallbackLanguage,
            copyCode = copyCode
        )
    }
}

internal fun isSafeMarkdownUri(uri: String): Boolean = runCatching {
    val parsed = URI(uri.trim())
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    scheme in setOf("http", "https") && !parsed.host.isNullOrBlank()
}.getOrDefault(false)

private class SafeMarkdownUriHandler(
    private val delegate: UriHandler
) : UriHandler {
    override fun openUri(uri: String) {
        if (isSafeMarkdownUri(uri)) {
            runCatching { delegate.openUri(uri) }
        }
    }
}

@Preview(
    name = "Markdown light",
    widthDp = 360,
    heightDp = 640,
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun ChatMarkdownLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        ChatMarkdown(content = stringResource(R.string.chat_preview_markdown_assistant))
    }
}

@Preview(
    name = "Markdown dark",
    widthDp = 360,
    heightDp = 640,
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun ChatMarkdownDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        ChatMarkdown(content = stringResource(R.string.chat_preview_markdown_assistant))
    }
}
