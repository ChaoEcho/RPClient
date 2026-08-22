package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter

/** Story 候选角色关联的基础访问接口。 */
@Dao
interface StoryCharacterDao : MutableDao<StoryCharacter> {
    /** 配置写入冲突必须中止事务，不能用 REPLACE 静默覆盖关联。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(data: List<StoryCharacter>)

    @Query(
        """
        SELECT * FROM story_characters
        WHERE storyId = :storyId
        ORDER BY sortOrder ASC, characterId ASC
        """
    )
    suspend fun getByStoryId(storyId: Long): List<StoryCharacter>

    @Query("DELETE FROM story_characters WHERE storyId = :storyId")
    suspend fun deleteByStoryId(storyId: Long)
}
