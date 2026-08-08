package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterCandidate
import java.security.MessageDigest

/** 一次续写要插入的 UTF-16 正文位置。 */
data class StoryEditTarget(
    val start: Int,
    val end: Int
) {
    init {
        require(start >= 0 && end == start) { "Story continuation target must be a cursor" }
    }

    fun originalText(content: String): String {
        require(end == content.length) { "Story continuation target must be at document end" }
        return content.substring(start, end)
    }
}

/** 构建一轮故事写作 Prompt 所需的完整、只读输入。 */
data class StoryPromptContext(
    val story: Story,
    val characterCandidates: List<StoryCharacterCandidate>,
    val target: StoryEditTarget,
    val sourceContent: String,
    val provider: LLMProvider,
    val candidateLorebookEntries: List<LorebookEntry>,
    val candidateLorebooks: Map<Long, Lorebook>,
    val recursiveScanningLorebookIds: Set<Long>,
    val continuationGuidance: String = ""
)

/** 对正文目标做稳定校验时使用的 SHA-256。 */
fun storyTextHash(text: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
