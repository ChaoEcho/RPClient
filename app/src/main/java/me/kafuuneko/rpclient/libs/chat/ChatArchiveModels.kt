package me.kafuuneko.rpclient.libs.chat

/**
 * 可导入、导出的单聊归档。
 *
 * 该模型不携带本地 Room 主键；角色绑定由用户在导入确认阶段重新选择，避免跨设备
 * 复用自增 ID 导致误关联。
 */
data class ChatArchive(
    val title: String,
    val createTime: Long,
    val latestTime: Long,
    val userName: String,
    val userDescription: String,
    val userNote: String,
    val creatorNotes: String?,
    val lorebookEntrySet: String,
    val worldInfoStateJson: String,
    val autoSummaryPaused: Boolean,
    val characterNameHint: String,
    val characterFingerprint: String?,
    val messages: List<ChatArchiveMessage>,
    val summary: ChatArchiveSummary?
)

/** 归档中的普通消息；列表顺序是唯一权威的对话顺序。 */
data class ChatArchiveMessage(
    val createTime: Long,
    val role: ChatArchiveMessageRole,
    val content: String
)

/** RPClient 单聊消息与 SillyTavern 消息标记之间的稳定角色集合。 */
enum class ChatArchiveMessageRole {
    User,
    Character,
    Narrator
}

/**
 * 归档中的最新总结快照。
 *
 * [coveredMessageIndex] 使用普通消息列表的零基索引，-1 表示空边界。数据库写入后再转换为
 * 新生成的消息主键，避免把来源数据库 ID 带入目标数据库。
 */
data class ChatArchiveSummary(
    val content: String,
    val createTime: Long,
    val coveredMessageIndex: Int
)
