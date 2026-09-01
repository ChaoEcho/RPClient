package me.kafuuneko.rpclient.ui.message

import me.kafuuneko.rpclient.utils.MarkdownBlock

/**
 * Adds display-only paragraph breaks without changing the source message or Markdown model.
 *
 * Paragraphs are split at complete sentence boundaries in groups of two or three sentences.
 * Inline code and URLs are skipped while looking for those boundaries so punctuation inside
 * either remains part of the same displayed paragraph.
 */
internal object MessageDisplayParagraphFormatter {
    private const val TARGET_SENTENCES_PER_PARAGRAPH = 3
    private const val MIN_SENTENCES_TO_SPLIT = 4
    private const val LONG_PARAGRAPH_LENGTH = 120

    private val sentenceTerminators = setOf('。', '！', '？', '!', '?')
    private val closingPunctuation = setOf(
        '"', '\'',
        '”', '’', '»',
        '）', ')', '］', ']', '｝', '}',
        '】', '〕', '〉', '》', '」', '』',
        '〗', '〙', '〛', '｠'
    )
    private val openingPunctuation = setOf('“', '‘', '「', '『', '（', '(', '【', '[', '{', '《', '<')
    private val urlClosingPunctuation = setOf(')', ']', '}', '>', '）', '］', '｝', '》', '】')

    /** Formats parsed blocks for display while preserving every non-paragraph block object. */
    fun format(blocks: List<MarkdownBlock>): List<MarkdownBlock> {
        if (blocks.isEmpty()) return emptyList()

        return buildList {
            blocks.forEach { block ->
                if (block is MarkdownBlock.Paragraph) {
                    addAll(formatParagraph(block.content))
                } else {
                    add(block)
                }
            }
        }
    }

    private fun formatParagraph(content: String): List<MarkdownBlock.Paragraph> {
        val protectedRanges = findProtectedRanges(content)
        val sentenceEnds = findSentenceEnds(content, protectedRanges)

        val hasManySentences = sentenceEnds.size >= MIN_SENTENCES_TO_SPLIT
        val hasLongThreeSentenceText = content.length >= LONG_PARAGRAPH_LENGTH &&
            sentenceEnds.size >= 3
        if (!hasManySentences && !hasLongThreeSentenceText) {
            return listOf(MarkdownBlock.Paragraph(content))
        }

        val segmentCount = if (hasManySentences) {
            (sentenceEnds.size + TARGET_SENTENCES_PER_PARAGRAPH - 1) /
                TARGET_SENTENCES_PER_PARAGRAPH
        } else {
            2
        }
        val sentencesPerSegment = sentenceEnds.size / segmentCount
        val segmentsWithExtraSentence = sentenceEnds.size % segmentCount
        val paragraphs = ArrayList<MarkdownBlock.Paragraph>(segmentCount)

        var sentenceIndex = 0
        var contentStart = 0
        repeat(segmentCount) { segmentIndex ->
            val sentenceCount = sentencesPerSegment +
                if (segmentIndex < segmentsWithExtraSentence) 1 else 0
            val contentEnd = sentenceEnds[sentenceIndex + sentenceCount - 1]
            val paragraph = content.substring(contentStart, contentEnd).trim()
            if (paragraph.isNotEmpty()) {
                paragraphs += MarkdownBlock.Paragraph(paragraph)
            }
            contentStart = contentEnd
            sentenceIndex += sentenceCount
        }

        val trailingContent = content.substring(contentStart).trim()
        if (trailingContent.isNotEmpty()) {
            if (paragraphs.isEmpty()) {
                paragraphs += MarkdownBlock.Paragraph(trailingContent)
            } else {
                val lastIndex = paragraphs.lastIndex
                paragraphs[lastIndex] = MarkdownBlock.Paragraph(
                    paragraphs[lastIndex].content + " " + trailingContent
                )
            }
        }

        return paragraphs.ifEmpty { listOf(MarkdownBlock.Paragraph(content)) }
    }

