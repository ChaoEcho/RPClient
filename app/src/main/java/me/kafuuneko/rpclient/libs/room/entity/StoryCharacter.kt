package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Story 与候选角色卡的关联。
 *
 * 关联只保存 Story 专属的激活方式和顺序，不复制角色卡正文；角色名称始终以角色卡为准。
 */
@Entity(
    tableName = "story_characters",
    primaryKeys = ["storyId", "characterId"],
    foreignKeys = [
        ForeignKey(
            entity = Story::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("characterId"),
        Index(value = ["storyId", "sortOrder"])
    ]
)
data class StoryCharacter(
    val storyId: Long,
    val characterId: Long,
    val sortOrder: Int,
    val activationMode: Int = ACTIVATION_AUTO
) {
    companion object {
        const val ACTIVATION_PRIMARY = 0
        const val ACTIVATION_ALWAYS = 1
        const val ACTIVATION_AUTO = 2


        fun isValidActivationMode(value: Int): Boolean {
            return value == ACTIVATION_ALWAYS ||
                value == ACTIVATION_AUTO ||
                value == ACTIVATION_PRIMARY
        }
    }
}
