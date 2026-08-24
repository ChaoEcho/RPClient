package me.kafuuneko.rpclient.libs.story

/** RPClient 故事归档 V1 的顶层传输模型。 */
data class StoryArchive(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val story: ArchivedStory,
    val characterHints: List<StoryCharacterHint> = emptyList(),
    val lorebookHints: List<StoryLorebookHint> = emptyList()
) {
    companion object {
        /** 故事归档标识字符串。 */
        const val FORMAT = "rpclient_story"
        /** 故事归档协议版本号。 */
        const val VERSION = 1
    }
}

/** 故事归档中可独立恢复的正文与上下文设置。 */
data class ArchivedStory(
    val title: String,
    val content: String,
    val memory: String = "",
    val authorNote: String = "",
    val summary: String = "",
    val includeUserPersona: Boolean = false
)

/** 归档中的角色匹配提示，不包含完整角色卡内容。 */
data class StoryCharacterHint(
    val name: String,
    val fingerprint: String,
    val activationMode: String
)

/** 归档中的世界书条目匹配提示，不包含条目正文。 */
data class StoryLorebookHint(
    val lorebookName: String,
    val entryName: String,
    val fingerprint: String
)

/** 故事导入来源类型。 */
enum class StoryImportType {
    Text,
    Archive
}

/** 已完成解析但尚未写入 Room 的导入草稿。 */
data class StoryImportDraft(
    val title: String,
    val content: String,
    val memory: String = "",
    val authorNote: String = "",
    val summary: String = "",
    val includeUserPersona: Boolean = false,
    val characterHints: List<StoryCharacterHint> = emptyList(),
    val lorebookHints: List<StoryLorebookHint> = emptyList(),
    val type: StoryImportType
)
