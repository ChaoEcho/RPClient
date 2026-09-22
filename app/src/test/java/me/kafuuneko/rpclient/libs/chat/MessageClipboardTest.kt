package me.kafuuneko.rpclient.libs.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageClipboardTest {
    @Test
    fun hidesClosedAndUnclosedReasoningWithoutChangingOtherWhitespace() {
        assertEquals("before\n\nafter", messageClipboardText("before\n<THINK>hidden</THINK>\nafter", false))
        assertEquals("visible\n", messageClipboardText("visible\n<think>unfinished", false))
        assertEquals("", messageClipboardText("<think>reasoning only", false))
    }

    @Test
    fun preservesRawTextWhenReasoningIsExplicitlyIncluded() {
        val content = "  <think>reasoning</think>\nbody  "
        assertEquals(content, messageClipboardText(content, true))
        assertEquals("  body  ", messageClipboardText("  body  ", false))
    }
}
