package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 使用与 Token 估算预留一致的滑块交互展示百分比设置。 */
@Composable
fun RpPercentageSlider(
    title: String,
    value: Int,
    helper: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: IntRange = MIN_PERCENT..MAX_PERCENT
) {
    val minimum = valueRange.first.coerceIn(MIN_PERCENT, MAX_PERCENT)
    val maximum = valueRange.last.coerceIn(minimum, MAX_PERCENT)
    val percent = value.coerceIn(minimum, maximum)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            enabled = enabled,
            valueRange = minimum.toFloat()..maximum.toFloat()
        )
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 连续浮点参数滑块，排版与 [RpPercentageSlider] 一致。
 *
 * 用于语速、音调、温度这类不适合表示为百分比的参数。
 */
@Composable
fun RpFloatSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    enabled: Boolean = true
) {
    val current = value.coerceIn(valueRange.start, valueRange.endInclusive)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "%.2f".format(current),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = current,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange
        )
        if (!helper.isNullOrBlank()) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
