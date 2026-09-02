package me.kafuuneko.rpclient.feature.imagegeneration.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsForm
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiIntent
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiState
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImagePromptProviderItem
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpCollapsibleSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpFormTextField
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDropdown
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

/** Standalone OpenAI-compatible image-generation settings page. */
@Composable
fun ImageGenerationSettingsLayout(
    uiState: ImageGenerationSettingsUiState,
    emit: ImageGenerationSettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is ImageGenerationSettingsUiState.Normal) {
        ImageGenerationSettingsUiIntent.Back.emit()
    }

    when (uiState) {
        ImageGenerationSettingsUiState.None -> Unit
        is ImageGenerationSettingsUiState.Finished -> ImageGenerationSettingsLayout(uiState.previous) {}
        is ImageGenerationSettingsUiState.Normal -> ImageGenerationSettingsNormal(uiState, emit)
    }
}

@Composable
private fun ImageGenerationSettingsNormal(
    state: ImageGenerationSettingsUiState.Normal,
    emit: ImageGenerationSettingsUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.image_generation),
            onBack = { ImageGenerationSettingsUiIntent.Back.emit() },
            actions = {
                TopBarSaveButton(onClick = { ImageGenerationSettingsUiIntent.Save.emit() })
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.image_generation),
                    subtitle = stringResource(R.string.image_generation_settings_subtitle)
                )
            }
            item {
                ServicePanel(form = state.form, emit = emit)
            }
            item {
                GenerationParamsPanel(form = state.form, emit = emit)
            }
            item {
                PromptModelPanel(
                    selectedProviderId = state.form.promptProviderId,
                    providers = state.providers,
                    emit = emit
                )
            }
            item {
                StylePanel(form = state.form, emit = emit)
            }
        }
    }
}

@Composable
private fun TopBarSaveButton(
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.image_generation_save))
    }
}

@Composable
private fun ServicePanel(
    form: ImageGenerationSettingsForm,
    emit: ImageGenerationSettingsUiIntent.() -> Unit
) {
    RpPanel {
        RpSectionHeader(title = stringResource(R.string.image_generation_service_section))

        RpFormTextField(
            value = form.baseUrl,
            label = stringResource(R.string.image_generation_base_url),
            onValueChange = { ImageGenerationSettingsUiIntent.ChangeBaseUrl(it).emit() },
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next
        )
        RpFormTextField(
            value = form.apiKey,
            label = stringResource(R.string.image_generation_api_key),
            onValueChange = { ImageGenerationSettingsUiIntent.ChangeApiKey(it).emit() },
            password = true,
            imeAction = ImeAction.Next
        )
        RpFormTextField(
            value = form.model,
            label = stringResource(R.string.image_generation_model),
            onValueChange = { ImageGenerationSettingsUiIntent.ChangeModel(it).emit() },
            imeAction = ImeAction.Next
        )
    }
}

@Composable
private fun GenerationParamsPanel(
    form: ImageGenerationSettingsForm,
    emit: ImageGenerationSettingsUiIntent.() -> Unit
) {
    RpPanel {
        RpSectionHeader(title = stringResource(R.string.image_generation_params_section))

        RpFormTextField(
            value = form.size,
            label = stringResource(R.string.image_generation_size),
            onValueChange = { ImageGenerationSettingsUiIntent.ChangeSize(it).emit() },
            imeAction = ImeAction.Next
        )
        RpFormTextField(
            value = form.maxConcurrentRequests,
            label = stringResource(R.string.image_generation_max_concurrent_requests),
            supportingText = stringResource(R.string.image_generation_max_concurrent_requests_desc),
            onValueChange = { ImageGenerationSettingsUiIntent.ChangeMaxConcurrentRequests(it).emit() },
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        )
    }
}

@Composable
private fun PromptModelPanel(
    selectedProviderId: Long,
    providers: List<ImagePromptProviderItem>,
    emit: ImageGenerationSettingsUiIntent.() -> Unit
) {
    val followChatModel = stringResource(R.string.image_prompt_follow_chat_model)
    RpPanel {
        RpSectionHeader(title = stringResource(R.string.image_prompt_model_section))

        RpSettingsDropdown(
            label = stringResource(R.string.image_prompt_model),
            supportingText = stringResource(R.string.image_prompt_model_helper),
            selectedLabel = providers.firstOrNull { it.id == selectedProviderId }?.displayName()
                ?: followChatModel,
            values = providers,
            valueLabel = { it.displayName() },
            onSelect = { ImageGenerationSettingsUiIntent.ChangePromptProvider(it.id).emit() },
            leadingOption = followChatModel to {
                ImageGenerationSettingsUiIntent.ChangePromptProvider(0L).emit()
            }
        )
    }
}

@Composable
private fun StylePanel(
    form: ImageGenerationSettingsForm,
    emit: ImageGenerationSettingsUiIntent.() -> Unit
) {
    RpCollapsibleSettingsGroup(
        title = stringResource(R.string.image_generation_style_section),
        subtitle = stringResource(R.string.image_generation_style_prompt),
        initiallyExpanded = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpFormTextField(
                value = form.sceneStylePrompt,
                label = stringResource(R.string.image_generation_scene_style_prompt),
                supportingText = stringResource(R.string.image_generation_scene_style_prompt_desc),
                onValueChange = { ImageGenerationSettingsUiIntent.ChangeSceneStylePrompt(it).emit() },
                singleLine = false,
                minLines = 3,
                imeAction = ImeAction.Default
            )

            RpFormTextField(
                value = form.avatarStylePrompt,
                label = stringResource(R.string.image_generation_avatar_style_prompt),
                supportingText = stringResource(R.string.image_generation_avatar_style_prompt_desc),
                onValueChange = { ImageGenerationSettingsUiIntent.ChangeAvatarStylePrompt(it).emit() },
                singleLine = false,
                minLines = 3,
                imeAction = ImeAction.Default
            )
        }
    }
}

private fun ImagePromptProviderItem.displayName(): String {
    val providerName = name.trim()
    val providerModel = model.trim()
    return when {
        providerName.isEmpty() -> providerModel
        providerModel.isEmpty() -> providerName
        else -> "$providerName · $providerModel"
    }
}
