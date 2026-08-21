package me.kafuuneko.rpclient.libs.room.model

/** 单聊列表专用投影，在一次查询中返回会话及其消息概览。 */
data class ChatSessionOverview(
    val id: Long,
    val characterId: Long,
    val title: String,
    val latestTime: Long,
    val latestMessageContent: String?,
    val messageCount: Int
)
