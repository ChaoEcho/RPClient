package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry

/** Story 世界书条目关联和条目级时序状态的基础访问接口。 */
@Dao
interface StoryLorebookEntryDao : MutableDao<StoryLorebookEntry> {
    @Query(
        """
        SELECT * FROM story_lorebook_entries
        WHERE storyId = :storyId
        ORDER BY lorebookEntryId ASC
        """
    )
    suspend fun getByStoryId(storyId: Long): List<StoryLorebookEntry>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(data: List<StoryLorebookEntry>)

    @Update
    suspend fun updateAll(data: List<StoryLorebookEntry>): Int

    @Query("DELETE FROM story_lorebook_entries WHERE storyId = :storyId")
    suspend fun deleteByStoryId(storyId: Long)
}
