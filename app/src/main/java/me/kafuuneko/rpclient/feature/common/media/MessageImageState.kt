package me.kafuuneko.rpclient.feature.common.media

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap

/** 共享图片交互状态；原始凭据只在协调器内持有。 */
data class MessageImageState(
    val draft: List<String> = emptyList(),
    val editing: List<String> = emptyList(),
    val thumbnails: Map<String, ImageBitmap?> = emptyMap(),
    val processing: Boolean = false,
    /** 图片选择或处理失败时展示的本地化提示资源；null 表示当前没有错误。 */
    @param:StringRes val errorResId: Int? = null,
    val preview: ImagePreviewState? = null
)

/** 大图查看器状态，切换或关闭时释放前一张大图引用。 */
data class ImagePreviewState(
    val ids: List<String>,
    val index: Int,
    val bitmap: ImageBitmap? = null,
    val sendVersion: Boolean = false,
    val loading: Boolean = true
)

/** 两类聊天共用的图片用户行为，由页面 Intent 包装后交给 ViewModel。 */
sealed interface MessageImageAction {
    data class Choose(val editing: Boolean = false) : MessageImageAction
    data class Picked(val uris: List<Uri>) : MessageImageAction
    data class Remove(val uuid: String, val editing: Boolean = false) : MessageImageAction
    data class Move(val uuid: String, val editing: Boolean = false) : MessageImageAction
    data class Load(val uuid: String) : MessageImageAction
    data class Preview(val ids: List<String>, val index: Int, val sendVersion: Boolean = false) : MessageImageAction
    data object CancelProcessing : MessageImageAction
    data object ClosePreview : MessageImageAction
    data object Save : MessageImageAction
    data class SaveResult(val uri: Uri) : MessageImageAction
}
