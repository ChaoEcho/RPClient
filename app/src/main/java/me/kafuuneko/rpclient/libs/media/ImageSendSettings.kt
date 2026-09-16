package me.kafuuneko.rpclient.libs.media

import kotlin.math.roundToInt
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy

/** 图片发送模式；持久化使用稳定标识，不依赖枚举顺序。 */
enum class ImageSendMode(val persistedValue: String) {
    Auto("auto"), Original("original"), Custom("custom")
}

/**
 * 图片发送参数的不可变快照。
 * - 自定义参数仅影响发送副本，原图模式不隐式转码或缩放。
 * - 单个偏好值原子保存全部字段，候选准备及缓存重建复用同一份快照。
 */
data class ImageSendSettings(
    val mode: ImageSendMode = ImageSendMode.Auto,
    val maxFileKiB: Int = 2048,
    val maxWidth: Int = MessageImagePolicy.SEND_LONG_EDGE,
    val maxHeight: Int = MessageImagePolicy.SEND_LONG_EDGE
) {
    /** 当前模式实际允许的文件字节数，始终不超过客户端硬上限。 */
    val maxBytes: Long
        get() = if (mode == ImageSendMode.Custom) maxFileKiB * 1024L else MessageImagePolicy.MAX_SEND_BYTES

    /** 显式版本号同时隔离旧编码算法、模式和全部压缩参数。 */
    val cacheSuffix: String
        get() = "send-v2-${mode.persistedValue}-$maxFileKiB-$maxWidth-$maxHeight"

    /**
     * 检查完整参数的安全范围。
     * @return 是否可以安全用于解码、缩放和偏好保存。
     */
    fun isValid(): Boolean = maxFileKiB in 64..2048 && maxWidth in 128..4096 && maxHeight in 128..4096

    /**
     * 序列化完整发送参数以供原子保存。
     * @return 不含路径或用户内容的完整偏好值。
     */
    fun encode(): String = "${mode.persistedValue}:$maxFileKiB:$maxWidth:$maxHeight"

    /**
     * 按方向校正后的宽高等比缩小；不放大、不裁切。
     * @param width 正向像素宽度。
     * @param height 正向像素高度。
     * @return 同时满足宽高上限的目标尺寸。
     */
    fun fitDimensions(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && isValid()) { "Invalid image dimensions or settings" }
        if (mode == ImageSendMode.Original) return width to height
        val limitWidth = if (mode == ImageSendMode.Custom) maxWidth else MessageImagePolicy.SEND_LONG_EDGE
        val limitHeight = if (mode == ImageSendMode.Custom) maxHeight else MessageImagePolicy.SEND_LONG_EDGE
        val ratio = minOf(1.0, limitWidth.toDouble() / width, limitHeight.toDouble() / height)
        return (width * ratio).roundToInt().coerceAtLeast(1) to
            (height * ratio).roundToInt().coerceAtLeast(1)
    }

    companion object {
        /**
         * 读取完整偏好；未知版本、越界和损坏值回退自动模式。
         * @param value 已持久化的轻量参数串。
         * @return 经过边界校验的发送参数。
         */
        fun decode(value: String): ImageSendSettings {
            val parts = value.split(':')
            if (parts.size != 4) return ImageSendSettings()
            val mode = ImageSendMode.entries.firstOrNull { it.persistedValue == parts[0] }
                ?: return ImageSendSettings()
            return ImageSendSettings(
                mode, parts[1].toIntOrNull() ?: return ImageSendSettings(),
                parts[2].toIntOrNull() ?: return ImageSendSettings(),
                parts[3].toIntOrNull() ?: return ImageSendSettings()
            ).takeIf { it.isValid() } ?: ImageSendSettings()
        }
    }
}
