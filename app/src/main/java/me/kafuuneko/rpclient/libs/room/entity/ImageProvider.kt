package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 持久化的图片生成服务配置。 */
@Entity(tableName = "image_providers")
data class ImageProvider(
    // 图片服务主键
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 展示名称
    val name: String,
    // 接口基础地址
    val baseUrl: String,
    // API Key，留空表示尚未配置
    val apiKey: String = "",
    // 生成模型名
    val model: String,
    // 出图尺寸
    val size: String = DEFAULT_IMAGE_PROVIDER_SIZE,
    // 此服务同时执行的最大出图请求数
    val maxConcurrentRequests: Int = DEFAULT_IMAGE_PROVIDER_CONCURRENCY,
    // 创建时间
    val createTime: Long = System.currentTimeMillis(),
    // 更新时间
    val updateTime: Long = createTime
)

const val DEFAULT_IMAGE_PROVIDER_BASE_URL = "https://api.openai.com/v1"
const val DEFAULT_IMAGE_PROVIDER_SIZE = "1024x1024"

const val MIN_IMAGE_PROVIDER_CONCURRENCY = 1
const val MAX_IMAGE_PROVIDER_CONCURRENCY = 8
const val DEFAULT_IMAGE_PROVIDER_CONCURRENCY = 1

/** 图片服务的并发配额键，与对话模型的 `llm-provider:{id}` 同构。 */
fun imageProviderPermitKey(providerId: Long) = "image-provider:$providerId"
