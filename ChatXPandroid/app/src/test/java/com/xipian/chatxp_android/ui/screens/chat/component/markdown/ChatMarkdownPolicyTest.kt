package com.xipian.chatxp_android.ui.screens.chat.component.markdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownPolicyTest {
    @Test
    fun allowsHttpAndHttpsLinks() {
        assertTrue(isSafeMarkdownUri("https://developer.android.com"))
        assertTrue(isSafeMarkdownUri("HTTP://example.com/path?q=compose"))
    }

    @Test
    fun rejectsUnsafeAndRelativeLinks() {
        assertFalse(isSafeMarkdownUri("javascript:alert(1)"))
        assertFalse(isSafeMarkdownUri("file:///tmp/message.md"))
        assertFalse(isSafeMarkdownUri("intent://example.com"))
        assertFalse(isSafeMarkdownUri("/relative/path"))
        assertFalse(isSafeMarkdownUri("https:///missing-host"))
    }

    @Test
    fun extractsImageAltFromParsedNodeRange() {
        val content = "before ![Compose diagram](https://example.com/image.png) after"
        assertTrue(
            extractImageAlt(content, startOffset = 7, endOffset = content.length - 6) ==
                "Compose diagram"
        )
    }
}
