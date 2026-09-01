package me.kafuuneko.rpclient.libs.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class ChatArchiveCodecTest {
    private val codec = ChatArchiveCodec(Gson())

    @Test
    fun rpclientArchiveRoundTripsThroughSillyTavernJsonl() {
        val archive = ChatArchive(
            title = "Investigation",
            createTime = 1_000L,
            latestTime = 3_000L,
            userName = "Alice",
            userDescription = "Detective",
            userNote = "Private note",
            creatorNotes = "Session notes",
            lorebookEntrySet = "[1,2]",
            worldInfoStateJson = """{"sticky":true}""",
            autoSummaryPaused = true,
            characterNameHint = "Seraphina",
            characterFingerprint = "fingerprint",
            messages = listOf(
                ChatArchiveMessage(
                    createTime = 1_000L,
                    role = ChatArchiveMessageRole.User,
                    content = "Hello\n\"there\""
                ),
                ChatArchiveMessage(
                    createTime = 2_000L,
                    role = ChatArchiveMessageRole.Character,
                    content = "Welcome."
                ),
                ChatArchiveMessage(
                    createTime = 3_000L,
                    role = ChatArchiveMessageRole.Narrator,
                    content = "The lights dim."
                )
            ),
            summary = ChatArchiveSummary(
                content = "Alice arrived.",
                createTime = 4_000L,
                coveredMessageIndex = 1
            ),
            mimoTtsVoiceOverride = "mimo_voice_1"
        )

        val encoded = codec.encode(archive)
        val lines = encoded.lineSequence().filter { it.isNotBlank() }.toList()
        val header = JsonParser.parseString(lines.first()).asJsonObject
        val narrator = JsonParser.parseString(lines.last()).asJsonObject

        assertEquals("unused", header["user_name"].asString)
        assertTrue(header["chat_metadata"].asJsonObject.has("rpclient"))
        assertEquals(
            "mimo_voice_1",
            header["chat_metadata"].asJsonObject
                .getAsJsonObject("rpclient")["mimo_tts_voice_override"].asString
        )
        assertFalse(narrator["is_system"].asBoolean)
        assertEquals("narrator", narrator["extra"].asJsonObject["type"].asString)

        val decoded = codec.decode(encoded, fallbackTitle = "Fallback", fallbackTime = 9_000L)

        assertEquals(archive, decoded)
    }

    @Test
    fun missingNullOrNonStringVoiceOverrideIsImportedAsNull() {
        listOf(
            "{\"schema_version\":1}",
            "{\"schema_version\":1,\"mimo_tts_voice_override\":null}",
            "{\"schema_version\":1,\"mimo_tts_voice_override\":123}",
            "{\"schema_version\":1,\"mimo_tts_voice_override\":true}"
        ).forEach { rpclientMetadata ->
            val decoded = codec.decode(
                jsonl = """{"chat_metadata":{"rpclient":$rpclientMetadata}}""",
                fallbackTitle = "Fallback"
            )

            assertNull(decoded.mimoTtsVoiceOverride)
        }
    }

    @Test
    fun nullVoiceOverrideIsOmittedFromExport() {
        val lines = codec.encode(archive()).lineSequence().filter { it.isNotBlank() }.toList()
        val rpclient = JsonParser.parseString(lines.first()).asJsonObject
            .getAsJsonObject("chat_metadata")
            .getAsJsonObject("rpclient")

        assertFalse(rpclient.has("mimo_tts_voice_override"))
    }

    @Test
    fun streamingEncoderMatchesStringEncoder() {
        val archive = archive()
        val writer = StringWriter()

        codec.encodeHeader(archive, writer)
        archive.messages.forEach { message ->
            codec.encodeMessage(archive, message, writer)
        }

        assertEquals(codec.encode(archive), writer.toString())
    }

    @Test
    fun sillyTavernArchiveUsesMessageNamesAndSkipsSystemUiMessages() {
        val jsonl = """
            ﻿{"user_name":"unused","character_name":"unused","chat_metadata":{}}
            {"name":"Alice","is_user":true,"is_system":false,"send_date":"2026-07-28T10:00:00Z","mes":"Hello"}
            {"name":"Seraphina","is_user":false,"is_system":false,"send_date":1785232801,"mes":"Welcome"}
            {"name":"System","is_user":false,"is_system":true,"mes":"Internal notice"}
            {"name":"Narrator","is_user":false,"is_system":false,"mes":"Rain falls","extra":{"type":"narrator"}}
        """.trimIndent()

        val decoded = codec.decode(
            jsonl = jsonl,
            fallbackTitle = "Imported file",
            fallbackTime = 10_000L
        )

        assertEquals("Imported file", decoded.title)
        assertEquals("Alice", decoded.userName)
        assertEquals("Seraphina", decoded.characterNameHint)
        assertEquals(
            listOf(
                ChatArchiveMessageRole.User,
                ChatArchiveMessageRole.Character,
                ChatArchiveMessageRole.Narrator
            ),
            decoded.messages.map { it.role }
        )
        assertEquals(listOf("Hello", "Welcome", "Rain falls"), decoded.messages.map { it.content })
        assertTrue(decoded.messages.zipWithNext().all { (first, second) ->
            first.createTime < second.createTime
        })
    }

    @Test
    fun missingImportedUserNameUsesCallerFallback() {
        val decoded = codec.decode(
            jsonl = """{"chat_metadata":{}}""",
            fallbackTitle = "Fallback",
            fallbackUserName = " Local user "
        )

        assertEquals("Local user", decoded.userName)
    }

    @Test
    fun malformedOrHeaderlessFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode("""{"name":"Alice","mes":"Hello","is_user":true}""", "Fallback")
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode("""{"chat_metadata":{}}\nnot-json""", "Fallback")
        }
    }

    @Test
    fun messageLimitIsEnforcedWhileReadingLines() {
        val jsonl = buildString {
            appendLine("""{"chat_metadata":{}}""")
            repeat(100_000) {
                appendLine("{}")
            }
            appendLine("not-json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            codec.decode(jsonl.reader(), "Fallback")
        }
        assertEquals("Chat archive has too many messages", error.message)
    }

    @Test
    fun timestampNormalizationRejectsLongOverflow() {
        val jsonl = """
            {"chat_metadata":{}}
            {"name":"Alice","is_user":true,"send_date":9223372036854775807,"mes":"Hello"}
            {"name":"Seraphina","is_user":false,"send_date":0,"mes":"Welcome"}
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(jsonl, "Fallback")
        }
    }

    @Test
    fun futureRpclientSchemaIsRejected() {
        val jsonl = """
            {"chat_metadata":{"rpclient":{"schema_version":2}}}
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            codec.decode(jsonl, "Fallback")
        }

        assertTrue(error.message?.contains("schema version") == true)
    }

    @Test
    fun outOfRangeSummaryBoundaryIsIgnored() {
        val jsonl = """
            {"chat_metadata":{"rpclient":{"schema_version":1,"summary":{"content":"Wrong boundary","covered_message_index":1}}}}
            {"name":"Alice","is_user":true,"mes":"Hello"}
        """.trimIndent()

        val decoded = codec.decode(jsonl, "Fallback")

        assertNull(decoded.summary)
    }

    private fun archive(): ChatArchive {
        return ChatArchive(
            title = "Streaming",
            createTime = 1_000L,
            latestTime = 2_000L,
            userName = "Alice",
            userDescription = "",
            userNote = "",
            creatorNotes = null,
            lorebookEntrySet = "[]",
            worldInfoStateJson = "{}",
            autoSummaryPaused = false,
            characterNameHint = "Seraphina",
            characterFingerprint = null,
            messages = listOf(
                ChatArchiveMessage(1_000L, ChatArchiveMessageRole.User, "Hello"),
                ChatArchiveMessage(2_000L, ChatArchiveMessageRole.Character, "Welcome")
            ),
            summary = null
        )
    }
}
