package me.kafuuneko.rpclient.feature.tts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.tts.presentation.AzureTtsSettingsState
import me.kafuuneko.rpclient.feature.tts.presentation.MimoTtsSettingsState
import me.kafuuneko.rpclient.feature.tts.presentation.SystemTtsSettingsState
import me.kafuuneko.rpclient.feature.tts.presentation.TtsPreviewState
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiIntent
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiState
import me.kafuuneko.rpclient.libs.tts.MIMO_VOICES
import me.kafuuneko.rpclient.libs.tts.TtsProviderType
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpCollapsibleSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpFloatSlider
import me.kafuuneko.rpclient.ui.widgets.RpFormTextField
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDropdown
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpGroupedTilePadding
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpTagPill

/** Global TTS settings page with provider configuration and voice preview controls. */
@Composable
fun TtsSettingsLayout(
    uiState: TtsSettingsUiState,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is TtsSettingsUiState.Normal) {
        TtsSettingsUiIntent.Back.emit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.tts_settings_title),
            onBack = { TtsSettingsUiIntent.Back.emit() }
        )
        when (val state = uiState) {
            TtsSettingsUiState.None -> Unit
            is TtsSettingsUiState.Finished -> Unit
            is TtsSettingsUiState.Normal -> TtsSettingsContent(state = state, emit = emit)
        }
    }
}

@Composable
private fun TtsSettingsContent(
    state: TtsSettingsUiState.Normal,
    emit: TtsSettingsUiIntent.() -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultPreviewText = stringResource(R.string.tts_preview_text)
    var previewText by rememberSaveable { mutableStateOf(defaultPreviewText) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            )
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RpPageTitle(
                title = stringResource(R.string.tts_settings_title),
                subtitle = stringResource(R.string.tts_settings_subtitle)
            )
        }

        item {
            RpSectionHeader(title = stringResource(R.string.tts_provider))
        }

        item {
            ProviderSelector(state.selectedProvider, emit)
        }

        item {
            when (state.selectedProvider) {
                TtsProviderType.Mimo -> MimoPanel(state.mimo, emit)
                TtsProviderType.System -> SystemPanel(state.system, emit)
                TtsProviderType.Azure -> AzurePanel(state.azure, emit)
            }
        }

        item {
            VoiceTestPanel(
                previewText = previewText,
                previewState = state.previewState,
                onPreviewTextChange = { previewText = it },
                emit = emit
            )
        }
    }
}

@Composable
private fun ProviderSelector(
    selectedProvider: TtsProviderType,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TtsProviderCard(
            selected = selectedProvider == TtsProviderType.Mimo,
            title = stringResource(R.string.tts_provider_mimo),
            description = stringResource(R.string.tts_provider_mimo_description),
            onClick = { TtsSettingsUiIntent.SelectProvider(TtsProviderType.Mimo).emit() }
        )
        TtsProviderCard(
            selected = selectedProvider == TtsProviderType.System,
            title = stringResource(R.string.tts_provider_system),
            description = stringResource(R.string.tts_provider_system_description),
            onClick = { TtsSettingsUiIntent.SelectProvider(TtsProviderType.System).emit() }
        )
        TtsProviderCard(
            selected = selectedProvider == TtsProviderType.Azure,
            title = stringResource(R.string.tts_provider_azure),
            description = stringResource(R.string.tts_provider_azure_description),
            onClick = { TtsSettingsUiIntent.SelectProvider(TtsProviderType.Azure).emit() }
        )
    }
}

@Composable
private fun TtsProviderCard(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Spacer(modifier = Modifier.width(12.dp))
                RpTagPill(text = stringResource(R.string.current_badge))
            }
        }
    }
}