    private fun findSentenceEnds(
        source: String,
        protectedRanges: List<ProtectedRange>
    ): List<Int> {
        val sentenceEnds = mutableListOf<Int>()
        var index = 0
        var protectedRangeIndex = 0

        while (index < source.length) {
            val protectedRange = protectedRanges.getOrNull(protectedRangeIndex)
            if (protectedRange != null) {
                if (index < protectedRange.start) {
                    // Continue scanning normal text until this protected range begins.
                } else if (index < protectedRange.endExclusive) {
                    index = protectedRange.endExclusive
                    protectedRangeIndex += 1
                    continue
                } else {
                    protectedRangeIndex += 1
                    continue
                }
            }

            val character = source[index]
            val sentenceEnd = when {
                character == '.' -> englishSentenceEnd(source, index)
                character == '…' -> ellipsisSentenceEnd(source, index)
                character in sentenceTerminators -> punctuationSentenceEnd(source, index)
                else -> null
            }

            if (sentenceEnd != null) {
                sentenceEnds += sentenceEnd
                index = sentenceEnd
            } else {
                index += 1
            }
        }

        return sentenceEnds
    }

    private fun englishSentenceEnd(source: String, start: Int): Int? {
        if (isDecimalPoint(source, start)) return null

        var end = start + 1
        while (source.getOrNull(end) == '.') {
            end += 1
        }
        end = skipClosingPunctuation(source, end)
        return end.takeIf { hasSentenceBoundaryAfter(source, it) }
    }

    private fun ellipsisSentenceEnd(source: String, start: Int): Int? {
        var end = start + 1
        while (source.getOrNull(end) == '…') {
            end += 1
        }
        end = skipClosingPunctuation(source, end)
        return end.takeIf { hasSentenceBoundaryAfter(source, it) }
    }

    private fun punctuationSentenceEnd(source: String, start: Int): Int? {
        var end = start + 1
        end = skipClosingPunctuation(source, end)
        return end.takeIf { hasSentenceBoundaryAfter(source, it) }
    }

    private fun skipClosingPunctuation(source: String, start: Int): Int {
        var end = start
        while (source.getOrNull(end)?.let { it in closingPunctuation } == true) {
            end += 1
        }
        return end
    }

    private fun hasSentenceBoundaryAfter(source: String, end: Int): Boolean {
        val next = source.getOrNull(end) ?: return true
        return next.isWhitespace() ||
            next in openingPunctuation ||
            isCjk(next) ||
            !next.isLetterOrDigit()
    }

    private fun isDecimalPoint(source: String, index: Int): Boolean {
        return source.getOrNull(index - 1)?.isDigit() == true &&
            source.getOrNull(index + 1)?.isDigit() == true
    }

    private fun findProtectedRanges(source: String): List<ProtectedRange> {
        val ranges = mutableListOf<ProtectedRange>()
        var index = 0

        while (index < source.length) {
            if (source[index] == '`') {
                val closing = source.indexOf('`', index + 1)
                val endExclusive = if (closing == -1) source.length else closing + 1
                ranges += ProtectedRange(index, endExclusive)
                index = endExclusive
                continue
            }

            if (isUrlStart(source, index)) {
                var endExclusive = index
                while (endExclusive < source.length && isUrlCharacter(source[endExclusive])) {
                    endExclusive += 1
                }
                val urlEnd = trimUrlPunctuation(source, index, endExclusive)
                if (urlEnd > index) {
                    ranges += ProtectedRange(index, urlEnd)
                }
                index = maxOf(endExclusive, index + 1)
                continue
            }

            index += 1
        }

        return ranges
    }

    private fun isUrlStart(source: String, index: Int): Boolean {
        val startsWithScheme = source.regionMatches(index, "https://", 0, 8, ignoreCase = true) ||
            source.regionMatches(index, "http://", 0, 7, ignoreCase = true) ||
            source.regionMatches(index, "www.", 0, 4, ignoreCase = true)
        if (!startsWithScheme) return false

        val previous = source.getOrNull(index - 1)
        return previous == null || !previous.isLetterOrDigit()
    }

    private fun isUrlCharacter(character: Char): Boolean {
        return character.code in 0x21..0x7E && character !in "<>\"'`"
    }

    private fun trimUrlPunctuation(source: String, start: Int, endExclusive: Int): Int {
        var end = endExclusive
        while (end > start) {
            val character = source[end - 1]
            if (character in ".,!?:;" || character in urlClosingPunctuation) {
                end -= 1
            } else {
                break
            }
        }
        return end
    }

    private data class ProtectedRange(
        val start: Int,
        val endExclusive: Int
    )

    private fun isCjk(character: Char): Boolean {
        val code = character.code
        return code in 0x2E80..0x9FFF ||
            code in 0xAC00..0xD7AF ||
            code in 0xF900..0xFAFF
    }

}
