package com.xipian.chatxp_android.ui.screens.chat.component.markdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownDimens
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import com.xipian.chatxp_android.ui.token.MarkdownBlockSpacing
import com.xipian.chatxp_android.ui.token.MarkdownCodeHorizontalPadding
import com.xipian.chatxp_android.ui.token.MarkdownCodeVerticalPadding
import com.xipian.chatxp_android.ui.token.MarkdownCornerSize
import com.xipian.chatxp_android.ui.token.MarkdownDividerThickness
import com.xipian.chatxp_android.ui.token.MarkdownListIndent
import com.xipian.chatxp_android.ui.token.MarkdownListSpacing
import com.xipian.chatxp_android.ui.token.MarkdownQuoteBorderWidth
import com.xipian.chatxp_android.ui.token.MarkdownQuoteHorizontalPadding
import com.xipian.chatxp_android.ui.token.MarkdownQuoteVerticalPadding
import com.xipian.chatxp_android.ui.token.MarkdownTableCellPadding
import com.xipian.chatxp_android.ui.token.MarkdownTableCellWidth
import com.xipian.chatxp_android.ui.token.MarkdownTableMaxWidth

@Composable
internal fun chatMarkdownColors(): MarkdownColors = markdownColor(
    text = MaterialTheme.colorScheme.onSurface,
    codeBackground = MaterialTheme.colorScheme.surfaceVariant,
    inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant,
    dividerColor = MaterialTheme.colorScheme.outlineVariant,
    tableBackground = MaterialTheme.colorScheme.surface
)

@Composable
internal fun chatMarkdownTypography(): MarkdownTypography {
    val body = MaterialTheme.typography.bodyMedium
    val code = body.copy(fontFamily = FontFamily.Monospace)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    )

    return markdownTypography(
        h1 = MaterialTheme.typography.titleLarge,
        h2 = MaterialTheme.typography.titleMedium,
        h3 = MaterialTheme.typography.titleSmall,
        h4 = MaterialTheme.typography.titleSmall,
        h5 = MaterialTheme.typography.titleSmall,
        h6 = MaterialTheme.typography.titleSmall,
        text = body,
        code = code,
        inlineCode = code,
        quote = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        textLink = linkStyles,
        table = body
    )
}

@Composable
internal fun chatMarkdownPadding(): MarkdownPadding = markdownPadding(
    block = MarkdownBlockSpacing,
    list = MarkdownListSpacing,
    listItemTop = MarkdownListSpacing,
    listItemBottom = MarkdownListSpacing,
    listIndent = MarkdownListIndent,
    codeBlock = PaddingValues(
        horizontal = MarkdownCodeHorizontalPadding,
        vertical = MarkdownCodeVerticalPadding
    ),
    blockQuote = PaddingValues(vertical = MarkdownQuoteVerticalPadding),
    blockQuoteText = PaddingValues(horizontal = MarkdownQuoteHorizontalPadding),
    blockQuoteBar = PaddingValues.Absolute(right = MarkdownQuoteHorizontalPadding)
)

@Composable
internal fun chatMarkdownDimens(): MarkdownDimens = markdownDimens(
    dividerThickness = MarkdownDividerThickness,
    codeBackgroundCornerSize = MarkdownCornerSize,
    blockQuoteThickness = MarkdownQuoteBorderWidth,
    tableMaxWidth = MarkdownTableMaxWidth,
    tableCellWidth = MarkdownTableCellWidth,
    tableCellPadding = MarkdownTableCellPadding,
    tableCornerSize = MarkdownCornerSize
)