@Composable
private fun SystemPanel(
    state: SystemTtsSettingsState,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    val languages = state.voices.map { it.languageTag }.distinct().sorted()
    val voices = state.voices
        .filter { it.languageTag.equals(state.languageTag, ignoreCase = true) }
        .sortedBy { it.displayName.lowercase() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RpPanel {
            RpSectionHeader(title = stringResource(R.string.tts_provider_system))
            if (languages.isEmpty()) {
                Text(
                    text = stringResource(R.string.tts_system_voices_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                RpSettingsDropdown(
                    label = stringResource(R.string.tts_language),
                    selectedLabel = state.languageTag,
                    values = languages,
                    valueLabel = { it },
                    onSelect = { TtsSettingsUiIntent.SelectSystemLanguage(it).emit() }
                )
                RpSettingsDropdown(
                    label = stringResource(R.string.tts_voice),
                    selectedLabel = voices.firstOrNull { it.name == state.voiceName }?.displayName
                        ?: state.voiceName,
                    values = voices,
                    valueLabel = { it.displayName },
                    onSelect = { TtsSettingsUiIntent.SelectSystemVoice(it.name).emit() }
                )
            }
        }

        RpCollapsibleSettingsGroup(
            title = stringResource(R.string.tts_voice_parameters),
            initiallyExpanded = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RpFloatSlider(
                    title = stringResource(R.string.tts_speech_rate),
                    value = state.speechRate,
                    valueRange = 0.25f..3f,
                    onValueChange = { TtsSettingsUiIntent.ChangeSystemSpeechRate(it).emit() }
                )
                RpFloatSlider(
                    title = stringResource(R.string.tts_pitch),
                    value = state.pitch,
                    valueRange = 0.25f..3f,
                    onValueChange = { TtsSettingsUiIntent.ChangeSystemPitch(it).emit() }
                )
            }
        }
    }
}

@Composable
private fun MimoPanel(
    state: MimoTtsSettingsState,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RpPanel {
            RpSectionHeader(title = stringResource(R.string.tts_provider_mimo))
            RpFormTextField(
                value = state.baseUrl,
                label = stringResource(R.string.tts_base_url),
                onValueChange = { TtsSettingsUiIntent.ChangeMimoBaseUrl(it).emit() },
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            )
            RpFormTextField(
                value = state.apiKey,
                label = stringResource(R.string.tts_api_key),
                onValueChange = { TtsSettingsUiIntent.ChangeMimoApiKey(it).emit() },
                password = true,
                imeAction = ImeAction.Next
            )
            RpFormTextField(
                value = state.model,
                label = stringResource(R.string.model_name),
                onValueChange = { TtsSettingsUiIntent.ChangeMimoModel(it).emit() },
                imeAction = ImeAction.Done
            )
            RpSettingsDropdown(
                label = stringResource(R.string.tts_voice),
                selectedLabel = MIMO_VOICES.firstOrNull { it.id == state.voice }?.label
                    ?: state.voice,
                values = MIMO_VOICES,
                valueLabel = { it.label },
                onSelect = { TtsSettingsUiIntent.ChangeMimoVoice(it.id).emit() }
            )
        }

        RpCollapsibleSettingsGroup(
            title = stringResource(R.string.advanced_settings),
            initiallyExpanded = false
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RpFormTextField(
                    value = state.instructions,
                    label = stringResource(R.string.tts_mimo_instructions),
                    onValueChange = { TtsSettingsUiIntent.ChangeMimoInstructions(it).emit() },
                    singleLine = false,
                    minLines = 3
                )
                RpFloatSlider(
                    title = stringResource(R.string.tts_temperature),
                    value = state.temperature,
                    valueRange = 0f..1.5f,
                    onValueChange = { TtsSettingsUiIntent.ChangeMimoTemperature(it).emit() }
                )
                RpSettingsSwitchTile(
                    title = stringResource(R.string.tts_mimo_streaming),
                    subtitle = stringResource(R.string.tts_mimo_streaming_description),
                    checked = state.streaming,
                    onCheckedChange = { TtsSettingsUiIntent.ChangeMimoStreaming(it).emit() },
                    contentPadding = RpGroupedTilePadding
                )
            }
        }
    }
}

@Composable
private fun AzurePanel(
    state: AzureTtsSettingsState,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RpPanel {
            RpSectionHeader(title = stringResource(R.string.tts_provider_azure))
            RpFormTextField(
                value = state.apiKey,
                label = stringResource(R.string.tts_api_key),
                onValueChange = { TtsSettingsUiIntent.ChangeAzureApiKey(it).emit() },
                password = true,
                imeAction = ImeAction.Next
            )
            RpFormTextField(
                value = state.region,
                label = stringResource(R.string.tts_azure_region),
                onValueChange = { TtsSettingsUiIntent.ChangeAzureRegion(it).emit() },
                imeAction = ImeAction.Next
            )
            RpFormTextField(
                value = state.voice,
                label = stringResource(R.string.tts_voice),
                onValueChange = { TtsSettingsUiIntent.ChangeAzureVoice(it).emit() },
                imeAction = ImeAction.Done
            )
        }

        RpCollapsibleSettingsGroup(
            title = stringResource(R.string.tts_voice_parameters),
            initiallyExpanded = false
        ) {
            RpFloatSlider(
                title = stringResource(R.string.tts_speech_rate),
                value = state.speechRate,
                valueRange = 0.5f..2f,
                onValueChange = { TtsSettingsUiIntent.ChangeAzureSpeechRate(it).emit() }
            )
        }
    }
}

@Composable
private fun VoiceTestPanel(
    previewText: String,
    previewState: TtsPreviewState,
    onPreviewTextChange: (String) -> Unit,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    RpCollapsibleSettingsGroup(
        title = stringResource(R.string.tts_test_section),
        initiallyExpanded = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RpFormTextField(
                value = previewText,
                label = stringResource(R.string.tts_preview_text_label),
                onValueChange = onPreviewTextChange,
                singleLine = false,
                minLines = 2
            )
            PreviewButton(previewState, previewText, emit)
        }
    }
}

@Composable
private fun PreviewButton(
    previewState: TtsPreviewState,
    previewText: String,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    val isLoading = previewState == TtsPreviewState.Loading
    val isPlaying = previewState == TtsPreviewState.Playing
    Button(
        onClick = {
            if (isPlaying) {
                TtsSettingsUiIntent.StopPreview.emit()
            } else {
                TtsSettingsUiIntent.PreviewSpeech(previewText).emit()
            }
        },
        enabled = isPlaying || (!isLoading && previewText.isNotBlank()),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.tts_preview))
            }
            isPlaying -> {
                Icon(Icons.Rounded.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.tts_stop))
            }
            else -> {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.tts_preview))
            }
        }
    }
}
