package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/** 世界书及其条目的批量读取结果，避免调用方逐本查询条目。 */
data class LorebookWithEntries(
    val lorebook: Lorebook,
    val entries: List<LorebookEntry>
)

/** 世界书条目数量的聚合查询结果。 */
data class LorebookEntryCount(
    val lorebookId: Long,
    val count: Int
)
