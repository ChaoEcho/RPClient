package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R

/** 数值编辑对话框的可选快捷输入项。 */
data class NumericEditQuickOption(
    val label: String,
    val value: String
)

/** 通用数值编辑对话框；快捷选项为空时只展示输入框。 */
@Composable
fun NumericEditDialog(
    title: String,
    value: String,
    decimalInput: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    quickOptions: List<NumericEditQuickOption> = emptyList()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.edit_numeric_value_title, title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(title) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimalInput) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        }
                    )
                )
                QuickOptionChips(
                    value = value,
                    options = quickOptions,
                    onValueChange = onValueChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.confirm),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun QuickOptionChips(
    value: String,
    options: List<NumericEditQuickOption>,
    onValueChange: (String) -> Unit
) {
    if (options.isEmpty()) return
    Text(
        text = stringResource(R.string.quick_select),
        style = MaterialTheme.typography.labelLarge
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = value == option.value,
                onClick = { onValueChange(option.value) },
                label = { Text(option.label) }
            )
        }
    }
}
