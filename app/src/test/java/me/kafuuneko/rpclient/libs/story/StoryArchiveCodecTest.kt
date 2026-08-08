package me.kafuuneko.rpclient.libs.story

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryArchiveCodecTest {
    private val mCodec = StoryArchiveCodec(Gson())

    @Test
    fun archiveRoundTripPreservesStoryAndHints() {
        val source = StoryArchive(
            story = ArchivedStory(
                title = "Rain",
                content = "正文😀",
                memory = "Memory",
                authorNote = "Note",
                summary = "Summary"
            ),
            characterHints = listOf(
                StoryCharacterHint("Alice", "abc", "always", listOf("Ally"))
            ),
            lorebookHints = listOf(StoryLorebookHint("City", "Station", "def"))
        )

        assertEquals(source, mCodec.decode(mCodec.encode(source)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownMajorVersionIsRejected() {
        mCodec.decode(
            """{"format":"rpclient_story","version":2,"story":{"title":"x","content":"y"}}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingContentIsRejected() {
        mCodec.decode(
            """{"format":"rpclient_story","version":1,"story":{"title":"x"}}"""
        )
    }
}
