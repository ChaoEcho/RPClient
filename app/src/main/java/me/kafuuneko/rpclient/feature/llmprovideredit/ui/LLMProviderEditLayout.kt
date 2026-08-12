package me.kafuuneko.rpclient.feature.llmprovideredit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.CredentialEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditDialogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditLoadState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditModelCatalogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditRequestExtensionsState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogFailure
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.MIN_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.model.TokenPreset
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpPanel as Panel

/** 模型供应商创建与编辑页 Compose 入口。 */
@Composable
fun LLMProviderEditLayout(
    uiState: LLMProviderEditUiState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is LLMProviderEditUiState.Normal) { LLMProviderEditUiIntent.Back.emit() }
    when (uiState) {
        LLMProviderEditUiState.None -> Unit
        is LLMProviderEditUiState.Finished -> LLMProviderEditLayout(uiState.previous) {}
        is LLMProviderEditUiState.Normal -> {
            LLMProviderEditNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun LLMProviderEditNormal(
    state: LLMProviderEditUiState.Normal,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = if (state.mode == LLMProviderEditMode.Create) stringResource(R.string.create_model_title) else stringResource(
                R.string.edit_model_title
            ),
            onBack = { LLMProviderEditUiIntent.Back.emit() },
            actions = {
                TopBarSaveButton(state, emit)
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = if (state.mode == LLMProviderEditMode.Create) stringResource(R.string.create_model_subtitle) else state.form.name.ifBlank {
                        stringResource(
                            R.string.model_provider_title
                        )
                    },
                    subtitle = stringResource(R.string.edit_model_subtitle)
                )
            }
            item { BasicPanel(state.form, state.modelCatalogState, emit) }
            item { ProtocolPanel(state.form, emit) }
            item { RequestExtensionsPanel(state.requestExtensionsState, emit) }
            item { ParameterPanel(state.form, emit) }
            item { TestPanel(state.testState, emit) }
            item { ActionPanel(state, emit) }
        }
    }
}

@Composable
private fun BasicPanel(
    form: LLMProviderEditForm,
    modelCatalogState: LLMProviderEditModelCatalogState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.basic_info))
        FormTextField(
            stringResource(R.string.name),
            form.name
        ) { LLMProviderEditUiIntent.ChangeName(it).emit() }
        FormTextField(
            stringResource(R.string.base_url),
            form.baseUrl
        ) { LLMProviderEditUiIntent.ChangeBaseUrl(it).emit() }
        CredentialControl(
            title = stringResource(R.string.api_key),
            hasExistingValue = form.hasExistingApiKey,
            editMode = form.apiKeyEditMode,
            onEdit = { LLMProviderEditUiIntent.ShowApiKeyEditor.emit() },
            onClear = { LLMProviderEditUiIntent.ClearApiKey.emit() },
            onKeepExisting = { LLMProviderEditUiIntent.KeepExistingApiKey.emit() }
        )
        ModelField(
            value = form.model,
            catalogState = modelCatalogState,
            emit = emit
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.enabled), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.enabled_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = form.isEnabled,
                onCheckedChange = { LLMProviderEditUiIntent.ToggleEnabled(it).emit() }
            )
        }
    }
}

