package me.kafuuneko.rpclient.libs.groupchat

import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.prompt.selectSummaryPrefix
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatSummaryPrefixSelectionTest {
    @Test
    fun largeGroupHistoryKeepsContinuousPrefixWithFewCompleteCounts() = runBlocking {
        val messages = List(1_000) { index ->
            GroupChatMessage(
                id = index + 1L,
                sessionId = 1L,
                createTime = index.toLong(),
                source = GroupChatMessage.Source.Character,
                content = "中文群聊消息 $index",
                speakerCharacterId = 2L,
                speakerNameSnapshot = "Char"
            )
        }
        var itemEstimateCalls = 0
        var completeRequestCalls = 0

        val selected = selectSummaryPrefix(
            items = messages,
            promptBudget = 2_510,
            baseTokenEstimate = 10,
            estimateItemTokens = {
                itemEstimateCalls += 1
                5
            },
            countPrefixTokens = { prefixSize ->
                completeRequestCalls += 1
                10 + prefixSize * 5
            }
        )

        assertEquals(messages.take(500), selected)
        assertEquals((1L..500L).toList(), selected.map { it.id })
        assertEquals(1_000, itemEstimateCalls)
        assertTrue("full prompt counted $completeRequestCalls times", completeRequestCalls <= 3)
    }
}
