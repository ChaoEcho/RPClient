package me.kafuuneko.rpclient.libs.room.model

/** Story 列表专用聚合投影，避免列表查询载入任一章节的完整私密正文。 */
data class StoryOverview(
    val id: Long,
    val title: String,
    val contentCharacterCount: Int,
    val preview: String,
    val latestTime: Long
)