@Composable
private fun ModelField(
    value: String,
    catalogState: LLMProviderEditModelCatalogState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = {
                LLMProviderEditUiIntent.ChangeModel(it).emit()
            },
            label = { Text(stringResource(R.string.model_name)) },
            supportingText = {
                ModelCatalogSupportingText(catalogState, emit)
            },
            trailingIcon = {
                val loading =
                    catalogState is LLMProviderEditModelCatalogState.Loading
                IconButton(
                    onClick = {
                        if (loading) {
                            LLMProviderEditUiIntent.CancelModelQuery.emit()
                        } else {
                            LLMProviderEditUiIntent.QueryModels.emit()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (loading) {
                            Icons.Rounded.Close
                        } else {
                            Icons.Rounded.Refresh
                        },
                        contentDescription = stringResource(
                            if (loading) {
                                R.string.cancel_model_query
                            } else {
                                R.string.query_models
                            }
                        )
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ModelCatalogSupportingText(
    state: LLMProviderEditModelCatalogState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    when (state) {
        LLMProviderEditModelCatalogState.Idle -> {
            Text(stringResource(R.string.model_manual_input_hint))
        }

        LLMProviderEditModelCatalogState.Loading -> {
            Text(stringResource(R.string.querying_models))
        }

        is LLMProviderEditModelCatalogState.Loaded -> {
            if (state.models.isEmpty()) {
                Text(stringResource(R.string.no_available_models_returned))
            } else {
                Text(
                    text = stringResource(
                        R.string.available_models_found,
                        state.models.size
                    ),
                    modifier = Modifier.clickable {
                        LLMProviderEditUiIntent.ShowModelPicker.emit()
                    },
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        is LLMProviderEditModelCatalogState.Failed -> {
            Text(
                text = modelCatalogFailureText(state.failure),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun modelCatalogFailureText(failure: LLMModelCatalogFailure): String {
    return when (failure) {
        LLMModelCatalogFailure.Unauthorized -> {
            stringResource(R.string.generation_error_unauthorized)
        }

        LLMModelCatalogFailure.Forbidden -> {
            stringResource(R.string.generation_error_forbidden)
        }

        LLMModelCatalogFailure.RateLimited -> {
            stringResource(R.string.generation_error_rate_limited)
        }

        LLMModelCatalogFailure.UnsupportedEndpoint -> {
            stringResource(R.string.model_query_unsupported)
        }

        LLMModelCatalogFailure.Network -> {
            stringResource(R.string.generation_error_network)
        }

        LLMModelCatalogFailure.InvalidResponse -> {
            stringResource(R.string.model_query_invalid_response)
        }

        is LLMModelCatalogFailure.HttpFailure -> {
            stringResource(
                R.string.generation_error_http,
                failure.statusCode
            )
        }

        LLMModelCatalogFailure.Unknown -> {
            stringResource(R.string.model_query_failed)
        }
    }
}

@Composable
private fun ProtocolPanel(
    form: LLMProviderEditForm,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.protocol))
        Text(stringResource(R.string.provider_type), style = MaterialTheme.typography.titleSmall)
        EnumChipRow(
            values = LLMProviderType.entries,
            selected = form.providerType,
            label = { it.name },
            onSelect = { LLMProviderEditUiIntent.ChangeProviderType(it).emit() }
        )
        Text(stringResource(R.string.protocol), style = MaterialTheme.typography.titleSmall)
        EnumChipRow(
            values = LLMProviderProtocol.entries,
            selected = form.protocol,
            label = { it.name },
            onSelect = { LLMProviderEditUiIntent.ChangeProtocol(it).emit() }
        )
        CredentialControl(
            title = stringResource(R.string.custom_headers_json),
            hasExistingValue = form.hasExistingCustomHeaders,
            editMode = form.customHeadersEditMode,
            onEdit = { LLMProviderEditUiIntent.ShowCustomHeadersEditor.emit() },
            onClear = { LLMProviderEditUiIntent.ClearCustomHeaders.emit() },
            onKeepExisting = { LLMProviderEditUiIntent.KeepExistingCustomHeaders.emit() }
        )
    }
}

@Composable
private fun CredentialControl(
    title: String,
    hasExistingValue: Boolean,
    editMode: CredentialEditMode,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onKeepExisting: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = when (editMode) {
                CredentialEditMode.KeepExisting -> stringResource(
                    if (hasExistingValue) {
                        R.string.credential_keep_existing
                    } else {
                        R.string.credential_not_set
                    }
                )

                CredentialEditMode.Replace -> stringResource(R.string.credential_replace_on_save)
                CredentialEditMode.Clear -> stringResource(R.string.credential_clear_on_save)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) {
                Text(
                    stringResource(
                        if (hasExistingValue || editMode == CredentialEditMode.Replace) {
                            R.string.credential_replace
                        } else {
                            R.string.credential_set
                        }
                    )
                )
            }
            if (hasExistingValue) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.credential_clear))
                }
            }
            if (editMode != CredentialEditMode.KeepExisting) {
                TextButton(onClick = onKeepExisting) {
                    Text(stringResource(R.string.credential_undo_change))
                }
            }
        }
    }
}

@Composable
private fun RequestExtensionsPanel(
    state: LLMProviderEditRequestExtensionsState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.request_extensions))
        Text(
            text = stringResource(R.string.request_body_patch_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.isOpenRouter) {
            ParameterSwitchRow(
                title = stringResource(R.string.openrouter_preferred_provider),
                checked = state.usesPreferredProvider,
                onCheckedChange = {
                    LLMProviderEditUiIntent.ToggleOpenRouterPreferredProvider(it).emit()
                }
            )
            if (state.usesPreferredProvider) {
                FormTextField(
                    label = stringResource(R.string.openrouter_provider_slug),
                    value = state.preferredProvider,
                    onChange = {
                        LLMProviderEditUiIntent.ChangeOpenRouterPreferredProvider(it).emit()
                    }
                )
                ParameterSwitchRow(
                    title = stringResource(R.string.openrouter_allow_fallbacks),
                    checked = state.allowFallbacks,
                    onCheckedChange = {
                        LLMProviderEditUiIntent.ToggleOpenRouterFallbacks(it).emit()
                    }
                )
            }
            Text(
                text = stringResource(R.string.openrouter_session_affinity_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { LLMProviderEditUiIntent.ShowRequestBodyPatchDialog.emit() }
        ) {
            Text(stringResource(R.string.edit_request_body_patch))
        }
    }
}

@Composable
private fun ParameterPanel(
    form: LLMProviderEditForm,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.generation_parameters))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.temperature),
                value = form.temperature,
                modifier = Modifier.weight(1f),
                enabled = form.sendTemperature,
                keyboardType = KeyboardType.Decimal,
                onChange = { LLMProviderEditUiIntent.ChangeTemperature(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.top_p),
                value = form.topP,
                modifier = Modifier.weight(1f),
                enabled = form.sendTopP,
                keyboardType = KeyboardType.Decimal,
                onChange = { LLMProviderEditUiIntent.ChangeTopP(it).emit() }
            )
        }
        ParameterSwitchRow(
            title = stringResource(R.string.provider_send_temperature),
            checked = form.sendTemperature,
            onCheckedChange = {
                LLMProviderEditUiIntent.ToggleSendTemperature(it).emit()
            }
        )
        ParameterSwitchRow(
            title = stringResource(R.string.provider_send_top_p),
            checked = form.sendTopP,
            onCheckedChange = { LLMProviderEditUiIntent.ToggleSendTopP(it).emit() }
        )
        TokenPresetField(
            label = stringResource(R.string.max_tokens),
            value = form.maxTokens,
            onChange = { LLMProviderEditUiIntent.ChangeMaxTokens(it).emit() }
        )
        TokenPresetField(
            label = stringResource(R.string.context) + " " + stringResource(R.string.tokens),
            value = form.contextTokens,
            onChange = { LLMProviderEditUiIntent.ChangeContextTokens(it).emit() }
        )
        TokenEstimateReserveSlider(
            value = form.tokenEstimateReservePercent,
            onChange = {
                LLMProviderEditUiIntent.ChangeTokenEstimateReservePercent(it).emit()
            }
        )
        Text(
            text = stringResource(R.string.prompt_post_processing_provider_title),
            style = MaterialTheme.typography.titleSmall
        )
        EnumChipRow(
            values = PromptPostProcessingMode.entries,
            selected = form.promptPostProcessingMode,
            label = { it.name },
            onSelect = {
                LLMProviderEditUiIntent.SelectPostProcessingMode(it).emit()
            }
        )
    }
}

