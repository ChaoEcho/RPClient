package me.kafuuneko.rpclient.feature.main.model

/** 首页故事卡片所需的元数据，不包含完整正文。 */
data class MainStoryItem(
    val id: Long,
    val title: String,
    val preview: String,
    val contentCharacterCount: Int,
    val updatedAt: String
)
