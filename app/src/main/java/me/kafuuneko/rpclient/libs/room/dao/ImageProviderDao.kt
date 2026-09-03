package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.ImageProvider

/** 图片生成服务配置的数据库访问接口。 */
@Dao
interface ImageProviderDao : MutableDao<ImageProvider> {
    /** 按创建顺序读取全部图片服务。 */
    @Query("SELECT * FROM image_providers ORDER BY id ASC")
    suspend fun getAllProviders(): List<ImageProvider>

    /** 根据主键读取图片服务。 */
    @Query("SELECT * FROM image_providers WHERE id = :id")
    suspend fun getProviderById(id: Long): ImageProvider?

    /** 根据主键删除图片服务。 */
    @Query("DELETE FROM image_providers WHERE id = :id")
    suspend fun deleteProviderById(id: Long)
}
