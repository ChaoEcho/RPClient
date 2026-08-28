package me.kafuuneko.rpclient.utils

/** Markdown 渲染器支持的块级语法模型，不依赖 Compose 类型。 */
internal sealed class MarkdownBlock {
    data class Paragraph(val content: String) : MarkdownBlock()
    data class Heading(val level: Int, val content: String) : MarkdownBlock()
    data class Code(val language: String?, val content: String) : MarkdownBlock()
    data class Quote(val content: String) : MarkdownBlock()
    data class ListBlock(val items: List<MarkdownListItem>) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

/** Markdown 渲染器支持的行内语法模型，不携带颜色、字体等界面信息。 */
internal sealed class MarkdownInline {
    data class Text(val content: String) : MarkdownInline()
    data class Code(val content: String) : MarkdownInline()
    data class Strong(val content: List<MarkdownInline>) : MarkdownInline()
    data class Emphasis(val content: List<MarkdownInline>) : MarkdownInline()
    data class Strikethrough(val content: List<MarkdownInline>) : MarkdownInline()
    data class Link(val content: List<MarkdownInline>) : MarkdownInline()
}

/** 行内列表项的标记与正文。 */
internal data class MarkdownListItem(
    val marker: String,
    val content: String
)

/**
 * 轻量 Markdown 解析器。
 *
 * - 只负责把文本转换为稳定的块级和行内模型；
 * - 不依赖 Compose，因此可以在非 UI 层复用和单元测试；
 * - 未识别或未闭合的语法保留为普通文本，兼容流式输出中间态。
 */
internal object MarkdownParser {
    private val mHeadingRegex = Regex("""^(#{1,6})\s+(.+)$""")
    private val mUnorderedListRegex = Regex("""^\s*[-*+]\s+(.+)$""")
    private val mOrderedListRegex = Regex("""^\s*(\d+)[.)]\s+(.+)$""")
    private val mDividerRegex = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

