package me.kafuuneko.rpclient.libs.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.prompt.model.PromptImagePreparation
import me.kafuuneko.rpclient.libs.prompt.model.UnavailablePromptImage
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import me.kafuuneko.rpclient.libs.room.model.MessageImageWithFile
import me.kafuuneko.rpclient.libs.room.model.MessageWithImages
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.FileRepository

/**
 * 共享图片处理服务。
 * - 原图保留在文件仓库，解码、方向校正与发送缓存均在 IO 线程串行执行。
 * - 候选准备时冻结发送参数，编码和缓存重建使用相同快照；可重建缓存不参与备份。
 */
class MessageImageRuntime(
    private val mContext: Context,
    private val mFiles: FileRepository,
    private val mSettings: () -> ImageSendSettings = { ImageSendSettings.decode(AppModel.imageSendSettings) }
) {
    private val mMutex = Mutex()
    private val mCache = File(mContext.cacheDir, "message-images-v1").apply { mkdirs() }

    /**
     * 请求暂存与可重建图片缓存分开；旧进程中断留下的文件延迟回收。
     *
     * @return 本次请求独占的临时 JSON 文件，调用方使用后删除。
     */
    fun createRequestFile(): File {
        val directory = File(mContext.cacheDir, "image-requests").apply { mkdirs() }
        directory.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            .forEach { it.delete() }
        return File.createTempFile("request-", ".json", directory)
    }

    /**
     * 复制选择器 URI 并验证实际图像；失败时释放暂存，调用方只保存已就绪凭据。
     *
     * @param owner 当前草稿所有者标识。
     * @param uri 系统选择器授予读取权限的资源 URI。
     * @return 已完成格式与发送版本校验的暂存凭据。
     */
    suspend fun prepare(owner: String, uri: Uri): PreparedFile {
        // 原图复制完成后仍由草稿持有，只有图像校验成功才交给页面。
        val prepared = mFiles.prepareFile(owner, uri)
        try {
            mFiles.withPreparedFile(prepared) { file ->
                mMutex.withLock { prepareVersion(prepared.file.uuid, prepared.file.hash, file) }
            }
            return prepared
        } catch (error: Throwable) {
            mFiles.releasePrepared(prepared)
            throw error
        }
    }

    /**
     * 为候选历史收集资源与受控失败，错误是否阻断发送由最终选择决定。
     *
     * @param messages 包含有序附件的候选消息列表。
     * @return 按消息 ID 组织的就绪资源和不可用原因。
     */
    suspend fun prepareCandidates(messages: List<MessageWithImages>): PromptImagePreparation =
        withContext(Dispatchers.IO) {
            val settings = mSettings()
            val ready = mutableMapOf<Long, List<LLMImageReference>>()
            val unavailable = mutableMapOf<Long, List<UnavailablePromptImage>>()
            // 按消息和附件原顺序遍历，失败项不能使纯图消息变成空消息。
            for (message in messages) {
                val images = mutableListOf<LLMImageReference>()
                val failures = mutableListOf<UnavailablePromptImage>()
                for (attachment in message.images) {
                    try {
                        images += prepareAttachment(attachment, settings)
                    } catch (error: ImageRequestException) {
                        // 超限或原图格式错误同样延迟至最终保留，旧历史被裁剪后不阻断发送。
                        if (error.failure !in setOf(ImageRequestFailure.Missing, ImageRequestFailure.InvalidImage,
                                ImageRequestFailure.OriginalUnsupported, ImageRequestFailure.OriginalTooLarge)) throw error
                        failures += UnavailablePromptImage(message.key.messageId,
                            attachment.image.imageUuid, attachment.image.position, error.failure)
                    }
                }
                if (images.isNotEmpty()) ready[message.key.messageId] = images
                if (failures.isNotEmpty()) unavailable[message.key.messageId] = failures
            }
            PromptImagePreparation(ready, unavailable)
        }

    /** 只归类明确资源 IO 错误；协程取消和未知程序错误继续传播。 */
    private suspend fun prepareAttachment(
        attachment: MessageImageWithFile,
        settings: ImageSendSettings
    ): LLMImageReference {
        val index = attachment.file ?: throw ImageRequestException(ImageRequestFailure.Missing)
        return try {
            mFiles.withFileLease(index.uuid) { original ->
                mMutex.withLock { prepareVersion(index.uuid, index.hash, original, settings) }
            }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw ImageRequestException(ImageRequestFailure.InvalidImage)
        }
    }

    /**
     * 严格准备入口供直接发送资源的调用方使用，候选裁剪使用 prepareCandidates。
     *
     * @param messages 本次需要发送的完整消息列表。
     * @return 按消息 ID 组织、保持附件顺序的发送引用。
     * @throws ImageRequestException 任一消息存在缺失或无效图片时抛出。
     */
    suspend fun references(messages: List<MessageWithImages>): Map<Long, List<LLMImageReference>> {
        val prepared = prepareCandidates(messages)
        prepared.unavailable.values.firstOrNull { it.isNotEmpty() }?.first()?.let {
            throw ImageRequestException(it.failure)
        }
        return prepared.references
    }

    /**
     * 持原图租约重新核对发送快照，回调期间缓存不会被其他图片处理回收。
     *
     * @param reference 请求准备阶段冻结的图片引用。
     * @param block 同步消费发送文件的回调，不得在回调结束后继续读取。
     * @return 回调的执行结果。
     */
    suspend fun <T> withSendFile(reference: LLMImageReference, block: (File) -> T): T =
        mFiles.withFileLease(reference.uuid) { original ->
            val index = requireNotNull(mFiles.getFileEntity(reference.uuid)) { "Missing history image" }
            mMutex.withLock {
                val current = prepareVersion(index.uuid, index.hash, original, reference.sendSettings)
                require(current == reference) { "The image send version changed, retry" }
                block(File(mCache, current.cacheKey))
            }
        }

    /**
     * 按需加载列表缩略图或查看器版本；失败返回 null，由 UI 显示缺图占位。
     *
     * @param uuid 原图文件 ID。
     * @param prepared 未提交草稿的凭据；历史图片传 null。
     * @param longEdge 解码结果的最大长边像素数。
     * @return 有界解码后的图片；文件不可用时返回 null，取消继续传播。
     */
    suspend fun load(uuid: String, prepared: PreparedFile? = null, longEdge: Int = 384): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (prepared != null) mFiles.withPreparedFile(prepared) { decode(it, longEdge) }
                else mFiles.withFileLease(uuid) { decode(it, longEdge) }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
        }

    /**
     * 校验原始文件并生成保存元数据，不信任选图来源声明的 MIME。
     *
     * @param uuid 原图文件 ID。
     * @param prepared 尚未持久化的草稿凭据；历史图片传 null。
     * @return 与原始字节格式一致的 MIME 和带扩展名的默认文件名。
     * @throws ImageRequestException 原图格式无效或文件缺失时抛出。
     */
    suspend fun exportMetadata(uuid: String, prepared: PreparedFile? = null): ImageExportMetadata {
        // 在文件仓库保护范围内检查实际容器，不使用发送缓存的编码格式。
        val inspect: suspend (File) -> ImageExportMetadata = { original ->
            validate(original)
            val mimeType = bounds(original).outMimeType
            val extension = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic" -> "heic"
                "image/heif" -> "heif"
                else -> throw ImageRequestException(ImageRequestFailure.InvalidImage)
            }
            ImageExportMetadata(mimeType, "image.$extension")
        }
        // 草稿与历史文件沿用各自的所有权保护，IO 由文件仓库调度。
        return if (prepared != null) mFiles.withPreparedFile(prepared, inspect)
        else mFiles.withFileLease(uuid, inspect)
    }

    /**
     * 用户显式保存图片时复制原始字节，不导出缓存或内部路径。
     *
     * @param uuid 选择器启动前冻结的原图文件 ID。
     * @param destination 用户确认的目标文档 URI。
     * @param prepared 未提交草稿的凭据；历史图片传 null。
     */
    suspend fun save(uuid: String, destination: Uri, prepared: PreparedFile? = null) {
        val copy: suspend (File) -> Unit = { original ->
            val output = mContext.contentResolver.openOutputStream(destination)
                ?: error("Could not save the image")
            output.use { target -> original.inputStream().use { it.copyTo(target) } }
        }
        if (prepared != null) mFiles.withPreparedFile(prepared, copy)
        else mFiles.withFileLease(uuid, copy)
    }

    /**
     * 预览真实编码后的发送缓存，与请求使用相同压缩和尺寸。
     *
     * @param uuid 原图文件 ID。
     * @param prepared 未提交草稿的凭据；历史图片传 null。
     * @return 发送缓存的有界预览；解码失败时为 null。
     */
    suspend fun loadSendPreview(uuid: String, prepared: PreparedFile?): Bitmap? = withContext(Dispatchers.IO) {
        val read: suspend (File, String) -> Bitmap? = { original, hash ->
            mMutex.withLock {
                val reference = prepareVersion(uuid, hash, original)
                decode(File(mCache, reference.cacheKey), MessageImagePolicy.SEND_LONG_EDGE)
            }
        }
        if (prepared != null) mFiles.withPreparedFile(prepared) { read(it, prepared.file.hash) }
        else mFiles.withFileLease(uuid) { read(it, requireNotNull(mFiles.getFileEntity(uuid)).hash) }
    }

    /** 按冻结策略生成发送版本；缓存发布前核对格式、尺寸与实际字节数。 */
    private suspend fun prepareVersion(
        uuid: String,
        hash: String,
        original: File,
        settings: ImageSendSettings = mSettings()
    ): LLMImageReference {
        require(settings.isValid()) { "Invalid image send settings" }
        val key = "$hash-${settings.cacheSuffix}"
        val target = File(mCache, key)
        if (!target.isFile) {
            validate(original)
            val temporary = File(mCache, UUID.randomUUID().toString())
            try {
                // 原图不能静默转码；压缩副本按校正方向后的边界生成。
                if (settings.mode == ImageSendMode.Original) copyOriginal(original, temporary)
                else encodeCompressed(original, temporary, settings)
                currentCoroutineContext().ensureActive()
                if (!temporary.renameTo(target)) throw IOException("Could not cache the image")
            } finally {
                temporary.delete()
            }
        }
        trimCache(target)
        val bounds = bounds(target)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageRequestException(ImageRequestFailure.InvalidImage)
        // 原图保留 EXIF，Token 估算使用与预览相同的正向尺寸。
        val rotation = runCatching { ExifInterface(target).rotationDegrees }.getOrDefault(0)
        val swap = rotation == 90 || rotation == 270
        return LLMImageReference(uuid, key, bounds.outMimeType,
            if (swap) bounds.outHeight else bounds.outWidth,
            if (swap) bounds.outWidth else bounds.outHeight, target.length(), settings)
    }

    /** 保留原始字节及元数据，只允许各协议共同支持的静态格式和客户端发送上限。 */
    private fun copyOriginal(original: File, target: File) {
        val bounds = bounds(original)
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
            throw ImageRequestException(ImageRequestFailure.OriginalUnsupported)
        }
        if (original.length() > MessageImagePolicy.MAX_SEND_BYTES ||
            maxOf(bounds.outWidth, bounds.outHeight) > 8000) {
            throw ImageRequestException(ImageRequestFailure.OriginalTooLarge)
        }
        original.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    /** 透明图片保留 Alpha；逐步缩小高噪声图片，直到实际文件满足用户上限。 */
    private suspend fun encodeCompressed(original: File, target: File, settings: ImageSendSettings) {
        // 自动模式保留既有采样和质量，自定义模式额外施加宽高包围框。
        val edge = if (settings.mode == ImageSendMode.Custom) maxOf(settings.maxWidth, settings.maxHeight)
            else MessageImagePolicy.SEND_LONG_EDGE
        var bitmap = decode(original, edge) ?: throw ImageRequestException(ImageRequestFailure.InvalidImage)
        try {
            val (width, height) = settings.fitDimensions(bitmap.width, bitmap.height)
            if (width != bitmap.width || height != bitmap.height) {
                val scaled = bitmap.scale(width, height)
                bitmap.recycle()
                bitmap = scaled
            }
            // 不依赖压缩质量与文件大小单调对应，使用真实编码结果检查预算。
            while (true) {
                currentCoroutineContext().ensureActive()
                FileOutputStream(target).use { output ->
                    val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    if (!bitmap.compress(format, 85, output)) throw ImageRequestException(ImageRequestFailure.InvalidImage)
                }
                if (target.length() <= settings.maxBytes) break
                if (maxOf(bitmap.width, bitmap.height) <= 128) throw ImageRequestException(ImageRequestFailure.InvalidImage)
                val scaled = bitmap.scale(
                    (bitmap.width * 0.75).roundToInt().coerceAtLeast(1),
                    (bitmap.height * 0.75).roundToInt().coerceAtLeast(1)
                )
                bitmap.recycle()
                bitmap = scaled
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** 保留最近使用的缓存，正在消费的目标始终排除在回收范围之外。 */
    private fun trimCache(target: File) {
        target.setLastModified(System.currentTimeMillis())
        var cacheBytes = mCache.listFiles().orEmpty().sumOf { it.length() }
        mCache.listFiles().orEmpty().filter { it != target }.sortedBy { it.lastModified() }.forEach {
            if (cacheBytes > 64L * 1024 * 1024) {
                val size = it.length()
                if (it.delete()) cacheBytes -= size
            }
        }
    }

    /** 通过解码器与容器结构校验真实格式，禁止伪 MIME、动画和超大像素。 */
    private fun validate(file: File) {
        requireValidImage(file.length() <= MessageImagePolicy.MAX_ORIGINAL_BYTES)
        val bounds = bounds(file)
        requireValidImage(bounds.outWidth > 0 && bounds.outHeight > 0)
        requireValidImage(bounds.outWidth.toLong() * bounds.outHeight <= MessageImagePolicy.MAX_ORIGINAL_PIXELS)
        requireValidImage(bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"))
        // PNG / WebP 按容器块跳转检查动画标记，不将压缩像素误识别成块头。
        RandomAccessFile(file, "r").use { input ->
            var offset = if (bounds.outMimeType == "image/png") 8L else 12L
            while (bounds.outMimeType in setOf("image/png", "image/webp") && offset + 8 <= input.length()) {
                input.seek(offset)
                val png = bounds.outMimeType == "image/png"
                val size = if (png) input.readInt().toLong() and 0xffffffffL else 0L
                val name = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                val length = if (png) size else Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                requireValidImage(name !in setOf("acTL", "ANIM", "ANMF"))
                offset += 8 + length + if (png) 4 else length % 2
            }
        }
    }

    /** 解码和格式限制属于受控资源错误，不能与程序性 require 混淆。 */
    private fun requireValidImage(valid: Boolean) {
        if (!valid) throw ImageRequestException(ImageRequestFailure.InvalidImage)
    }

    /** 读取像素信息，不分配整图内存。 */
    private fun bounds(file: File): BitmapFactory.Options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }

    /** 有界采样后应用全部 EXIF 镜像及旋转方向，最后精确限制长边。 */
    private fun decode(file: File, edge: Int): Bitmap? {
        val bounds = bounds(file)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        // 自定义上限可达 4096 px，额外约束采样像素，避免原图及旋转副本同时撑满堆内存。
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2 ||
            bounds.outWidth.toLong() * bounds.outHeight / sample / sample > 4096L * 4096) {
            sample *= 2
        }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
        }) ?: return null
        // ExifInterface 统一表示旋转和水平翻转，涵盖八种方向。
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val matrix = Matrix().apply {
            if (exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        bitmap = oriented
        val ratio = edge.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (ratio >= 1f) return bitmap
        val scaled = bitmap.scale(
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
