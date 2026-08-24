package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.StoryVolume

/** Story 分卷的基础查询和排序写入接口。 */
@Dao
interface StoryVolumeDao : MutableDao<StoryVolume> {
    /** 分卷主键冲突表示调用方快照无效，不能用 REPLACE 级联影响章节。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(data: StoryVolume): Long

    @Query(
        """
        SELECT * FROM story_volumes
        WHERE storyId = :storyId
        ORDER BY sortOrder ASC, id ASC
        """
    )
    suspend fun getByStoryId(storyId: Long): List<StoryVolume>

    @Query("SELECT * FROM story_volumes WHERE id = :id")
    suspend fun getById(id: Long): StoryVolume?

    @Query(
        """
        UPDATE story_volumes
        SET title = :title
        WHERE id = :id AND storyId = :storyId
        """
    )
    suspend fun rename(id: Long, storyId: Long, title: String): Int

    @Query(
        """
        UPDATE story_volumes
        SET sortOrder = :sortOrder
        WHERE id = :id AND storyId = :storyId
        """
    )
    suspend fun updateSortOrder(id: Long, storyId: Long, sortOrder: Int): Int

    @Query("DELETE FROM story_volumes WHERE id = :id AND storyId = :storyId")
    suspend fun deleteById(id: Long, storyId: Long): Int
}
