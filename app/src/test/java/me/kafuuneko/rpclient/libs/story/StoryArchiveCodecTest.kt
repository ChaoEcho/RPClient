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
                summary = "Summary",
                includeUserPersona = true
            ),
            characterHints = listOf(
                StoryCharacterHint("Alice", "abc", "primary")
            ),
            lorebookHints = listOf(
                StoryLorebookHint(
                    "City",
                    "Station",
                    "def"
                )
            )
        )

        assertEquals(source, mCodec.decode(mCodec.encode(source)))
    }

    @Test
    fun olderV1ArchiveIgnoresLegacyBindingFields() {
        val archive = mCodec.decode(
            """{"format":"rpclient_story","version":1,"story":{"title":"x","content":"y"},"characterHints":[{"name":"Alice","fingerprint":"abc","activationMode":"auto"}],"lorebookHints":[{"lorebookName":"City","entryName":"Station","fingerprint":"def","boundCharacterName":"Alice","boundCharacterFingerprint":"abc"}]}"""
        )

        assertEquals("auto", archive.characterHints.single().activationMode)
        assertEquals(StoryLorebookHint("City", "Station", "def"), archive.lorebookHints.single())
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
