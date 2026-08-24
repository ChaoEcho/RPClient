package me.kafuuneko.rpclient.libs.room.model

/** 章节大纲专用投影，只读取标题、顺序和轻量统计，不载入正文。 */
data class StoryChapterOverview(
    val id: Long,
    val storyId: Long,
    val volumeId: Long?,
    val title: String,
    val contentCharacterCount: Int,
    val sortOrder: Int,
    val contentRevision: Long,
    val latestTime: Long
)
