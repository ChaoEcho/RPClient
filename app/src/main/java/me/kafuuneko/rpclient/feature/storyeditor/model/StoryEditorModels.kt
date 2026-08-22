package me.kafuuneko.rpclient.feature.storyeditor.model

import me.kafuuneko.rpclient.libs.story.StoryImportDraft

/** Compose 文本编辑状态与 ViewModel 草稿之间的轻量同步快照。 */
data class StoryEditorDocument(
    val storyId: Long,
    val content: String,
    val syncVersion: Long,
    val latestEditedRange: StoryEditedTextRange? = null
)

/** 当前编辑会话中最近一次正文修改所插入或替换内容的半开字符区间。 */
data class StoryEditedTextRange(
    val start: Int,
    val end: Int
) {
    init {
        require(start >= 0) { "Edited text range start cannot be negative" }
        require(end > start) { "Edited text range must not be empty or reversed" }
    }
}

/** 当前正文的文本和 IME composition 快照。 */
data class StoryEditorSnapshot(
    val content: String,
    val isComposing: Boolean
)

/** Story 设置页中的角色卡候选项。 */
data class StoryCharacterOptionItem(
    val id: Long,
    val name: String,
    val description: String,
    val selected: Boolean,
    val activationMode: StoryCharacterActivationMode = StoryCharacterActivationMode.Auto,
    val activationKeysDraft: String = "",
    val sortOrder: Int = Int.MAX_VALUE,
    val linkedLorebookId: Long? = null,
    val linkedLorebookName: String? = null
)

/** Story 设置页可选择的角色激活方式，不暴露 Room 的持久化取值。 */
enum class StoryCharacterActivationMode {
    Always,
    Auto
}

/** Story 设置页中的世界书条目。 */
data class StoryLorebookEntryItem(
    val id: Long,
    val name: String,
    val contentPreview: String,
    val keywords: List<String>,
    val constant: Boolean,
    val selected: Boolean
)

/** Story 设置页中的世界书分组。 */
data class StoryLorebookGroupItem(
    val id: Long,
    val name: String,
    val entries: List<StoryLorebookEntryItem>
) {
    val selectedCount: Int
        get() = entries.count { it.selected }

    val isAllSelected: Boolean
        get() = entries.isNotEmpty() && selectedCount == entries.size
}

/** 启用指定世界书的全部条目，用于角色关联世界书的自动联动。 */
fun List<StoryLorebookGroupItem>.enableLorebook(
    lorebookId: Long
): List<StoryLorebookGroupItem> {
    return map { group ->
        if (group.id == lorebookId) {
            group.copy(entries = group.entries.map { it.copy(selected = true) })
        } else {
            group
        }
    }
}

/**
 * 按 Story 已持久化的条目 ID 重建世界书选择状态。
 *
 * 角色关联只负责在用户选择角色时提供默认勾选，不能在重新打开设置时覆盖用户显式关闭的结果。
 */
fun List<StoryLorebookGroupItem>.restoreLorebookSelection(
    selectedEntryIds: Set<Long>
): List<StoryLorebookGroupItem> {
    return map { group ->
        group.copy(
            entries = group.entries.map { entry ->
                entry.copy(selected = entry.id in selectedEntryIds)
            }
        )
    }
}

/** 按整本世界书切换条目；部分启用时会补全，全部启用时会关闭。 */
fun List<StoryLorebookGroupItem>.toggleLorebook(
    lorebookId: Long
): List<StoryLorebookGroupItem> {
    val target = firstOrNull { it.id == lorebookId } ?: return this
    if (target.entries.isEmpty()) return this
    val selectAll = !target.isAllSelected
    return map { group ->
        if (group.id == lorebookId) {
            group.copy(entries = group.entries.map { it.copy(selected = selectAll) })
        } else {
            group
        }
    }
}

/** 用户选择的故事纯文本导出格式。 */
enum class StoryTextExportFormat {
    Text,
    Markdown
}

/** 一次可撤销的正文替换及其前后世界书时序状态。 */
data class StoryUndoEntry(
    val start: Int,
    val insertedText: String,
    val replacedText: String,
    val previousWorldInfoStateJson: String,
    val previousWorldInfoGenerationStep: Int,
    val nextWorldInfoStateJson: String,
    val nextWorldInfoGenerationStep: Int = previousWorldInfoGenerationStep + 1,
    val source: StoryEditSource = StoryEditSource.Ai
)

/** 正文修改的来源，用于区分手工合并和 AI 原子操作。 */
enum class StoryEditSource {
    Ai,
    User
}

/** 导入确认对话框所需的解析结果和标题草稿。 */
data class StoryImportPreview(
    val draft: StoryImportDraft,
    val title: String,
    val isSaving: Boolean = false
)
