package me.kafuuneko.rpclient.feature.llmprovideredit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** JSON token categories rendered by the provider configuration editors. */
internal enum class JsonSyntaxTokenType {
    Key,
    String,
    Number,
    Literal,
    Punctuation
}

/** A half-open source range that can be styled without changing cursor offsets. */
internal data class JsonSyntaxToken(
    val start: Int,
    val end: Int,
    val type: JsonSyntaxTokenType
)

/** Theme-derived colors for JSON syntax highlighting. */
internal data class JsonSyntaxColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val literal: Color,
    val punctuation: Color
)

/**
 * Styles JSON source while leaving its text and offsets unchanged.
 *
 * The lexer deliberately accepts incomplete input so highlighting remains stable while the user
 * is in the middle of typing invalid JSON.
 */
internal class JsonSyntaxVisualTransformation(
    private val colors: JsonSyntaxColors
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = buildAnnotatedString {
            append(text.text)
            tokenizeJsonSyntax(text.text).forEach { token ->
                addStyle(
                    style = token.style(colors),
                    start = token.start,
                    end = token.end
                )
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

private fun JsonSyntaxToken.style(colors: JsonSyntaxColors): SpanStyle {
    return when (type) {
        JsonSyntaxTokenType.Key -> SpanStyle(
            color = colors.key,
            fontWeight = FontWeight.SemiBold
        )

        JsonSyntaxTokenType.String -> SpanStyle(color = colors.string)
        JsonSyntaxTokenType.Number -> SpanStyle(color = colors.number)
        JsonSyntaxTokenType.Literal -> SpanStyle(
            color = colors.literal,
            fontWeight = FontWeight.SemiBold
        )

        JsonSyntaxTokenType.Punctuation -> SpanStyle(color = colors.punctuation)
    }
}

/** Single-pass lexer for valid and partially entered JSON. */
internal fun tokenizeJsonSyntax(source: String): List<JsonSyntaxToken> {
    val tokens = mutableListOf<JsonSyntaxToken>()
    var index = 0
    while (index < source.length) {
        val start = index
        when (source[index]) {
            '"' -> {
                index = readStringEnd(source, start)
                val type = if (isObjectKey(source, index)) {
                    JsonSyntaxTokenType.Key
                } else {
                    JsonSyntaxTokenType.String
                }
                tokens += JsonSyntaxToken(start, index, type)
            }

            '-', in '0'..'9' -> {
                index = readNumberEnd(source, start)
                tokens += JsonSyntaxToken(start, index, JsonSyntaxTokenType.Number)
            }

            '{', '}', '[', ']', ':', ',' -> {
                index++
                tokens += JsonSyntaxToken(start, index, JsonSyntaxTokenType.Punctuation)
            }

            else -> {
                val literalEnd = readLiteralEnd(source, start)
                if (literalEnd != null) {
                    index = literalEnd
                    tokens += JsonSyntaxToken(start, index, JsonSyntaxTokenType.Literal)
                } else {
                    index++
                }
            }
        }
    }
    return tokens
}

private fun readStringEnd(source: String, start: Int): Int {
    var index = start + 1
    var escaped = false
    while (index < source.length) {
        val char = source[index]
        if (escaped) {
            escaped = false
        } else {
            when (char) {
                '\\' -> escaped = true
                '"' -> return index + 1
            }
        }
        index++
    }
    return source.length
}

private fun isObjectKey(source: String, stringEnd: Int): Boolean {
    var index = stringEnd
    while (index < source.length && source[index].isWhitespace()) index++
    return index < source.length && source[index] == ':'
}

private fun readNumberEnd(source: String, start: Int): Int {
    var index = start
    if (source[index] == '-') index++
    while (index < source.length && source[index].isDigit()) index++
    if (index < source.length && source[index] == '.') {
        index++
        while (index < source.length && source[index].isDigit()) index++
    }
    if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
        index++
        if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
        while (index < source.length && source[index].isDigit()) index++
    }
    return index
}

private fun readLiteralEnd(source: String, start: Int): Int? {
    if (start > 0 && source[start - 1].isIdentifierCharacter()) return null
    val literal = when (source[start]) {
        't' -> "true"
        'f' -> "false"
        'n' -> "null"
        else -> return null
    }
    if (!source.startsWith(literal, start)) return null
    val end = start + literal.length
    if (end < source.length && source[end].isIdentifierCharacter()) return null
    return end
}

private fun Char.isIdentifierCharacter(): Boolean = isLetterOrDigit() || this == '_'
