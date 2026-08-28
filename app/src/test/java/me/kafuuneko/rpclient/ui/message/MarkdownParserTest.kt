package me.kafuuneko.rpclient.ui.message

import me.kafuuneko.rpclient.utils.MarkdownBlock
import me.kafuuneko.rpclient.utils.MarkdownInline
import me.kafuuneko.rpclient.utils.MarkdownListItem
import me.kafuuneko.rpclient.utils.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun parseBlocksPreservesSupportedBlockTypes() {
        val blocks = MarkdownParser.parseBlocks(
            "# Heading\n\nParagraph\n```kotlin\nval answer = 42\n```\n> Quote\n- One\n2. Two\n---"
        )

        assertEquals(
            listOf(
                MarkdownBlock.Heading(level = 1, content = "Heading"),
                MarkdownBlock.Paragraph("Paragraph"),
                MarkdownBlock.Code("kotlin", "val answer = 42"),
                MarkdownBlock.Quote("Quote"),
                MarkdownBlock.ListBlock(
                    listOf(
                        MarkdownListItem("-", "One"),
                        MarkdownListItem("2.", "Two")
                    )
                ),
                MarkdownBlock.Divider
            ),
            blocks
        )
    }

    @Test
    fun unclosedCodeFenceConsumesTheRemainingMessage() {
        val blocks = MarkdownParser.parseBlocks("before\n```text\npartial")

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("before"),
                MarkdownBlock.Code("text", "partial")
            ),
            blocks
        )
    }

    @Test
    fun parseInlineKeepsNestedStylesAndLinksAsModels() {
        val parts = MarkdownParser.parseInline(
            "before **bold _inner_** [link](https://example.com) `code`"
        )

        assertEquals(
            listOf(
                MarkdownInline.Text("before "),
                MarkdownInline.Strong(
                    listOf(
                        MarkdownInline.Text("bold "),
                        MarkdownInline.Emphasis(listOf(MarkdownInline.Text("inner")))
                    )
                ),
                MarkdownInline.Text(" "),
                MarkdownInline.Link(listOf(MarkdownInline.Text("link"))),
                MarkdownInline.Text(" "),
                MarkdownInline.Code("code")
            ),
            parts
        )
    }

    @Test
    fun unclosedInlineDelimiterRemainsPlainText() {
        assertEquals(
            listOf(MarkdownInline.Text("keep *the text")),
            MarkdownParser.parseInline("keep *the text")
        )
    }
}
