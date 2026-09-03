package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogFailure
import me.kafuuneko.rpclient.libs.llm.catalog.ModelCatalogState
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone

/**
 * 模型名输入框 + 在线目录拉取。
 *
 * 手填始终可用，右侧 ↻ 触发目录查询、查询中变为取消。对话模型与图片服务共用同一交互。
 */
@Composable
fun RpModelNameField(
    value: String,
    catalogState: ModelCatalogState,
    onValueChange: (String) -> Unit,
    onQueryModels: () -> Unit,
    onCancelQuery: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.model_name)) },
            trailingIcon = {
                val loading = catalogState is ModelCatalogState.Loading
                IconButton(onClick = { if (loading) onCancelQuery() else onQueryModels() }) {
                    Icon(
                        imageVector = if (loading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = stringResource(
                            if (loading) R.string.cancel_model_query else R.string.query_models
                        )
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
        ModelCatalogSupportingView(catalogState, onOpenPicker)
    }
}

@Composable
private fun ModelCatalogSupportingView(state: ModelCatalogState, onOpenPicker: () -> Unit) {
    when (state) {
        ModelCatalogState.Idle -> Text(
            text = stringResource(R.string.model_manual_input_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 4.dp)
        )

        ModelCatalogState.Loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.querying_models),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        is ModelCatalogState.Loaded -> if (state.models.isEmpty()) {
            Text(
                text = stringResource(R.string.no_available_models_returned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenPicker() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(
                            R.string.available_models_found,
                            state.models.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        is ModelCatalogState.Failed -> Text(
            text = modelCatalogFailureText(state.failure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun modelCatalogFailureText(failure: LLMModelCatalogFailure): String = when (failure) {
    LLMModelCatalogFailure.Unauthorized -> stringResource(R.string.generation_error_unauthorized)
    LLMModelCatalogFailure.Forbidden -> stringResource(R.string.generation_error_forbidden)
    LLMModelCatalogFailure.RateLimited -> stringResource(R.string.generation_error_rate_limited)
    LLMModelCatalogFailure.UnsupportedEndpoint -> stringResource(R.string.model_query_unsupported)
    LLMModelCatalogFailure.Network -> stringResource(R.string.generation_error_network)
    LLMModelCatalogFailure.InvalidResponse -> stringResource(R.string.model_query_invalid_response)
    is LLMModelCatalogFailure.HttpFailure -> stringResource(
        R.string.generation_error_http,
        failure.statusCode
    )

    LLMModelCatalogFailure.Unknown -> stringResource(R.string.model_query_failed)
}

/**
 * 可用模型选择弹窗。
 *
 * 搜索词是纯展示态，留在弹窗内部；页面 UiState 只需要保存待选列表。
 */
@Composable
fun RpModelPickerDialog(
    models: List<LLMAvailableModel>,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val visibleModels = remember(models, searchQuery) {
        val keyword = searchQuery.trim()
        if (keyword.isEmpty()) {
            models
        } else {
            models.filter {
                it.id.contains(keyword, ignoreCase = true) ||
                        it.displayName.contains(keyword, ignoreCase = true)
            }
        }
    }
    AppDialogScaffold(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.choose_model),
        badgeIcon = Icons.Rounded.Search,
        badgeTone = DialogBadgeTone.Primary,
        confirmText = "",
        onConfirm = null,
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_models)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            if (visibleModels.isEmpty()) {
                Text(
                    stringResource(R.string.no_matching_models),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visibleModels, key = { it.id }) { model ->
                        ModelPickerItem(model, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPickerItem(model: LLMAvailableModel, onSelect: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(model.id) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (model.displayName != model.id) {
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            ModelMetadataText(model)
        }
    }
}

@Composable
private fun ModelMetadataText(model: LLMAvailableModel) {
    val metadata = listOfNotNull(
        model.contextTokens?.let { stringResource(R.string.model_context_tokens, it) },
        model.maxOutputTokens?.let { stringResource(R.string.model_max_output_tokens, it) }
    )
    if (metadata.isEmpty()) return
    Text(
        text = metadata.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    )
}
