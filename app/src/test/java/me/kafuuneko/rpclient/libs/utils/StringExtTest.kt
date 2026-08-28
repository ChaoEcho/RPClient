package me.kafuuneko.rpclient.libs.utils

import me.kafuuneko.rpclient.utils.stripThinkBlocks
import org.junit.Assert.assertEquals
import org.junit.Test

class StringExtTest {
    @Test
    fun stripThinkBlocks_removesClosedThinkBlock() {
        val content = "<think>\nmodel reasoning\n</think>\n\nactual reply"

        assertEquals("actual reply", content.stripThinkBlocks())
    }

    @Test
    fun stripThinkBlocks_removesUnclosedThinkBlock() {
        val content = "actual reply\n<think>\nunpersisted reasoning"

        assertEquals("actual reply", content.stripThinkBlocks())
    }

    @Test
    fun stripThinkBlocks_removesMultipleThinkBlocksCaseInsensitively() {
        val content = "<THINK>first</THINK>\nhello\n<think>second</think>\nworld"

        assertEquals("hello\n\nworld", content.stripThinkBlocks())
    }
}