@Composable
private fun TokenEstimateReserveSlider(
    value: Int,
    onChange: (Int) -> Unit
) {
    val percent = value.coerceIn(
        MIN_TOKEN_ESTIMATE_RESERVE_PERCENT,
        MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.token_estimate_reserve),
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
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = MIN_TOKEN_ESTIMATE_RESERVE_PERCENT.toFloat()..
                MAX_TOKEN_ESTIMATE_RESERVE_PERCENT.toFloat()
        )
        Text(
            text = stringResource(R.string.token_estimate_reserve_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TokenPresetField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormTextField(
            label = label,
            value = value,
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number,
            onChange = onChange
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TokenPreset.entries) { preset ->
                FilterChip(
                    selected = value.toIntOrNull() == preset.value,
                    onClick = { onChange(preset.value.toString()) },
                    label = { Text(preset.displayName) }
                )
            }
        }
    }
}

@Composable
private fun ParameterSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TestPanel(
    testState: LLMProviderEditTestState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(
                icon = when (testState) {
                    is LLMProviderEditTestState.Failed -> Icons.Rounded.ErrorOutline
                    is LLMProviderEditTestState.Success -> Icons.Rounded.CloudDone
                    else -> Icons.Rounded.PlayArrow
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.model_test),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when (testState) {
                        LLMProviderEditTestState.None -> stringResource(R.string.send_short_message)
                        LLMProviderEditTestState.Testing -> stringResource(R.string.testing)
                        is LLMProviderEditTestState.Success -> testState.message
                        LLMProviderEditTestState.Failed -> stringResource(R.string.test_failed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            OutlinedButton(
                onClick = {
                    if (testState is LLMProviderEditTestState.Testing) {
                        LLMProviderEditUiIntent.CancelTest.emit()
                    } else {
                        LLMProviderEditUiIntent.TestClick.emit()
                    }
                }
            ) {
                val isTesting = testState is LLMProviderEditTestState.Testing
                Icon(
                    imageVector = if (isTesting) Icons.Rounded.Close else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (isTesting) R.string.cancel else R.string.test)
                )
                Text(stringResource(if (isTesting) R.string.cancel else R.string.test))
            }
        }
    }
}

