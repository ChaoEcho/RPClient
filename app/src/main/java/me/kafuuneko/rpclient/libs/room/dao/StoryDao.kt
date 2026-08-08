package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.model.StoryOverview

/** Story 表的基础查询和带 revision 正文写入入口。 */
@Dao
interface StoryDao : MutableDao<Story> {
    @Query(
        """
        SELECT id,
               title,
               LENGTH(content) AS contentCharacterCount,
               SUBSTR(REPLACE(REPLACE(TRIM(content), CHAR(13), ' '), CHAR(10), ' '), 1, 160)
                   AS preview,
               latestTime
        FROM stories
        ORDER BY latestTime DESC, id DESC
        """
    )
    suspend fun getStoryOverviews(): List<StoryOverview>

    @Query("SELECT * FROM stories WHERE id = :id")
    suspend fun getStory(id: Long): Story?

    @Query(
        """
        UPDATE stories
        SET title = :title,
            latestTime = :latestTime
        WHERE id = :id
        """
    )
    suspend fun renameStory(id: Long, title: String, latestTime: Long): Int

    @Query(
        """
        UPDATE stories
        SET memory = :memory,
            summary = :summary,
            authorNote = :authorNote,
            lorebookEntrySet = :lorebookEntrySet,
            latestTime = :latestTime
        WHERE id = :id
        """
    )
    suspend fun updateStorySettings(
        id: Long,
        memory: String,
        summary: String,
        authorNote: String,
        lorebookEntrySet: String,
        latestTime: Long
    ): Int

    @Query(
        """
        UPDATE stories
        SET summary = :summary,
            latestTime = :latestTime
        WHERE id = :storyId
          AND contentRevision = :expectedContentRevision
        """
    )
    suspend fun updateSummary(
        storyId: Long,
        expectedContentRevision: Long,
        summary: String,
        latestTime: Long
    ): Int

    @Query(
        """
        UPDATE stories
        SET content = :content,
            contentRevision = contentRevision + 1,
            latestTime = :latestTime
        WHERE id = :storyId
          AND contentRevision = :expectedRevision
        """
    )
    suspend fun updateContent(
        storyId: Long,
        expectedRevision: Long,
        content: String,
        latestTime: Long
    ): Int

    @Query(
        """
        UPDATE stories
        SET content = :content,
            contentRevision = contentRevision + 1,
            worldInfoStateJson = :worldInfoStateJson,
            worldInfoGenerationStep = :worldInfoGenerationStep,
            latestTime = :latestTime
        WHERE id = :storyId
          AND contentRevision = :expectedRevision
        """
    )
    suspend fun updateGeneratedContent(
        storyId: Long,
        expectedRevision: Long,
        content: String,
        worldInfoStateJson: String,
        worldInfoGenerationStep: Int,
        latestTime: Long
    ): Int

    @Query("DELETE FROM stories WHERE id = :id")
    suspend fun deleteStory(id: Long)
}
