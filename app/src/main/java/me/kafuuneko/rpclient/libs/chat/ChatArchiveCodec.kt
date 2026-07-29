package me.kafuuneko.rpclient.libs.chat

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.Reader
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RPClient 与 SillyTavern JSONL 单聊文件之间的编解码器。
 *
 * 第一行遵循 SillyTavern 的 chat header，后续每行保存一条消息；RPClient 无法由酒馆表达的
 * 会话设置和总结放在 `chat_metadata.rpclient` 命名空间，酒馆可忽略这些扩展字段。
 */
class ChatArchiveCodec(
    private val mGson: Gson
) {
    /** 将单聊归档编码为可被 SillyTavern 导入的 UTF-8 JSONL。 */
    fun encode(archive: ChatArchive): String {
        val lines = buildList {
            add(buildHeader(archive))
            archive.messages.forEach { message ->
                add(buildMessage(archive, message))
            }
        }
        return lines.joinToString(separator = "\n", postfix = "\n") { mGson.toJson(it) }
    }

    /**
     * 解析 SillyTavern JSONL 或 RPClient 导出的扩展 JSONL。
     *
     * [fallbackTitle] 通常来自系统文档名；[fallbackTime] 仅在来源没有可识别时间时使用。
     */
    fun decode(
        jsonl: String,
        fallbackTitle: String,
        fallbackTime: Long = System.currentTimeMillis()
    ): ChatArchive {
        return decode(
            reader = jsonl.reader(),
            fallbackTitle = fallbackTitle,
            fallbackTime = fallbackTime
        )
    }

    fun decode(
        reader: Reader,
        fallbackTitle: String,
        fallbackTime: Long = System.currentTimeMillis()
    ): ChatArchive {
        val parsed = parseLines(reader, fallbackTime)
        val header = parsed.header
        require(header.isChatHeader()) { "Chat archive header is missing" }
        val metadata = header.objectOrNull(KEY_CHAT_METADATA)
        val rpclient = metadata?.objectOrNull(KEY_RPCLIENT)
        val decodedMessages = parsed.messages.normalizeTimes()

        val customCreateTime = rpclient?.longOrNull(KEY_CREATE_TIME)
        val createTime = customCreateTime
            ?: decodedMessages.firstOrNull()?.message?.createTime
            ?: fallbackTime
        val customLatestTime = rpclient?.longOrNull(KEY_LATEST_TIME)
        val latestTime = customLatestTime
            ?: decodedMessages.lastOrNull()?.message?.createTime
            ?: createTime
        val characterObject = rpclient?.objectOrNull(KEY_CHARACTER)
        val characterNameHint = characterObject?.stringOrNull(KEY_NAME)
            .takeUnlessUnused()
            ?: header.stringOrNull(KEY_CHARACTER_NAME).takeUnlessUnused()
            ?: decodedMessages.characterNameHint()
        val userName = rpclient?.stringOrNull(KEY_USER_NAME)
            .takeUnlessUnused()
            ?: decodedMessages.firstOrNull { it.message.role == ChatArchiveMessageRole.User }
                ?.speakerName
                .takeUnlessUnused()
            ?: header.stringOrNull(KEY_USER_NAME).takeUnlessUnused()
            ?: DEFAULT_USER_NAME

        return ChatArchive(
            title = rpclient?.stringOrNull(KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackTitle.ifBlank { DEFAULT_TITLE },
            createTime = createTime,
            latestTime = maxOf(latestTime, decodedMessages.lastOrNull()?.message?.createTime ?: createTime),
            userName = userName,
            userDescription = rpclient?.stringOrNull(KEY_USER_DESCRIPTION).orEmpty(),
            userNote = rpclient?.stringOrNull(KEY_USER_NOTE).orEmpty(),
            creatorNotes = rpclient?.stringOrNull(KEY_CREATOR_NOTES),
            lorebookEntrySet = rpclient?.stringOrNull(KEY_LOREBOOK_ENTRY_SET) ?: "[]",
            worldInfoStateJson = rpclient?.stringOrNull(KEY_WORLD_INFO_STATE) ?: "{}",
            autoSummaryPaused = rpclient?.booleanOrNull(KEY_AUTO_SUMMARY_PAUSED) ?: false,
            characterNameHint = characterNameHint.orEmpty(),
            characterFingerprint = characterObject?.stringOrNull(KEY_FINGERPRINT),
            messages = decodedMessages.map { it.message },
            summary = rpclient?.objectOrNull(KEY_SUMMARY)?.toSummary(createTime)
        )
    }

    private fun buildHeader(archive: ChatArchive): JsonObject {
        val rpclient = JsonObject().apply {
            addProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            addProperty(KEY_TITLE, archive.title)
            addProperty(KEY_CREATE_TIME, archive.createTime)
            addProperty(KEY_LATEST_TIME, archive.latestTime)
            addProperty(KEY_USER_NAME, archive.userName)
            addProperty(KEY_USER_DESCRIPTION, archive.userDescription)
            addProperty(KEY_USER_NOTE, archive.userNote)
            archive.creatorNotes?.let { addProperty(KEY_CREATOR_NOTES, it) }
            addProperty(KEY_LOREBOOK_ENTRY_SET, archive.lorebookEntrySet)
            addProperty(KEY_WORLD_INFO_STATE, archive.worldInfoStateJson)
            addProperty(KEY_AUTO_SUMMARY_PAUSED, archive.autoSummaryPaused)
            add(
                KEY_CHARACTER,
                JsonObject().apply {
                    addProperty(KEY_NAME, archive.characterNameHint)
                    archive.characterFingerprint?.let { addProperty(KEY_FINGERPRINT, it) }
                }
            )
            archive.summary?.let { summary ->
                add(
                    KEY_SUMMARY,
                    JsonObject().apply {
                        addProperty(KEY_CONTENT, summary.content)
                        addProperty(KEY_CREATE_TIME, summary.createTime)
                        addProperty(KEY_COVERED_MESSAGE_INDEX, summary.coveredMessageIndex)
                    }
                )
            }
        }
        return JsonObject().apply {
            addProperty(KEY_USER_NAME, UNUSED_NAME)
            addProperty(KEY_CHARACTER_NAME, UNUSED_NAME)
            add(
                KEY_CHAT_METADATA,
                JsonObject().apply {
                    add(KEY_RPCLIENT, rpclient)
                }
            )
        }
    }

    private fun buildMessage(
        archive: ChatArchive,
        message: ChatArchiveMessage
    ): JsonObject {
        val isUser = message.role == ChatArchiveMessageRole.User
        val isNarrator = message.role == ChatArchiveMessageRole.Narrator
        return JsonObject().apply {
            addProperty(
                KEY_NAME,
                when {
                    isUser -> archive.userName
                    isNarrator -> NARRATOR_NAME
                    else -> archive.characterNameHint
                }
            )
            addProperty(KEY_IS_USER, isUser)
            // SillyTavern 的 is_system 表示 UI 系统消息；旁白使用 extra.type 表达。
            addProperty(KEY_IS_SYSTEM, false)
            addProperty(KEY_SEND_DATE, Instant.ofEpochMilli(message.createTime).toString())
            addProperty(KEY_MESSAGE, message.content)
            add(
                KEY_EXTRA,
                JsonObject().apply {
                    if (isNarrator) addProperty(KEY_TYPE, NARRATOR_TYPE)
                }
            )
        }
    }

    private fun parseLines(reader: Reader, fallbackTime: Long): ParsedArchiveLines {
        var header: JsonObject? = null
        val messages = mutableListOf<DecodedMessage>()
        var messageObjectCount = 0
        reader.buffered().lineSequence().forEachIndexed { index, sourceLine ->
            val line = if (index == 0) sourceLine.removePrefix(BYTE_ORDER_MARK) else sourceLine
            if (line.isBlank()) return@forEachIndexed

            if (header == null) {
                header = parseLine(line, index)
                return@forEachIndexed
            }

            require(messageObjectCount < MAX_MESSAGE_COUNT) {
                "Chat archive has too many messages"
            }
            val json = parseLine(line, index)
            decodeMessage(json, fallbackTime + messageObjectCount)?.let(messages::add)
            messageObjectCount += 1
        }
        return ParsedArchiveLines(
            header = requireNotNull(header) { "Chat archive is empty" },
            messages = messages
        )
    }

    private fun parseLine(line: String, index: Int): JsonObject {
        return try {
            JsonParser.parseString(line).asJsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Invalid chat archive JSON at line ${index + 1}",
                error
            )
        }
    }

    private fun decodeMessage(json: JsonObject, fallbackTime: Long): DecodedMessage? {
        val content = json.stringOrNull(KEY_MESSAGE) ?: return null
        val extraType = json.objectOrNull(KEY_EXTRA)?.stringOrNull(KEY_TYPE)
        val role = when {
            extraType.equals(NARRATOR_TYPE, ignoreCase = true) -> ChatArchiveMessageRole.Narrator
            json.booleanOrNull(KEY_IS_USER) == true -> ChatArchiveMessageRole.User
            json.booleanOrNull(KEY_IS_SYSTEM) == true -> return null
            else -> ChatArchiveMessageRole.Character
        }
        return DecodedMessage(
            message = ChatArchiveMessage(
                createTime = json.elementOrNull(KEY_SEND_DATE).toTimestampOrNull() ?: fallbackTime,
                role = role,
                content = content
            ),
            speakerName = json.stringOrNull(KEY_NAME).orEmpty()
        )
    }

    private fun List<DecodedMessage>.normalizeTimes(): List<DecodedMessage> {
        var previousTime = Long.MIN_VALUE
        return map { decoded ->
            val normalizedTime = if (decoded.message.createTime > previousTime) {
                decoded.message.createTime
            } else {
                require(previousTime < Long.MAX_VALUE) {
                    "Chat archive message timestamps cannot be normalized"
                }
                previousTime + 1L
            }
            previousTime = normalizedTime
            decoded.copy(message = decoded.message.copy(createTime = normalizedTime))
        }
    }

    private fun List<DecodedMessage>.characterNameHint(): String? {
        return asSequence()
            .filter { it.message.role == ChatArchiveMessageRole.Character }
            .map { it.speakerName.trim() }
            .filter { it.isNotBlank() && !it.equals(UNUSED_NAME, ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun JsonObject.isChatHeader(): Boolean {
        if (has(KEY_CHAT_METADATA)) return true
        if (has(KEY_MESSAGE)) return false
        return has(KEY_USER_NAME) || has(KEY_CHARACTER_NAME)
    }

    private fun JsonObject.toSummary(defaultTime: Long): ChatArchiveSummary {
        return ChatArchiveSummary(
            content = stringOrNull(KEY_CONTENT).orEmpty(),
            createTime = longOrNull(KEY_CREATE_TIME) ?: defaultTime,
            coveredMessageIndex = intOrNull(KEY_COVERED_MESSAGE_INDEX) ?: -1
        )
    }

    private fun JsonObject.elementOrNull(key: String): JsonElement? {
        return get(key)?.takeUnless { it.isJsonNull }
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? {
        return elementOrNull(key)?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return elementOrNull(key)?.takeIf { it.isJsonPrimitive }?.let {
            runCatching { it.asString }.getOrNull()
        }
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        return elementOrNull(key)?.takeIf { it.isJsonPrimitive }?.let {
            runCatching { it.asLong }.getOrNull()
        }
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        return elementOrNull(key)?.takeIf { it.isJsonPrimitive }?.let {
            runCatching { it.asInt }.getOrNull()
        }
    }

    private fun JsonObject.booleanOrNull(key: String): Boolean? {
        return elementOrNull(key)?.takeIf { it.isJsonPrimitive }?.let {
            runCatching { it.asBoolean }.getOrNull()
        }
    }

    private fun JsonElement?.toTimestampOrNull(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isNumber) {
            return primitive.asLong.normalizeEpoch()
        }
        val value = runCatching { primitive.asString.trim() }.getOrNull() ?: return null
        value.toLongOrNull()?.let { return it.normalizeEpoch() }
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()
            ?.let { return it }
        runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()?.let { return it }
        return LegacyTimestampRegex.matchEntire(value)?.destructured?.let {
            val (year, month, day, hour, minute, second, millis) = it
            runCatching {
                LocalDateTime.of(
                    year.toInt(),
                    month.toInt(),
                    day.toInt(),
                    hour.toInt(),
                    minute.toInt(),
                    second.toInt(),
                    millis.toInt() * 1_000_000
                ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
        }
    }

    private fun Long.normalizeEpoch(): Long {
        return if (this in -SECONDS_EPOCH_THRESHOLD..SECONDS_EPOCH_THRESHOLD) {
            this * 1_000L
        } else {
            this
        }
    }

    private fun String?.takeUnlessUnused(): String? {
        return this?.trim()?.takeIf {
            it.isNotBlank() && !it.equals(UNUSED_NAME, ignoreCase = true)
        }
    }

    private data class DecodedMessage(
        val message: ChatArchiveMessage,
        val speakerName: String
    )

    private data class ParsedArchiveLines(
        val header: JsonObject,
        val messages: List<DecodedMessage>
    )

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_MESSAGE_COUNT = 100_000
        const val SECONDS_EPOCH_THRESHOLD = 100_000_000_000L
        const val BYTE_ORDER_MARK = "\uFEFF"
        const val UNUSED_NAME = "unused"
        const val DEFAULT_USER_NAME = "You"
        const val DEFAULT_TITLE = "Imported chat"
        const val NARRATOR_NAME = "Narrator"
        const val NARRATOR_TYPE = "narrator"

        const val KEY_CHAT_METADATA = "chat_metadata"
        const val KEY_RPCLIENT = "rpclient"
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_TITLE = "title"
        const val KEY_CREATE_TIME = "create_time"
        const val KEY_LATEST_TIME = "latest_time"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_DESCRIPTION = "user_description"
        const val KEY_USER_NOTE = "user_note"
        const val KEY_CREATOR_NOTES = "creator_notes"
        const val KEY_LOREBOOK_ENTRY_SET = "lorebook_entry_set"
        const val KEY_WORLD_INFO_STATE = "world_info_state_json"
        const val KEY_AUTO_SUMMARY_PAUSED = "auto_summary_paused"
        const val KEY_CHARACTER = "character"
        const val KEY_CHARACTER_NAME = "character_name"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_SUMMARY = "summary"
        const val KEY_CONTENT = "content"
        const val KEY_COVERED_MESSAGE_INDEX = "covered_message_index"
        const val KEY_NAME = "name"
        const val KEY_IS_USER = "is_user"
        const val KEY_IS_SYSTEM = "is_system"
        const val KEY_SEND_DATE = "send_date"
        const val KEY_MESSAGE = "mes"
        const val KEY_EXTRA = "extra"
        const val KEY_TYPE = "type"

        val LegacyTimestampRegex = Regex(
            """^(\d{4})-(\d{1,2})-(\d{1,2})\s*@?(\d{1,2})h\s+(\d{1,2})m\s+(\d{1,2})s\s+(\d{1,3})ms$"""
        )
    }
}
