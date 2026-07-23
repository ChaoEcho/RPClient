package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.entity.RegexCharacterAuthorization
import me.kafuuneko.rpclient.libs.room.entity.RegexScriptEntity

/** Regex 脚本和角色授权的数据库访问入口。 */
@Dao
interface RegexScriptDao {
    @Query(
        """
        SELECT * FROM regex_scripts
        WHERE characterId IS NULL
        ORDER BY sortOrder ASC, rowId ASC
        """
    )
    suspend fun getGlobalScripts(): List<RegexScriptEntity>

    @Query(
        """
        SELECT * FROM regex_scripts
        WHERE characterId = :characterId
        ORDER BY sortOrder ASC, rowId ASC
        """
    )
    suspend fun getCharacterScripts(characterId: Long): List<RegexScriptEntity>

    @Query(
        """
        SELECT * FROM regex_scripts
        WHERE characterId IN (:characterIds)
        ORDER BY characterId ASC, sortOrder ASC, rowId ASC
        """
    )
    suspend fun getCharacterScripts(characterIds: List<Long>): List<RegexScriptEntity>

    @Insert
    suspend fun insertScripts(scripts: List<RegexScriptEntity>)

    @Query("DELETE FROM regex_scripts WHERE characterId IS NULL")
    suspend fun deleteGlobalScripts()

    @Query("DELETE FROM regex_scripts WHERE characterId = :characterId")
    suspend fun deleteCharacterScripts(characterId: Long)

    @Query(
        """
        SELECT characterId FROM regex_character_authorizations
        WHERE characterId IN (:characterIds)
        """
    )
    suspend fun getAuthorizedCharacterIds(characterIds: List<Long>): List<Long>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM regex_character_authorizations
            WHERE characterId = :characterId
        )
        """
    )
    suspend fun isCharacterAuthorized(characterId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun authorizeCharacters(authorizations: List<RegexCharacterAuthorization>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun authorizeCharacter(authorization: RegexCharacterAuthorization)

    @Query(
        """
        DELETE FROM regex_character_authorizations
        WHERE characterId = :characterId
        """
    )
    suspend fun revokeCharacterAuthorization(characterId: Long)

}
