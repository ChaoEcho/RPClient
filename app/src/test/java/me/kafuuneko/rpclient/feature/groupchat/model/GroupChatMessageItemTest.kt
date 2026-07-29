package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatMessageSource

import me.kafuuneko.rpclient.ui.message.MessageContentPart
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChatMessageItemTest {
    @Test
    fun buildsTextAndReasoningPartsFromContent() {
        val item = GroupChatMessageItem(
            id = 7,
            source = GroupChatMessageSource.Character,
            speakerName = "Lyra",
            content = "hello<think>reasoning</think>world",
            time = "12:00"
        )

        assertEquals(
            listOf(
                MessageContentPart.Text("hello"),
                MessageContentPart.Think("7:0", "reasoning"),
                MessageContentPart.Text("world")
            ),
            item.parts
        )
    }
}
