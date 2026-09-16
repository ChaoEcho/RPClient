package me.kafuuneko.rpclient.feature.main.presentation

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.libs.media.ImageSendMode
import me.kafuuneko.rpclient.libs.media.ImageSendSettings

/** 图片发送面板；输入草稿与已保存配置分离，非法输入不会影响正在准备的请求。 */
data class MainImageSendState(
    val mode: ImageSendMode = ImageSendMode.Auto,
    val maxFileKiB: String = "2048",
    val maxWidth: String = "1536",
    val maxHeight: String = "1536",
    val hasChanges: Boolean = false,
    @param:StringRes val errorResId: Int? = null
) {
    companion object {
        /**
         * 从已校验偏好创建面板草稿。
         * @param settings 当前已保存参数。
         * @return 未修改的可编辑面板状态。
         */
        fun from(settings: ImageSendSettings): MainImageSendState = MainImageSendState(
            settings.mode, settings.maxFileKiB.toString(), settings.maxWidth.toString(), settings.maxHeight.toString()
        )
    }
}

/** 自定义发送参数的编辑目标，不在 View 中判断数值合法性。 */
enum class ImageSendField { FileKiB, Width, Height }
