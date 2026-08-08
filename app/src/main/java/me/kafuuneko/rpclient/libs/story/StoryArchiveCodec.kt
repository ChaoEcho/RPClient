package me.kafuuneko.rpclient.libs.story

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Reader
import java.io.Writer

/** `.rpstory.json` 的稳定 V1 编解码器。 */
class StoryArchiveCodec(private val mGson: Gson) {
    fun encode(archive: StoryArchive): String = mGson.toJson(archive)

    fun encode(archive: StoryArchive, writer: Writer) {
        mGson.toJson(archive, writer)
    }

    fun decode(json: String): StoryArchive = decode(json.reader())

    fun decode(reader: Reader): StoryArchive {
        val root = runCatching { JsonParser.parseReader(reader).asJsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid story archive") }
        require(root.requiredString("format") == StoryArchive.FORMAT) {
            "Unsupported story archive format"
        }
        require(root.requiredInt("version") == StoryArchive.VERSION) {
            "Unsupported story archive version"
        }
        val story = root.requiredObject("story")
        val archive = StoryArchive(
            story = ArchivedStory(
                title = story.requiredString("title").trim().ifBlank { DEFAULT_TITLE },
                content = story.requiredString("content"),
                memory = story.optionalString("memory"),
                summary = story.optionalString("summary"),
                authorNote = story.optionalString("authorNote")
            ),
            characterHints = root.arrayObjects("characterHints").map { hint ->
                val mode = hint.requiredString("activationMode").lowercase()
                require(mode == MODE_ALWAYS || mode == MODE_AUTO) {
                    "Unsupported character activation mode"
                }
                StoryCharacterHint(
                    name = hint.requiredString("name"),
                    fingerprint = hint.requiredString("fingerprint"),
                    activationMode = mode,
                    activationKeys = hint.stringArray("activationKeys")
                )
            },
            lorebookHints = root.arrayObjects("lorebookHints").map { hint ->
                StoryLorebookHint(
                    lorebookName = hint.requiredString("lorebookName"),
                    entryName = hint.requiredString("entryName"),
                    fingerprint = hint.requiredString("fingerprint")
                )
            }
        )
        require(archive.characterHints.size <= MAX_HINTS && archive.lorebookHints.size <= MAX_HINTS) {
            "Story archive contains too many references"
        }
        return archive
    }

    private fun JsonObject.requiredObject(key: String): JsonObject {
        return get(key)?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("Story archive is missing $key")
    }

    private fun JsonObject.requiredString(key: String): String {
        return get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw IllegalArgumentException("Story archive is missing $key")
    }

    private fun JsonObject.optionalString(key: String): String {
        val value = get(key) ?: return ""
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Story archive contains invalid $key"
        }
        return value.asString
    }

    private fun JsonObject.requiredInt(key: String): Int {
        return get(key)?.takeIf {
            it.isJsonPrimitive && it.asJsonPrimitive.isNumber
        }?.let {
            runCatching { it.asInt }.getOrNull()
        } ?: throw IllegalArgumentException("Story archive is missing $key")
    }

    private fun JsonObject.arrayObjects(key: String): List<JsonObject> {
        val value = get(key) ?: return emptyList()
        require(value.isJsonArray) { "Story archive contains invalid $key" }
        return value.asJsonArray.map {
            require(it.isJsonObject) { "Story archive contains invalid $key" }
            it.asJsonObject
        }
    }

    private fun JsonObject.stringArray(key: String): List<String> {
        val value = get(key) ?: return emptyList()
        require(value.isJsonArray) { "Story archive contains invalid $key" }
        return value.asJsonArray.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                "Story archive contains invalid $key"
            }
            it.asString
        }
    }

    companion object {
        const val MODE_ALWAYS = "always"
        const val MODE_AUTO = "auto"
        private const val DEFAULT_TITLE = "Imported story"
        private const val MAX_HINTS = 10_000
    }
}
