package me.kafuuneko.rpclient.libs.groupchat

import kotlin.random.Random
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GroupChatSpeakerSelectorTest {
    private val selector = GroupChatSpeakerSelector()

    @Test
    fun listStrategyReturnsEveryActiveMemberInOrder() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1),
            member(3, "Rowan", order = 2, muted = true)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.List),
            members = members,
            messages = emptyList(),
            activationText = "",
            isUserInput = false,
            manualCharacterId = null,
            random = Random(1)
        )

        assertEquals(listOf(1L, 2L), selected.map { it.character.id })
    }

    @Test
    fun pooledStrategyPrioritizesMemberWhoHasNotSpokenSinceUser() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )
        val messages = listOf(
            message(GroupChatMessage.Source.User, null, "You"),
            message(GroupChatMessage.Source.Character, 1, "Lyra")
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Pooled),
            members = members,
            messages = messages,
            activationText = "",
            isUserInput = false,
            manualCharacterId = null,
            random = Random(2)
        )

        assertEquals(2L, selected.single().character.id)
    }

    @Test
    fun naturalStrategyAlwaysActivatesMentionedCharacter() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = emptyList(),
            activationText = "Mina, check the archive.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(3)
        )

        assertEquals(true, selected.any { it.character.id == 2L })
    }

    @Test
    fun naturalStrategyRequiresAtAndTheCompleteCharacterNameForStrongMention() {
        val members = listOf(
            member(1, "Lyra", order = 0, talkativeness = 0.0),
            member(2, "Mina", order = 1, talkativeness = 1.0)
        )
        val session = session(
            GroupChatSession.ActivationStrategy.Natural,
            naturalMaxSpeakers = 1
        )

        val partialMention = selector.select(
            session = session,
            members = members,
            messages = emptyList(),
            activationText = "@Lyr, check the archive.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(32)
        )
        val fullMention = selector.select(
            session = session,
            members = members,
            messages = emptyList(),
            activationText = "@Lyra, check the archive.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(33)
        )

        assertFalse(partialMention.any { it.character.id == 1L })
        assertEquals(true, fullMention.any { it.character.id == 1L })
    }

    @Test
    fun naturalStrategyRecognizesCompleteCjkAndMultiWordAtMentions() {
        val members = listOf(
            member(1, "小洛", order = 0, talkativeness = 0.0),
            member(2, "Misaka Mikoto", order = 1, talkativeness = 0.0),
            member(3, "Rowan", order = 2, talkativeness = 1.0)
        )

        val selected = selector.select(
            session = session(
                GroupChatSession.ActivationStrategy.Natural,
                naturalMaxSpeakers = 1
            ),
            members = members,
            messages = emptyList(),
            activationText = "请 @小洛 和 @Misaka Mikoto 一起查看。",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(34)
        )

        assertEquals(listOf(1L, 2L), selected.map { it.character.id })
    }

    @Test
    fun naturalStrategyKeepsExplicitSpeakersAndCapsTotalSelection() {
        val members = listOf(
            member(1, "Lyra", order = 0, talkativeness = 1.0),
            member(2, "Mina", order = 1, talkativeness = 1.0),
            member(3, "Rowan", order = 2, talkativeness = 1.0),
            member(4, "Sora", order = 3, talkativeness = 1.0)
        )

        val automatic = selector.select(
            session = session(
                GroupChatSession.ActivationStrategy.Natural,
                naturalMaxSpeakers = 2
            ),
            members = members,
            messages = emptyList(),
            activationText = "Let's discuss this.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(35)
        )
        val explicit = selector.select(
            session = session(
                GroupChatSession.ActivationStrategy.Natural,
                naturalMaxSpeakers = 1
            ),
            members = members,
            messages = emptyList(),
            activationText = "@Lyra @Mina @Rowan",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(36)
        )

        assertEquals(2, automatic.size)
        assertEquals(listOf(1L, 2L, 3L), explicit.map { it.character.id })
    }

    @Test
    fun naturalStrategyNormalizesSpeakerLimitBoundaries() {
        val members = listOf(
            member(1, "Lyra", order = 0, talkativeness = 1.0),
            member(2, "Mina", order = 1, talkativeness = 1.0),
            member(3, "Rowan", order = 2, talkativeness = 1.0),
            member(4, "Sora", order = 3, talkativeness = 1.0)
        )

        fun selectedCount(limit: Int): Int = selector.select(
            session = session(
                GroupChatSession.ActivationStrategy.Natural,
                naturalMaxSpeakers = limit
            ),
            members = members,
            messages = emptyList(),
            activationText = "Let's discuss this.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(41)
        ).size

        assertEquals(2, selectedCount(0))
        assertEquals(2, selectedCount(99))
        assertEquals(3, selectedCount(3))
        assertEquals(4, selectedCount(-1))
    }

    @Test
    fun naturalStrategyPreservesReplyTargetBeyondAutomaticCap() {
        val members = listOf(
            member(1, "Lyra", order = 0, talkativeness = 0.0),
            member(2, "Mina", order = 1, talkativeness = 1.0)
        )

        val selected = selector.select(
            session = session(
                GroupChatSession.ActivationStrategy.Natural,
                naturalMaxSpeakers = 1
            ),
            members = members,
            messages = emptyList(),
            activationText = "A question for the group.",
            isUserInput = true,
            manualCharacterId = null,
            explicitCharacterIds = setOf(1L),
            random = Random(37)
        )

        assertEquals(listOf(1L), selected.map { it.character.id })
    }

    @Test
    fun naturalStrategyUsesRemainingCapacityAfterExplicitTarget() {
        val members = listOf(
            member(1, "Lyra", order = 0, talkativeness = 0.0),
            member(2, "Mina", order = 1, talkativeness = 1.0),
            member(3, "Rowan", order = 2, talkativeness = 1.0)
        )

        val selected = selector.select(
            session = session(
                GroupChatSession.ActivationStrategy.Natural,
                naturalMaxSpeakers = 2
            ),
            members = members,
            messages = emptyList(),
            activationText = "A question for the group.",
            isUserInput = true,
            manualCharacterId = null,
            explicitCharacterIds = setOf(1L),
            random = Random(40)
        )

        assertEquals(2, selected.size)
        assertEquals(1L, selected.first().character.id)
    }

    @Test
    fun naturalStrategyMatchesNameTokensWithoutSubstringFalsePositives() {
        val members = listOf(
            member(1, "Ann", order = 0),
            member(2, "Misaka Mikoto", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = emptyList(),
            activationText = "Misaka should inspect the announcement.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(8)
        )

        assertEquals(true, selected.any { it.character.id == 2L })
    }

    @Test
    fun naturalStrategyRecognizesCjkNameInsideSentence() {
        val members = listOf(
            member(1, "小洛", order = 0, talkativeness = 0.0),
            member(2, "大列巴", order = 1, talkativeness = 1.0)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = emptyList(),
            activationText = "让小洛回答这个问题。",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(9)
        )

        assertEquals(true, selected.any { it.character.id == 1L })
    }

    @Test
    fun manualStrategyWithUserInputRequiresAnExplicitActiveSpeaker() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Manual),
            members = members,
            messages = emptyList(),
            activationText = "Question",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(4)
        )

        assertEquals(emptyList<GroupChatMemberData>(), selected)
    }

    @Test
    fun manualStrategyWithEmptyInputSelectsOneRandomSpeaker() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Manual),
            members = members,
            messages = emptyList(),
            activationText = "",
            isUserInput = false,
            manualCharacterId = null,
            random = Random(4)
        )

        assertEquals(1, selected.size)
        assertEquals(true, selected.single() in members)
    }

    @Test
    fun manualStrategyIgnoresExplicitTargets() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Manual),
            members = members,
            messages = emptyList(),
            activationText = "@Mina",
            isUserInput = true,
            manualCharacterId = 1L,
            explicitCharacterIds = setOf(2L),
            random = Random(38)
        )

        assertEquals(listOf(1L), selected.map { it.character.id })
    }

    @Test
    fun pooledStrategyPrioritizesExplicitReplyTargetBeforePoolAlgorithm() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Pooled),
            members = members,
            messages = listOf(
                message(GroupChatMessage.Source.User, null, "You"),
                message(GroupChatMessage.Source.Character, 1, "Lyra")
            ),
            activationText = "No explicit text",
            isUserInput = false,
            manualCharacterId = null,
            explicitCharacterIds = setOf(1L),
            random = Random(39)
        )

        assertEquals(1L, selected.single().character.id)
    }

    @Test
    fun naturalStrategyAllowsUserToMentionPreviousSpeaker() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = listOf(
                message(GroupChatMessage.Source.Character, 1, "Lyra")
            ),
            activationText = "Lyra, answer this.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(5)
        )

        assertEquals(true, selected.any { it.character.id == 1L })
    }

    @Test
    fun pooledStrategyAvoidsImmediateRepeatAfterEveryoneSpoke() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )
        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Pooled),
            members = members,
            messages = listOf(
                message(GroupChatMessage.Source.User, null, "You"),
                message(GroupChatMessage.Source.Character, 1, "Lyra"),
                message(GroupChatMessage.Source.Character, 2, "Mina")
            ),
            activationText = "",
            isUserInput = false,
            manualCharacterId = null,
            random = Random(6)
        )

        assertEquals(1L, selected.single().character.id)
    }

    private fun session(
        strategy: GroupChatSession.ActivationStrategy,
        naturalMaxSpeakers: Int = 2
    ): GroupChatSession {
        return GroupChatSession(
            id = 1,
            title = "Group",
            createTime = 1,
            latestTime = 1,
            userName = "You",
            userDescription = "",
            activationStrategy = strategy,
            naturalMaxSpeakers = naturalMaxSpeakers
        )
    }

    private fun member(
        id: Long,
        name: String,
        order: Int,
        muted: Boolean = false,
        talkativeness: Double? = null
    ): GroupChatMemberData {
        return GroupChatMemberData(
            relation = GroupChatMember(
                sessionId = 1,
                characterId = id,
                sortOrder = order,
                muted = muted
            ),
            character = character(id, name, talkativeness)
        )
    }

    private fun character(
        id: Long,
        name: String,
        talkativeness: Double? = null
    ): Character {
        return Character(
            id = id,
            name = name,
            avatar = "",
            characterTags = "[]",
            description = "",
            personality = "",
            scenario = "",
            firstMessages = "",
            examplesOfDialogue = "",
            postHistoryInstructions = "",
            extensionsJson = talkativeness
                ?.let { """{"talkativeness":$it}""" }
                ?: "{}"
        )
    }

    private fun message(
        source: GroupChatMessage.Source,
        speakerId: Long?,
        speakerName: String
    ): GroupChatMessage {
        return GroupChatMessage(
            id = (speakerId ?: 10L),
            sessionId = 1,
            createTime = 1,
            source = source,
            content = "Message",
            speakerCharacterId = speakerId,
            speakerNameSnapshot = speakerName
        )
    }
}
