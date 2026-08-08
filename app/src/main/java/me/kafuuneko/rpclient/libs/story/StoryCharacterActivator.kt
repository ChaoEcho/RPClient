package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterCandidate

/** Story 候选角色在本轮被激活的原因。 */
enum class StoryCharacterActivationReason {
    Always,
    Name,
    Alias
}

/** 已激活的角色卡及其匹配依据。 */
data class ActiveStoryCharacter(
    val candidate: StoryCharacterCandidate,
    val reason: StoryCharacterActivationReason,
    val matchedKey: String? = null
)

/** 按 Story 配置和本轮固定扫描文本确定要注入的角色卡。 */
class StoryCharacterActivator {
    fun activate(
        candidates: List<StoryCharacterCandidate>,
        scanText: String
    ): List<ActiveStoryCharacter> {
        return candidates
            .sortedWith(compareBy({ it.relation.sortOrder }, { it.character.id }))
            .mapNotNull { candidate -> activate(candidate, scanText) }
    }

    private fun activate(
        candidate: StoryCharacterCandidate,
        scanText: String
    ): ActiveStoryCharacter? {
        if (candidate.relation.activationMode == StoryCharacter.ACTIVATION_ALWAYS) {
            return ActiveStoryCharacter(candidate, StoryCharacterActivationReason.Always)
        }
        val name = candidate.character.name.trim()
        if (name.isNotEmpty() && scanText.matchesStoryKey(name)) {
            return ActiveStoryCharacter(candidate, StoryCharacterActivationReason.Name, name)
        }
        val alias = candidate.activationKeys
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .firstOrNull { scanText.matchesStoryKey(it) }
            ?: return null
        return ActiveStoryCharacter(candidate, StoryCharacterActivationReason.Alias, alias)
    }

    private fun String.matchesStoryKey(key: String): Boolean {
        val normalizedKey = key.trim()
        if (normalizedKey.isEmpty()) return false
        if (!normalizedKey.requiresWordBoundary()) {
            return contains(normalizedKey, ignoreCase = true)
        }
        return Regex(
            pattern = "(?<![\\p{L}\\p{N}_])${Regex.escape(normalizedKey)}(?![\\p{L}\\p{N}_])",
            options = setOf(RegexOption.IGNORE_CASE)
        ).containsMatchIn(this)
    }

    private fun String.requiresWordBoundary(): Boolean {
        return all { character ->
            character.isWhitespace() || character == '-' || character == '_' ||
                (character.code < 128 && character.isLetterOrDigit())
        } && any { it.isLetterOrDigit() }
    }
}
