package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.prompt.PromptTokenizer

/** 一段可独立预算和追踪来源的连续正文上下文。 */
data class StoryContextChunk(
    val content: String,
    val start: Int,
    val end: Int,
    val distance: Int,
    val required: Boolean
)

/** 正文裁剪结果及其世界书、角色激活扫描文本。 */
data class StoryContextSelection(
    val activationScanText: String,
    val worldBookScanText: String,
    val chunks: List<StoryContextChunk>,
    val omittedChunkCount: Int = 0,
    val omittedTokenCount: Int = 0
)

/** 将连续正文临时拆成邻近目标的 Prompt 块，不改变正文持久化结构。 */
class StoryContextSelector {
    fun select(
        content: String,
        target: StoryEditTarget,
        authorNote: String,
        tokenizer: PromptTokenizer,
        promptBudget: Int,
        continuationGuidance: String = ""
    ): StoryContextSelection {
        require(target.end == content.length) {
            "Story continuation target must be at the end of the document"
        }
        val paragraphs = paragraphRanges(content)
        val neighboring = paragraphs
            .map { range ->
                val distance = target.start - range.end
                range to distance
            }
            .sortedWith(compareBy<Pair<TextRange, Int>> { it.second }.thenBy { it.first.start })
        val activationRanges = neighboring.take(ACTIVATION_PARAGRAPH_COUNT).map { it.first }
        val worldBookScanText = buildList {
            activationRanges.sortedBy { it.start }.forEach { add(content.substring(it.start, it.end)) }
        }.joinToString("\n\n")
        val activationScanText = buildList {
            worldBookScanText.takeIf(String::isNotBlank)?.let(::add)
            authorNote.takeIf(String::isNotBlank)?.let(::add)
            continuationGuidance.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString("\n\n")

        val maxChunkTokens = (promptBudget / 4).coerceIn(MIN_CHUNK_TOKENS, MAX_CHUNK_TOKENS)
        val chunks = neighboring.flatMap { (range, _) ->
            splitRange(content, range, tokenizer, maxChunkTokens).map { chunk ->
                CandidateChunk(
                    range = chunk,
                    distance = (target.start - chunk.end).coerceAtLeast(0),
                    tokenCount = tokenizer.countText(content.substring(chunk.start, chunk.end))
                )
            }
        }.sortedWith(compareBy<CandidateChunk> { it.distance }.thenBy { it.range.start })
        val selected = mutableListOf<CandidateChunk>()
        val omitted = mutableListOf<CandidateChunk>()
        var usedTokens = 0
        chunks.forEachIndexed { index, chunk ->
            if (
                index == 0 || (
                    selected.size < MAX_SELECTED_CHUNKS &&
                        usedTokens + chunk.tokenCount <= promptBudget.coerceAtLeast(1)
                    )
            ) {
                selected += chunk
                usedTokens += chunk.tokenCount
            } else {
                omitted += chunk
            }
        }
        val nearest = selected.firstOrNull()
        return StoryContextSelection(
            activationScanText = activationScanText,
            worldBookScanText = worldBookScanText,
            chunks = selected
                .map { candidate ->
                    val range = candidate.range
                    StoryContextChunk(
                        content = content.substring(range.start, range.end),
                        start = range.start,
                        end = range.end,
                        distance = candidate.distance,
                        required = range == nearest?.range
                    )
                }
                .sortedBy { it.start },
            omittedChunkCount = omitted.size,
            omittedTokenCount = omitted.sumOf { it.tokenCount }
        )
    }

    private fun paragraphRanges(content: String): List<TextRange> {
        if (content.isEmpty()) return emptyList()
        val result = mutableListOf<TextRange>()
        var start = 0
        PARAGRAPH_SEPARATOR.findAll(content).forEach { match ->
            val end = match.range.first
            if (end > start) result += TextRange(start, end)
            start = match.range.last + 1
        }
        if (start < content.length) result += TextRange(start, content.length)
        return result.filter { content.substring(it.start, it.end).isNotBlank() }
    }

    private fun splitRange(
        content: String,
        range: TextRange,
        tokenizer: PromptTokenizer,
        maxTokens: Int
    ): List<TextRange> {
        if (tokenizer.countText(content.substring(range.start, range.end)) <= maxTokens) {
            return listOf(range)
        }
        val result = mutableListOf<TextRange>()
        var start = range.start
        while (start < range.end) {
            var low = start + 1
            var high = range.end
            var best = low
            while (low <= high) {
                val middle = (low + high) ushr 1
                val safeMiddle = content.safeUtf16Boundary(middle, start, range.end)
                if (tokenizer.countText(content.substring(start, safeMiddle)) <= maxTokens) {
                    best = safeMiddle
                    low = middle + 1
                } else {
                    high = middle - 1
                }
            }
            val end = content.safeUtf16Boundary(best.coerceAtLeast(start + 1), start, range.end)
            result += TextRange(start, end)
            start = end
        }
        return result
    }

    private fun String.safeUtf16Boundary(index: Int, minimum: Int, maximum: Int): Int {
        var safe = index.coerceIn(minimum + 1, maximum)
        if (safe < maximum && safe > minimum && this[safe].isLowSurrogate() && this[safe - 1].isHighSurrogate()) {
            safe = if (safe - 1 > minimum) safe - 1 else (safe + 1).coerceAtMost(maximum)
        }
        return safe.coerceAtLeast(minimum + 1)
    }

    private data class TextRange(val start: Int, val end: Int)

    private data class CandidateChunk(
        val range: TextRange,
        val distance: Int,
        val tokenCount: Int
    )

    private companion object {
        val PARAGRAPH_SEPARATOR = Regex("(?:\\r?\\n){2,}")
        const val ACTIVATION_PARAGRAPH_COUNT = 6
        const val MIN_CHUNK_TOKENS = 128
        const val MAX_CHUNK_TOKENS = 768
        const val MAX_SELECTED_CHUNKS = 512
    }
}
