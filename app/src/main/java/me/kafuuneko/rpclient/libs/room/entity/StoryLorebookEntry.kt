package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Story 启用的世界书条目及条目级时序状态。
 *
 * 世界书的角色作用域由角色卡关联关系在 Prompt 构建时推断；四个时序字段全为空表示
 * 当前没有有效的 sticky/cooldown 状态。
 */
@Entity(
    tableName = "story_lorebook_entries",
    primaryKeys = ["storyId", "lorebookEntryId"],
    foreignKeys = [
        ForeignKey(
            entity = Story::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LorebookEntry::class,
            parentColumns = ["id"],
            childColumns = ["lorebookEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("lorebookEntryId")
    ]
)
data class StoryLorebookEntry(
    val storyId: Long,
    val lorebookEntryId: Long,
    @ColumnInfo(defaultValue = "NULL")
    val activatedAtStep: Int? = null,
    @ColumnInfo(defaultValue = "NULL")
    val stickyUntilStep: Int? = null,
    @ColumnInfo(defaultValue = "NULL")
    val cooldownUntilStep: Int? = null,
    @ColumnInfo(defaultValue = "NULL")
    val stateSignature: String? = null
) {
    /** 清除与旧宏作用域或旧条目签名关联的时序状态。 */
    fun withoutRuntimeState(): StoryLorebookEntry {
        return copy(
            activatedAtStep = null,
            stickyUntilStep = null,
            cooldownUntilStep = null,
            stateSignature = null
        )
    }
}
