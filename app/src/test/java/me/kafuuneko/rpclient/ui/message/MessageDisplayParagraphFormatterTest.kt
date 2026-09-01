package me.kafuuneko.rpclient.ui.message

import me.kafuuneko.rpclient.utils.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDisplayParagraphFormatterTest {
    @Test
    fun shortChineseParagraphRemainsUnchanged() {
        val source = "这是一个短段落。这里还有一句。"
        val blocks = listOf(MarkdownBlock.Paragraph(source))

        assertEquals(blocks, MessageDisplayParagraphFormatter.format(blocks))
    }

    @Test
    fun longChineseParagraphSplitsIntoBalancedSentenceGroups() {
        val source = "第一句说明了故事的背景。第二句补充了人物的动作。第三句交代了当前的目标。第四句描述了环境的变化。第五句说明了接下来的计划。第六句留下了一个新的问题。"

        val result = MessageDisplayParagraphFormatter.format(
            listOf(MarkdownBlock.Paragraph(source))
        )

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("第一句说明了故事的背景。第二句补充了人物的动作。第三句交代了当前的目标。"),
                MarkdownBlock.Paragraph("第四句描述了环境的变化。第五句说明了接下来的计划。第六句留下了一个新的问题。")
            ),
            result
        )
    }

    @Test
    fun longThreeSentenceParagraphCanSplitByLength() {
        val source = "第一句用较长的篇幅描述窗外持续不断的暴雨、昏暗的街灯和空无一人的道路，让整个场景显得格外压抑。第二句继续描写角色在房间里来回踱步、反复查看时间并犹豫是否应该打开那封刚收到的信。第三句则说明门外突然响起脚步声，她终于停下来并把手放到了门把手上。"

        val result = MessageDisplayParagraphFormatter.format(
            listOf(MarkdownBlock.Paragraph(source))
        )

        assertEquals(2, result.size)
        assertEquals(source, result.joinToString("") { (it as MarkdownBlock.Paragraph).content })
    }

    @Test
    fun ChineseClosingQuotesStayWithTheirSentence() {
        val source = "她说：“第一句已经结束了。”他回答：“第二句也结束了！”随后两人安静下来。最后他们一起离开了。"

        val result = MessageDisplayParagraphFormatter.format(
            listOf(MarkdownBlock.Paragraph(source))
        )
        val paragraphs = result.filterIsInstance<MarkdownBlock.Paragraph>().map { it.content }

        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs[0].endsWith("。”他回答：“第二句也结束了！”"))
        assertTrue(paragraphs[1].startsWith("随后两人安静下来。"))
    }

    @Test
    fun longEnglishParagraphSplitsWithoutCreatingSingleSentenceFragments() {
        val source = "The first sentence establishes the setting. The second sentence introduces the immediate problem. The third sentence explains what the character notices next. The fourth sentence closes the scene and points toward the next action."

        val result = MessageDisplayParagraphFormatter.format(
            listOf(MarkdownBlock.Paragraph(source))
        )

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(
                    "The first sentence establishes the setting. The second sentence introduces the immediate problem."
                ),
                MarkdownBlock.Paragraph(
                    "The third sentence explains what the character notices next. The fourth sentence closes the scene and points toward the next action."
                )
            ),
            result
        )
    }

    @Test
    fun urlAndInlineCodePunctuationDoesNotCreateInternalSplits() {
        val url = "https://example.com/docs/v1.2?mode=full"
        val source = "Open $url. Then read the first note. Compare the second note. Save the final result with `value.one?two`."

        val paragraphs = MessageDisplayParagraphFormatter.format(
            listOf(MarkdownBlock.Paragraph(source))
        ).filterIsInstance<MarkdownBlock.Paragraph>().map { it.content }

        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs.any { url in it })
        assertTrue(paragraphs.any { "`value.one?two`" in it })
        assertFalse(paragraphs.any { it.contains("https://example.com/docs/v1") && !it.contains(url) })
    }

    @Test
    fun nonParagraphBlocksRemainUntouched() {
        val code = MarkdownBlock.Code("kotlin", "println(\"one. two?\")")
        val heading = MarkdownBlock.Heading(2, "Heading")
        val quote = MarkdownBlock.Quote("A quote.")
        val list = MarkdownBlock.ListBlock(emptyList())
        val divider = MarkdownBlock.Divider
        val input = listOf(
            code,
            heading,
            quote,
            list,
            divider,
            MarkdownBlock.Paragraph("第一句。第二句。第三句。第四句。")
        )

        val result = MessageDisplayParagraphFormatter.format(input)

        assertSame(code, result[0])
        assertSame(heading, result[1])
        assertSame(quote, result[2])
        assertSame(list, result[3])
        assertSame(divider, result[4])
    }

    @Test
    fun existingParagraphsAreNeverMerged() {
        val first = MarkdownBlock.Paragraph("第一段已经独立。")
        val second = MarkdownBlock.Paragraph("第二段也已经独立。")

        val result = MessageDisplayParagraphFormatter.format(listOf(first, second))

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun formattingIsDeterministicAndDoesNotMutateSource() {
        val source = "第一句。第二句。第三句。第四句。第五句。第六句。"
        val originalSource = source
        val input = listOf(MarkdownBlock.Paragraph(source))

        val firstResult = MessageDisplayParagraphFormatter.format(input)
        val secondResult = MessageDisplayParagraphFormatter.format(input)

        assertEquals(firstResult, secondResult)
        assertEquals(originalSource, source)
        assertEquals(originalSource, input.single().content)
    }
}