@Composable
private fun ActionPanel(
    state: LLMProviderEditUiState.Normal,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = state.loadState !is LLMProviderEditLoadState.Saving,
        onClick = { LLMProviderEditUiIntent.SaveClick.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(
            if (state.mode == LLMProviderEditMode.Create) stringResource(R.string.create) else stringResource(
                R.string.save
            )
        )
    }
}

@Composable
private fun TopBarSaveButton(
    state: LLMProviderEditUiState.Normal,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = state.loadState !is LLMProviderEditLoadState.Saving,
        onClick = { LLMProviderEditUiIntent.SaveClick.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(
            when {
                state.loadState is LLMProviderEditLoadState.Saving -> stringResource(R.string.saving)
                state.mode == LLMProviderEditMode.Create -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

@Composable
private fun DialogSwitch(
    dialogState: LLMProviderEditDialogState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    when (dialogState) {
        LLMProviderEditDialogState.None -> Unit
        LLMProviderEditDialogState.UnsavedChangesConfirm -> AlertDialog(
            onDismissRequest = { LLMProviderEditUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = { LLMProviderEditUiIntent.ConfirmDiscardChanges.emit() }) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { LLMProviderEditUiIntent.DismissDialog.emit() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )

        LLMProviderEditDialogState.ApiKeyEditor -> SensitiveValueEditorDialog(
            title = stringResource(R.string.api_key_editor_title),
            label = stringResource(R.string.api_key),
            password = true,
            minLines = 1,
            onConfirm = { LLMProviderEditUiIntent.ConfirmApiKeyReplacement(it).emit() },
            onDismiss = { LLMProviderEditUiIntent.DismissDialog.emit() }
        )

        LLMProviderEditDialogState.CustomHeadersEditor -> SensitiveValueEditorDialog(
            title = stringResource(R.string.custom_headers_editor_title),
            label = stringResource(R.string.custom_headers_json),
            password = false,
            minLines = 4,
            noWrap = true,
            onConfirm = {
                LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement(it).emit()
            },
            onDismiss = { LLMProviderEditUiIntent.DismissDialog.emit() }
        )

        is LLMProviderEditDialogState.RequestBodyPatchEditor -> JsonObjectEditorDialog(
            initialValue = dialogState.initialValue,
            onConfirm = { LLMProviderEditUiIntent.ConfirmRequestBodyPatch(it).emit() },
            onDismiss = { LLMProviderEditUiIntent.DismissDialog.emit() }
        )

        is LLMProviderEditDialogState.ModelPicker -> ModelPickerDialog(
            state = dialogState,
            emit = emit
        )
    }
}

@Composable
private fun JsonObjectEditorDialog(
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.request_body_patch_editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.request_body_patch_editor_note),
                    style = MaterialTheme.typography.bodySmall
                )
                NoWrapCodeEditor(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.request_body_patch_json)) },
                    height = 232.dp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ModelPickerDialog(
    state: LLMProviderEditDialogState.ModelPicker,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    AlertDialog(
        onDismissRequest = { LLMProviderEditUiIntent.DismissDialog.emit() },
        title = { Text(stringResource(R.string.choose_model)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = {
                        LLMProviderEditUiIntent.ChangeModelSearch(it).emit()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.search_models)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                if (state.items.isEmpty()) {
                    Text(
                        stringResource(R.string.no_matching_models),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.items, key = { it.id }) { model ->
                            ModelPickerItem(model, emit)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = { LLMProviderEditUiIntent.DismissDialog.emit() }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ModelPickerItem(
    model: LLMAvailableModel,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    TextButton(
        onClick = {
            LLMProviderEditUiIntent.SelectAvailableModel(model.id).emit()
        },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.titleSmall
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
        model.contextTokens?.let {
            stringResource(R.string.model_context_tokens, it)
        },
        model.maxOutputTokens?.let {
            stringResource(R.string.model_max_output_tokens, it)
        }
    )
    if (metadata.isEmpty()) return
    Text(
        text = metadata.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    )
}

@Composable
private fun SensitiveValueEditorDialog(
    title: String,
    label: String,
    password: Boolean,
    minLines: Int,
    noWrap: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.credential_editor_privacy_note),
                    style = MaterialTheme.typography.bodySmall
                )
                if (noWrap) {
                    NoWrapCodeEditor(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(label) },
                        height = 176.dp
                    )
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(label) },
                        minLines = minLines,
                        visualTransformation = if (password) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value) }
            ) {
                Text(stringResource(R.string.confirm))
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
private fun NoWrapCodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    height: Dp
) {
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val horizontalPadding = 24.dp
                val verticalPadding = 24.dp
                val minimumContentWidth = (maxWidth - horizontalPadding).coerceAtLeast(0.dp)
                val minimumContentHeight = (maxHeight - verticalPadding).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                        .verticalScroll(verticalScrollState)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .widthIn(min = minimumContentWidth)
                            .heightIn(min = minimumContentHeight),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        enabled = enabled,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun <T> EnumChipRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values) { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) }
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LLMProviderEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        LLMProviderEditLayout(
            uiState = LLMProviderEditUiState.Normal(
                mode = LLMProviderEditMode.Create,
                form = LLMProviderEditForm(
                    name = "OpenRouter",
                    providerType = LLMProviderType.OpenRouter,
                    baseUrl = "https://openrouter.ai/api/v1",
                    model = "~anthropic/claude-sonnet-latest"
                ),
                requestExtensionsState = LLMProviderEditRequestExtensionsState(
                    isOpenRouter = true
                )
            ),
            emit = {}
        )
    }
}
