package me.kafuuneko.rpclient.model

/**
 * 原图坐标系中的正方形裁剪选区。
 *
 * 中心点按变换后原图宽高归一化，边长按原图短边归一化，使选区与具体解码尺寸无关。
 */
data class SquareCropSelection(
    val centerX: Float,
    val centerY: Float,
    val sizeFractionOfShortEdge: Float,
    val rotationDegrees: Int = 0,
    val isFlippedHorizontal: Boolean = false
)