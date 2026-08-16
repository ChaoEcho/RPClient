package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.CharacterLLMProviderAssociation

/** 角色与回复模型配置关联的基础访问接口。 */
@Dao
interface CharacterLLMProviderAssociationDao : MutableDao<CharacterLLMProviderAssociation> {
    /** 获取角色显式绑定的模型配置 ID；没有记录时返回 null。 */
    @Query(
        """
        SELECT llmProviderId FROM character_llm_provider_associations
        WHERE characterId = :characterId
        """
    )
    suspend fun getLLMProviderId(characterId: Long): Long?

    /** 删除角色的显式模型配置绑定，使其恢复跟随全局设置。 */
    @Query("DELETE FROM character_llm_provider_associations WHERE characterId = :characterId")
    suspend fun deleteByCharacterId(characterId: Long)

    /** 删除指定模型配置的全部角色绑定。 */
    @Query(
        """
        DELETE FROM character_llm_provider_associations
        WHERE llmProviderId = :providerId
        """
    )
    suspend fun deleteByLLMProviderId(providerId: Long)

    /** 统计显式绑定到指定模型配置的角色数量。 */
    @Query(
        """
        SELECT COUNT(*) FROM character_llm_provider_associations
        WHERE llmProviderId = :providerId
        """
    )
    suspend fun countByLLMProviderId(providerId: Long): Int
}
