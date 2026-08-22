package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 连续故事文档实体。
 *
 * [content] 是正文及章节标记的唯一事实来源；[contentRevision] 由带乐观锁的正文写入递增，
 * 普通设置更新不得修改它。
 */
@Entity(tableName = "stories")
data class Story(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val content: String = "",
    val memory: String = "",
    val summary: String = "",
    val authorNote: String = "",
    @ColumnInfo(defaultValue = "0")
    val includeUserPersona: Boolean = false,
    val worldInfoGenerationStep: Int = 0,
    val contentRevision: Long = 0L,
    val createTime: Long,
    val latestTime: Long
)
