package me.kafuuneko.rpclient.libs.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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
            )
        )

        val encoded = codec.encode(archive)
        val lines = encoded.lineSequence().filter { it.isNotBlank() }.toList()
        val header = JsonParser.parseString(lines.first()).asJsonObject
        val narrator = JsonParser.parseString(lines.last()).asJsonObject

        assertEquals("unused", header["user_name"].asString)
        assertTrue(header["chat_metadata"].asJsonObject.has("rpclient"))
        assertFalse(narrator["is_system"].asBoolean)
        assertEquals("narrator", narrator["extra"].asJsonObject["type"].asString)

        val decoded = codec.decode(encoded, fallbackTitle = "Fallback", fallbackTime = 9_000L)

        assertEquals(archive, decoded)
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
    fun malformedOrHeaderlessFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode("""{"name":"Alice","mes":"Hello","is_user":true}""", "Fallback")
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode("""{"chat_metadata":{}}\nnot-json""", "Fallback")
        }
    }
}
