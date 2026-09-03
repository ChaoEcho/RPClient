package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ImageProvider

/**
 * 图片生成服务配置的业务入口。
 *
 * 结构对齐 [LLMRepository]：一张表 + 一个"当前选中"的 Kotpref 主键。
 */
class ImageProviderRepository(mAppDatabase: AppDatabase) {

    private val mImageProviderDao = mAppDatabase.getImageProviderDao()

    /** 读取全部图片服务，必要时先完成首次播种。 */
    suspend fun getAllProviders(): List<ImageProvider> {
        ensureDefaultProvider()
        return mImageProviderDao.getAllProviders()
    }

    suspend fun getProviderById(id: Long): ImageProvider? = mImageProviderDao.getProviderById(id)

    /**
     * 获取当前选中的图片服务。
     *
     * 当前项缺失或已被删除时回退到第一条，避免配图因为一次误删彻底不可用。
     */
    suspend fun getSelectedProvider(): ImageProvider? {
        ensureDefaultProvider()
        AppModel.currentImageProvider
            .takeIf { it != 0L }
            ?.let { mImageProviderDao.getProviderById(it) }
            ?.let { return it }
        return mImageProviderDao.getAllProviders().firstOrNull()?.also {
            AppModel.currentImageProvider = it.id
        }
    }

    /** 保存图片服务，新建时自动成为当前项。 */
    suspend fun saveProvider(provider: ImageProvider): Long {
        val now = System.currentTimeMillis()
        val nextProvider = provider.copy(updateTime = now)
        return if (provider.id == 0L) {
            mImageProviderDao.insertOrReplace(nextProvider.copy(createTime = now)).also { newId ->
                if (AppModel.currentImageProvider == 0L) AppModel.currentImageProvider = newId
            }
        } else {
            mImageProviderDao.update(nextProvider)
            provider.id
        }
    }

    fun updateCurrentProvider(id: Long) {
        AppModel.currentImageProvider = id
    }

    /** 删除图片服务，当前项指向它时顺延到剩余的第一条。 */
    suspend fun deleteProvider(id: Long) {
        mImageProviderDao.deleteProviderById(id)
        AppModel.imageProvidersInitialized = true
        if (AppModel.currentImageProvider == id) {
            AppModel.currentImageProvider = mImageProviderDao.getAllProviders().firstOrNull()?.id ?: 0L
        }
    }

    /**
     * 首次运行时把旧版存在 Kotpref 里的单条图片配置播种成第一条记录。
     *
     * Kotpref 不在数据库里，Room 自动迁移看不到它，只能在这里手动搬一次，
     * 否则升级用户会发现原本能用的图片服务凭空消失。
     */
    suspend fun ensureDefaultProvider() {
        if (AppModel.imageProvidersInitialized) return
        if (mImageProviderDao.getAllProviders().isNotEmpty()) {
            AppModel.imageProvidersInitialized = true
            return
        }
        val seeded = ImageProvider(
            name = AppModel.imageGenerationBaseUrl.toProviderName(),
            baseUrl = AppModel.imageGenerationBaseUrl,
            apiKey = AppModel.imageGenerationApiKey,
            model = AppModel.imageGenerationModel,
            size = AppModel.imageGenerationSize,
            maxConcurrentRequests = AppModel.imageGenerationMaxConcurrentRequests
        )
        AppModel.currentImageProvider = mImageProviderDao.insertOrReplace(seeded)
        AppModel.imageProvidersInitialized = true
    }
}

/** 旧配置没有名称字段，用主机名兜底，用户可以在编辑页改。 */
private fun String.toProviderName(): String =
    substringAfter("://").substringBefore('/').ifBlank { "Image Service" }
