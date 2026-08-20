package me.kafuuneko.rpclient.feature.story.create.model

/** 创建 Story 时尚未持久化的角色与世界书选择。 */
data class StoryCreateForm(
    val title: String = "",
    val selectedCharacterIds: Set<Long> = emptySet(),
    val selectedLorebookEntryIds: Set<Long> = emptySet()
)

/** 新建 Story 页面中的角色卡候选项。 */
data class StoryCreateCharacterItem(
    val id: Long,
    val name: String,
    val description: String,
    val tags: List<String>,
    val linkedLorebookId: Long? = null,
    val linkedLorebookName: String? = null
)

/** 新建 Story 页面中可独立开启或关闭的世界书条目。 */
data class StoryCreateLorebookEntryItem(
    val id: Long,
    val lorebookName: String,
    val name: String,
    val content: String,
    val keywords: List<String>,
    val constant: Boolean,
    val order: Int,
    val depth: Int
)

/** 新建 Story 页面中的世界书分组。 */
data class StoryCreateLorebookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val entries: List<StoryCreateLorebookEntryItem>
) {
    val entryCount: Int
        get() = entries.size
}
