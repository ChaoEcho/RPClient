package me.kafuuneko.rpclient.feature.imageprovideredit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditDialogState
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditForm
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditUiIntent
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditUiState
import me.kafuuneko.rpclient.libs.llm.catalog.ModelCatalogState
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpFormTextField
import me.kafuuneko.rpclient.ui.widgets.RpModelNameField
import me.kafuuneko.rpclient.ui.widgets.RpModelPickerDialog
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

/** 图片服务创建与编辑页 Compose 入口。 */
@Composable
fun ImageProviderEditLayout(
    uiState: ImageProviderEditUiState,
    emit: ImageProviderEditUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is ImageProviderEditUiState.Normal) {
        ImageProviderEditUiIntent.Back.emit()
    }
    when (uiState) {
        ImageProviderEditUiState.None -> Unit
        is ImageProviderEditUiState.Finished -> ImageProviderEditLayout(uiState.previous) {}
        is ImageProviderEditUiState.Normal -> {
            ImageProviderEditNormal(uiState, emit)
            ImageProviderEditDialog(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun ImageProviderEditNormal(
    state: ImageProviderEditUiState.Normal,
    emit: ImageProviderEditUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(
                if (state.isCreateMode) {
                    R.string.create_image_provider
                } else {
                    R.string.edit_image_provider
                }
            ),
            onBack = { ImageProviderEditUiIntent.Back.emit() },
            actions = { TopBarSaveButton(state, emit) }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = if (state.isCreateMode) {
                        stringResource(R.string.create_image_provider)
                    } else {
                        state.form.name.ifBlank { stringResource(R.string.image_provider_title) }
                    },
                    subtitle = stringResource(R.string.image_provider_edit_subtitle)
                )
            }
            item { ServicePanel(state.form, state.modelCatalogState, emit) }
            item { ParamsPanel(state.form, emit) }
        }
    }
}

@Composable
private fun ServicePanel(
    form: ImageProviderEditForm,
    modelCatalogState: ModelCatalogState,
    emit: ImageProviderEditUiIntent.() -> Unit
) {
    RpPanel {
        RpSectionHeader(title = stringResource(R.string.image_generation_service_section))
        RpFormTextField(
            value = form.name,
            label = stringResource(R.string.name),
            onValueChange = { ImageProviderEditUiIntent.ChangeName(it).emit() }
        )
        RpFormTextField(
            value = form.baseUrl,
            label = stringResource(R.string.image_generation_base_url),
            onValueChange = { ImageProviderEditUiIntent.ChangeBaseUrl(it).emit() },
            keyboardType = KeyboardType.Uri
        )
        RpFormTextField(
            value = form.apiKey,
            label = stringResource(R.string.image_generation_api_key),
            onValueChange = { ImageProviderEditUiIntent.ChangeApiKey(it).emit() },
            password = true
        )
        RpModelNameField(
            value = form.model,
            catalogState = modelCatalogState,
            onValueChange = { ImageProviderEditUiIntent.ChangeModel(it).emit() },
            onQueryModels = { ImageProviderEditUiIntent.QueryModels.emit() },
            onCancelQuery = { ImageProviderEditUiIntent.CancelModelQuery.emit() },
            onOpenPicker = { ImageProviderEditUiIntent.ShowModelPicker.emit() }
        )
    }
}

@Composable
private fun ParamsPanel(
    form: ImageProviderEditForm,
    emit: ImageProviderEditUiIntent.() -> Unit
) {
    RpPanel {
        RpSectionHeader(title = stringResource(R.string.image_generation_params_section))
        RpFormTextField(
            value = form.size,
            label = stringResource(R.string.image_generation_size),
            onValueChange = { ImageProviderEditUiIntent.ChangeSize(it).emit() },
            imeAction = ImeAction.Next
        )
        RpFormTextField(
            value = form.maxConcurrentRequests,
            label = stringResource(R.string.image_generation_max_concurrent_requests),
            supportingText = stringResource(
                R.string.image_generation_max_concurrent_requests_desc
            ),
            onValueChange = {
                ImageProviderEditUiIntent.ChangeMaxConcurrentRequests(it).emit()
            },
            keyboardType = KeyboardType.Number
        )
    }
}

@Composable
private fun TopBarSaveButton(
    state: ImageProviderEditUiState.Normal,
    emit: ImageProviderEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = !state.isSaving,
        onClick = { ImageProviderEditUiIntent.SaveClick.emit() }
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            when {
                state.isSaving -> stringResource(R.string.saving)
                state.isCreateMode -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

@Composable
private fun ImageProviderEditDialog(
    dialogState: ImageProviderEditDialogState,
    emit: ImageProviderEditUiIntent.() -> Unit
) {
    when (dialogState) {
        ImageProviderEditDialogState.None -> Unit

        ImageProviderEditDialogState.UnsavedChangesConfirm -> AppDangerDialog(
            onDismissRequest = { ImageProviderEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.unsaved_changes_title),
            message = stringResource(R.string.unsaved_changes_message),
            confirmText = stringResource(R.string.discard_changes),
            onConfirm = { ImageProviderEditUiIntent.DiscardChanges.emit() }
        )

        is ImageProviderEditDialogState.ModelPicker -> RpModelPickerDialog(
            models = dialogState.items,
            onDismissRequest = { ImageProviderEditUiIntent.DismissDialog.emit() },
            onSelect = { ImageProviderEditUiIntent.SelectAvailableModel(it).emit() }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImageProviderEditPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        ImageProviderEditLayout(
            uiState = ImageProviderEditUiState.Normal(
                isCreateMode = true,
                form = ImageProviderEditForm(
                    name = "OpenAI",
                    baseUrl = "https://api.openai.com/v1",
                    model = "gpt-image-2",
                    size = "1024x1024",
                    maxConcurrentRequests = "1"
                )
            ),
            emit = {}
        )
    }
}