    /** 顺序切分消息支持的块级 Markdown。 */
    fun parseBlocks(source: String): List<MarkdownBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            // 空白行只分隔块，不产生空的展示模型。
            if (trimmed.isBlank()) {
                index += 1
                continue
            }
            if (line.trimStart().startsWith("```")) {
                // 代码围栏优先于其他块级语法，未闭合时消费到消息末尾。
                val parsed = parseCodeBlock(lines, index)
                blocks += parsed.block
                index = parsed.nextIndex
                continue
            }
            if (mDividerRegex.matches(line)) {
                blocks += MarkdownBlock.Divider
                index += 1
                continue
            }
            mHeadingRegex.matchEntire(trimmed)?.let { match ->
                blocks += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    content = match.groupValues[2].trim().trimEnd('#').trim()
                )
                index += 1
                continue
            }
            if (line.trimStart().startsWith(">")) {
                // 连续引用行合并为单个块，保留内部换行。
                val parsed = parseQuoteBlock(lines, index)
                blocks += parsed.block
                index = parsed.nextIndex
                continue
            }
            if (line.isListLine()) {
                // 连续列表项合并为列表块，标记在模型中保留。
                val parsed = parseListBlock(lines, index)
                blocks += parsed.block
                index = parsed.nextIndex
                continue
            }
            // 无法识别为特殊块的内容回退为普通段落。
            val parsed = parseParagraph(lines, index)
            blocks += parsed.block
            index = parsed.nextIndex
        }
        return blocks
    }

    /** 从左到右解析受支持的行内 Markdown，并保留可嵌套的样式结构。 */
    fun parseInline(source: String): List<MarkdownInline> {
        val parts = mutableListOf<MarkdownInline>()
        var index = 0
        var plainStart = 0

        fun flushPlain(end: Int) {
            if (plainStart < end) parts += MarkdownInline.Text(source.substring(plainStart, end))
        }

        while (index < source.length) {
            // 未闭合的定界符返回 null，扫描器会保留其原文。
            val parsed = when {
                source.startsWith("`", index) -> parseDelimited(source, index, "`") {
                    MarkdownInline.Code(it)
                }
                source.startsWith("**", index) -> parseDelimited(source, index, "**") {
                    MarkdownInline.Strong(parseInline(it))
                }
                source.startsWith("__", index) -> parseDelimited(source, index, "__") {
                    MarkdownInline.Strong(parseInline(it))
                }
                source.startsWith("~~", index) -> parseDelimited(source, index, "~~") {
                    MarkdownInline.Strikethrough(parseInline(it))
                }
                source[index] == '*' -> parseDelimited(source, index, "*") {
                    MarkdownInline.Emphasis(parseInline(it))
                }
                source[index] == '_' -> parseDelimited(source, index, "_") {
                    MarkdownInline.Emphasis(parseInline(it))
                }
                source[index] == '[' -> parseLink(source, index)
                else -> null
            }
            if (parsed == null) {
                index += 1
                continue
            }
            flushPlain(index)
            parts += parsed.part
            index = parsed.nextIndex
            plainStart = index
        }
        flushPlain(source.length)
        return parts
    }

    private fun parseCodeBlock(lines: List<String>, startIndex: Int): ParsedBlock {
        val fence = lines[startIndex].trimStart()
        val language = fence.removePrefix("```").trim().takeIf { it.isNotBlank() }
        val codeLines = mutableListOf<String>()
        var index = startIndex + 1
        // 代码块内部不再解析 Markdown，避免改变代码原文。
        while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
            codeLines += lines[index]
            index += 1
        }
        if (index < lines.size) index += 1
        return ParsedBlock(
            block = MarkdownBlock.Code(language = language, content = codeLines.joinToString("\n")),
            nextIndex = index
        )
    }

    private fun parseQuoteBlock(lines: List<String>, startIndex: Int): ParsedBlock {
        val quoteLines = mutableListOf<String>()
        var index = startIndex
        // 只合并相邻引用行，下一种块级语法交给外层扫描。
        while (index < lines.size && lines[index].trimStart().startsWith(">")) {
            quoteLines += lines[index].trimStart().removePrefix(">").trimStart()
            index += 1
        }
        return ParsedBlock(
            block = MarkdownBlock.Quote(quoteLines.joinToString("\n").trim()),
            nextIndex = index
        )
    }

    private fun parseListBlock(lines: List<String>, startIndex: Int): ParsedBlock {
        val items = mutableListOf<MarkdownListItem>()
        var index = startIndex
        // 保持原有列表顺序，并统一无序列表的展示标记。
        while (index < lines.size && lines[index].isListLine()) {
            val line = lines[index]
            val ordered = mOrderedListRegex.matchEntire(line)
            val unordered = mUnorderedListRegex.matchEntire(line)
            if (ordered != null) {
                items += MarkdownListItem("${ordered.groupValues[1]}.", ordered.groupValues[2])
            } else if (unordered != null) {
                items += MarkdownListItem("-", unordered.groupValues[1])
            }
            index += 1
        }
        return ParsedBlock(MarkdownBlock.ListBlock(items), index)
    }

    private fun parseParagraph(lines: List<String>, startIndex: Int): ParsedBlock {
        val paragraphLines = mutableListOf<String>()
        var index = startIndex
        // 段落遇到下一个块级起点时停止，避免跨块吞并文本。
        while (index < lines.size && lines[index].trim().isNotBlank() &&
            !lines[index].startsMarkdownBlock()
        ) {
            paragraphLines += lines[index].trimEnd()
            index += 1
        }
        return ParsedBlock(
            block = MarkdownBlock.Paragraph(paragraphLines.joinToString("\n").trim()),
            nextIndex = index
        )
    }

    private fun parseDelimited(
        source: String,
        startIndex: Int,
        delimiter: String,
        wrap: (String) -> MarkdownInline
    ): ParsedInline? {
        val contentStart = startIndex + delimiter.length
        val end = source.indexOf(delimiter, contentStart)
        if (end == -1) return null
        return ParsedInline(
            part = wrap(source.substring(contentStart, end)),
            nextIndex = end + delimiter.length
        )
    }

    private fun parseLink(source: String, startIndex: Int): ParsedInline? {
        val labelEnd = source.indexOf(']', startIndex + 1)
        val urlStart = labelEnd + 1
        if (labelEnd == -1 || urlStart >= source.length || source[urlStart] != '(') return null
        val urlEnd = source.indexOf(')', urlStart + 1)
        if (urlEnd == -1) return null
        return ParsedInline(
            part = MarkdownInline.Link(
                parseInline(source.substring(startIndex + 1, labelEnd))
            ),
            nextIndex = urlEnd + 1
        )
    }

    private fun String.startsMarkdownBlock(): Boolean {
        val trimmedStart = trimStart()
        val trimmed = trim()
        return trimmedStart.startsWith("```") ||
            trimmedStart.startsWith(">") ||
            mDividerRegex.matches(this) ||
            mHeadingRegex.matches(trimmed) ||
            isListLine()
    }

    private fun String.isListLine(): Boolean {
        return mUnorderedListRegex.matches(this) || mOrderedListRegex.matches(this)
    }

    private data class ParsedBlock(
        val block: MarkdownBlock,
        val nextIndex: Int
    )

    private data class ParsedInline(
        val part: MarkdownInline,
        val nextIndex: Int
    )
}
