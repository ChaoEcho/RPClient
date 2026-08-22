package me.kafuuneko.rpclient.feature.imagecrop.presentation

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.imagecrop.model.ImageCropTransform

/** 头像裁切蒙版预览形态。 */
enum class CropMaskShape {
    Squircle,
    Circle
}

/** 图片裁剪页状态树。 */
sealed class ImageCropUiState {
    data object None : ImageCropUiState()
    data object Loading : ImageCropUiState()

    data class Normal(
        val image: ImageBitmap,
        val transform: ImageCropTransform,
        val maskShape: CropMaskShape = CropMaskShape.Squircle,
        val saving: Boolean = false
    ) : ImageCropUiState()

    data class Failed(val previous: ImageCropUiState) : ImageCropUiState()
    data class Finished(val previous: ImageCropUiState) : ImageCropUiState()
}
