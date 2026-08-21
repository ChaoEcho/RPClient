package me.kafuuneko.rpclient.libs.room.model

/** 群聊列表专用投影，避免为预览加载完整成员对象和消息历史。 */
data class GroupChatSessionOverview(
    val id: Long,
    val title: String,
    val latestTime: Long,
    val memberNames: String,
    val latestMessageContent: String?,
    val messageCount: Int
)
