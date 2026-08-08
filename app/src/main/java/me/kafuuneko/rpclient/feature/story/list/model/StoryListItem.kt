package me.kafuuneko.rpclient.feature.story.list.model

/** Story 列表展示模型，不包含完整正文。 */
data class StoryListItem(
    val id: Long,
    val title: String,
    val preview: String,
    val characterCount: Int,
    val updatedAt: String
)
