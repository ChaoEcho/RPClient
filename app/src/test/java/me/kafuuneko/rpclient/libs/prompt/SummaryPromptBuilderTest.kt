package me.kafuuneko.rpclient.libs.prompt

import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPromptBuilderTest {

    @Test
    fun selectionMatchesExactContinuousPrefixSemantics() = runBlocking {
        val messages = listOf("one", "two", "three")
        var exactCalls = 0

        val selected = selectSummaryPrefix(
            items = messages,
            promptBudget = 18,
            baseTokenEstimate = 10,
            estimateItemTokens = { it.length + 1 },
            countPrefixTokens = { prefixSize ->
                exactCalls += 1
                10 + messages.take(prefixSize).sumOf { it.length + 1 }
            }
        )

        assertEquals(listOf("one", "two"), selected)
        assertTrue(exactCalls <= 3)
    }

    @Test
    fun exactVerificationCorrectsAnInaccurateLightweightEstimate() = runBlocking {
        val messages = List(10) { "message-$it" }

        val selected = selectSummaryPrefix(
            items = messages,
            promptBudget = 22,
            baseTokenEstimate = 2,
            estimateItemTokens = { 1 },
            countPrefixTokens = { prefixSize -> 2 + prefixSize * 5 }
        )

        assertEquals(messages.take(4), selected)
    }

    @Test
    fun selectionRejectsFirstMessageWhenCompleteRequestExceedsBudget() = runBlocking {
        val selected = selectSummaryPrefix(
            items = listOf("oversized"),
            promptBudget = 8,
            baseTokenEstimate = 2,
            estimateItemTokens = { it.length },
            countPrefixTokens = { 9 }
        )

        assertEquals(emptyList<String>(), selected)
    }

    @Test
    fun largeSelectionUsesLinearItemEstimatesAndFewCompleteRequestCounts() = runBlocking {
        val messages = List(1_000) { "message-$it" }
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

        assertEquals(500, selected.size)
        assertEquals(messages.take(500), selected)
        assertEquals(1_000, itemEstimateCalls)
        assertTrue("full prompt counted $completeRequestCalls times", completeRequestCalls <= 3)
    }

    @Test
    fun longExistingSummaryOverheadStillKeepsExactBudgetBoundary() = runBlocking {
        val messages = List(20) { "中文长段消息$it" }
        val existingSummaryTokens = 1_200

        val selected = selectSummaryPrefix(
            items = messages,
            promptBudget = 1_260,
            baseTokenEstimate = existingSummaryTokens,
            estimateItemTokens = { 10 },
            countPrefixTokens = { prefixSize -> existingSummaryTokens + prefixSize * 10 }
        )

        assertEquals(messages.take(6), selected)
    }

    @Test
    fun formattedSummaryHistoryUsesTextWithoutLocalImageMetadata() {
        val imageUuid = "local-image-file-uuid-should-not-leave-device"
        val history = FormattedHistoryBuilder().build(
            messages = listOf(
                ChatMessage(
                    id = 1L,
                    sessionId = 1L,
                    createTime = 1L,
                    source = ChatMessage.Source.Char,
                    content = "Visible reply",
                    imageFileUuid = imageUuid
                )
            ),
            userName = "User",
            characterName = "Char"
        )
        val outbound = buildRawSummaryMessages("Summarize", "", history)
            .joinToString("\n") { it.content }

        assertTrue(outbound.contains("Visible reply"))
        assertFalse(outbound.contains(imageUuid))
        assertFalse(outbound.contains("data:image/"))
        assertFalse(outbound.contains("base64"))
    }

    @Test
    fun summaryContentAlwaysRemovesReasoningBlocks() {
        val content = "<think>private chain</think>\nVisible event"
            .summarySafeContent()

        assertEquals("Visible event", content)
        assertFalse(content.contains("private chain"))
    }

    @Test
    fun legacySummaryPositionsMigrateToCurrentSemantics() {
        assertEquals(
            SummaryInjectionPosition.AfterMain,
            SummaryInjectionPosition.default
        )
        assertEquals(
            SummaryInjectionPosition.None,
            SummaryInjectionPosition.fromPersistedValue(-1)
        )
        assertEquals(
            SummaryInjectionPosition.BeforeMain,
            SummaryInjectionPosition.fromPersistedValue(0)
        )
        assertEquals(
            SummaryInjectionPosition.AfterMain,
            SummaryInjectionPosition.fromPersistedValue(1)
        )
        assertEquals(
            SummaryInjectionPosition.InChat,
            SummaryInjectionPosition.fromPersistedValue(2)
        )
        assertEquals(
            SummaryInjectionPosition.InChat,
            SummaryInjectionPosition.fromPersistedValue(3)
        )
        assertEquals(
            SummaryInjectionPosition.default,
            SummaryInjectionPosition.fromPersistedValue(Int.MAX_VALUE)
        )
    }

    @Test
    fun rawSummaryUsesSystemInstructionAndUserMaterial() {
        val messages = buildRawSummaryMessages(
            instruction = "Summarize",
            existingSummary = "Earlier events",
            history = "User: New event"
        )

        assertEquals(
            listOf(LLMMessageRole.System, LLMMessageRole.User),
            messages.map { it.role }
        )
        assertEquals("Summarize", messages[0].content)
        assertEquals(
            "Existing summary:\nEarlier events\n\nChat history:\nUser: New event",
            messages[1].content
        )
    }

    @Test
    fun summaryCandidatesAlwaysExcludeLastMessage() {
        assertEquals(listOf("one", "two"), listOf("one", "two", "three").summaryCandidates(0))
        assertEquals(listOf("one"), listOf("one", "two", "three").summaryCandidates(1))
    }
}
