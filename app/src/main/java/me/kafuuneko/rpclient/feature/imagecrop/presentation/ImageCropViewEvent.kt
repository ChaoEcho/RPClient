package me.kafuuneko.rpclient.feature.imagecrop.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 由裁剪页宿主返回给调用页面的一次性结果。 */
sealed class ImageCropViewEvent : IViewEvent {
    data class FinishWithResult(val fileUuid: String) : ImageCropViewEvent()
}
