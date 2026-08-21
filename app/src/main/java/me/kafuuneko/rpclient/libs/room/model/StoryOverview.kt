package me.kafuuneko.rpclient.libs.room.model

/** Story 列表专用投影，避免列表查询载入完整私密正文。 */
data class StoryOverview(
    val id: Long,
    val title: String,
    val contentCharacterCount: Int,
    val preview: String,
    val latestTime: Long
)
