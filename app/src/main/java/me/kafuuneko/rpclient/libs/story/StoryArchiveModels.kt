package me.kafuuneko.rpclient.libs.story

/** RPClient 故事归档 V2 的顶层传输模型。 */
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
        const val VERSION = 2
    }
}

/** 故事归档中可独立恢复的层级正文与 Story 级上下文设置。 */
data class ArchivedStory(
    val title: String,
    val memory: String = "",
    val authorNote: String = "",
    val summary: String = "",
    val includeUserPersona: Boolean = false,
    val ungroupedChapters: List<ArchivedChapter> = emptyList(),
    val volumes: List<ArchivedVolume> = emptyList()
) {
    val chapterCount: Int
        get() = ungroupedChapters.size + volumes.sumOf { it.chapters.size }

    val totalCharacterCount: Int
        get() = ungroupedChapters.sumOf { it.content.length } +
            volumes.sumOf { volume -> volume.chapters.sumOf { it.content.length } }
}

/** 归档中的一个分卷；数组顺序就是分卷和卷内章节顺序。 */
data class ArchivedVolume(
    val title: String,
    val chapters: List<ArchivedChapter> = emptyList()
)

/** 归档中的一个章节。 */
data class ArchivedChapter(
    val title: String,
    val content: String
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

/**
 * 已完成解析但尚未写入 Room 的结构化导入草稿。
 *
 * 纯文本导入同样转换为单章节结构，避免在导入确认阶段保留第二份连续正文事实来源。
 */
data class StoryImportDraft(
    val title: String,
    val memory: String = "",
    val authorNote: String = "",
    val summary: String = "",
    val includeUserPersona: Boolean = false,
    val ungroupedChapters: List<ArchivedChapter> = emptyList(),
    val volumes: List<ArchivedVolume> = emptyList(),
    val characterHints: List<StoryCharacterHint> = emptyList(),
    val lorebookHints: List<StoryLorebookHint> = emptyList(),
    val type: StoryImportType
) {
    val chapterCount: Int
        get() = ungroupedChapters.size + volumes.sumOf { it.chapters.size }

    val totalCharacterCount: Int
        get() = ungroupedChapters.sumOf { it.content.length } +
            volumes.sumOf { volume -> volume.chapters.sumOf { it.content.length } }
}
