package me.kafuuneko.rpclient.feature.tts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.ui.focus.onFocusChanged
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
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import androidx.compose.ui.res.stringResource

/** Global TTS settings page with provider configuration and voice preview controls. */
@Composable
fun TtsSettingsLayout(
    uiState: TtsSettingsUiState,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is TtsSettingsUiState.Normal) {
        TtsSettingsUiIntent.Back.emit()
    }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.tts_settings_title),
                onBack = { TtsSettingsUiIntent.Back.emit() }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            TtsSettingsUiState.None -> Unit
            is TtsSettingsUiState.Finished -> Unit
            is TtsSettingsUiState.Normal -> TtsSettingsContent(
                state = state,
                emit = emit,
                modifier = Modifier.padding(paddingValues)
            )
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tts_settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.tts_provider),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        ProviderSelector(state.selectedProvider, emit)

        SettingsTextField(
            value = previewText,
            label = stringResource(R.string.tts_preview_text_label),
            onValueChange = { previewText = it },
            singleLine = false,
            minLines = 2
        )

        when (state.selectedProvider) {
            TtsProviderType.System -> SystemPanel(state.system, state.previewState, previewText, emit)
            TtsProviderType.Mimo -> MimoPanel(state.mimo, state.previewState, previewText, emit)
            TtsProviderType.Azure -> AzurePanel(state.azure, state.previewState, previewText, emit)
        }
    }
}

@Composable
private fun ProviderSelector(
    selectedProvider: TtsProviderType,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProviderCard(
            selected = selectedProvider == TtsProviderType.System,
            title = stringResource(R.string.tts_provider_system),
            description = stringResource(R.string.tts_provider_system_description),
            onClick = { TtsSettingsUiIntent.SelectProvider(TtsProviderType.System).emit() }
        )
        ProviderCard(
            selected = selectedProvider == TtsProviderType.Mimo,
            title = stringResource(R.string.tts_provider_mimo),
            description = stringResource(R.string.tts_provider_mimo_description),
            onClick = { TtsSettingsUiIntent.SelectProvider(TtsProviderType.Mimo).emit() }
        )
        ProviderCard(
            selected = selectedProvider == TtsProviderType.Azure,
            title = stringResource(R.string.tts_provider_azure),
            description = stringResource(R.string.tts_provider_azure_description),
            onClick = { TtsSettingsUiIntent.SelectProvider(TtsProviderType.Azure).emit() }
        )
    }
}

@Composable
private fun ProviderCard(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SystemPanel(
    state: SystemTtsSettingsState,
    previewState: TtsPreviewState,
    previewText: String,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    val languages = state.voices.map { it.languageTag }.distinct().sorted()
    val voices = state.voices
        .filter { it.languageTag.equals(state.languageTag, ignoreCase = true) }
        .sortedBy { it.displayName.lowercase() }

    SettingsPanel {
        if (languages.isEmpty()) {
            Text(
                text = stringResource(R.string.tts_system_voices_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SettingsDropdown(
                label = stringResource(R.string.tts_language),
                selectedValue = state.languageTag,
                values = languages,
                valueLabel = { it },
                onSelect = { TtsSettingsUiIntent.SelectSystemLanguage(it).emit() }
            )
            SettingsDropdown(
                label = stringResource(R.string.tts_voice),
                selectedValue = state.voiceName,
                selectedLabel = voices.firstOrNull { it.name == state.voiceName }?.displayName,
                values = voices,
                valueLabel = { it.displayName },
                onSelect = { TtsSettingsUiIntent.SelectSystemVoice(it.name).emit() }
            )
        }

        FloatSlider(
            label = stringResource(R.string.tts_speech_rate),
            value = state.speechRate,
            valueRange = 0.25f..3f,
            onValueChange = { TtsSettingsUiIntent.ChangeSystemSpeechRate(it).emit() }
        )
        FloatSlider(
            label = stringResource(R.string.tts_pitch),
            value = state.pitch,
            valueRange = 0.25f..3f,
            onValueChange = { TtsSettingsUiIntent.ChangeSystemPitch(it).emit() }
        )
        PreviewButton(previewState, previewText, emit)
    }
}

@Composable
private fun MimoPanel(
    state: MimoTtsSettingsState,
    previewState: TtsPreviewState,
    previewText: String,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    SettingsPanel {
        SettingsTextField(
            value = state.baseUrl,
            label = stringResource(R.string.tts_base_url),
            onValueChange = { TtsSettingsUiIntent.ChangeMimoBaseUrl(it).emit() },
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next
        )
        SettingsTextField(
            value = state.apiKey,
            label = stringResource(R.string.tts_api_key),
            onValueChange = { TtsSettingsUiIntent.ChangeMimoApiKey(it).emit() },
            password = true,
            imeAction = ImeAction.Next
        )
        SettingsTextField(
            value = state.model,
            label = stringResource(R.string.model_name),
            onValueChange = { TtsSettingsUiIntent.ChangeMimoModel(it).emit() },
            imeAction = ImeAction.Next
        )
        SettingsDropdown(
            label = stringResource(R.string.tts_voice),
            selectedValue = state.voice,
            selectedLabel = MIMO_VOICES.firstOrNull { it.id == state.voice }?.label,
            values = MIMO_VOICES,
            valueLabel = { it.label },
            onSelect = { TtsSettingsUiIntent.ChangeMimoVoice(it.id).emit() }
        )
        SettingsTextField(
            value = state.instructions,
            label = stringResource(R.string.tts_mimo_instructions),
            onValueChange = { TtsSettingsUiIntent.ChangeMimoInstructions(it).emit() },
            singleLine = false,
            minLines = 3
        )
        FloatSlider(
            label = stringResource(R.string.tts_temperature),
            value = state.temperature,
            valueRange = 0f..1.5f,
            onValueChange = { TtsSettingsUiIntent.ChangeMimoTemperature(it).emit() }
        )
        RpSettingsSwitchTile(
            title = stringResource(R.string.tts_mimo_streaming),
            subtitle = stringResource(R.string.tts_mimo_streaming_description),
            checked = state.streaming,
            onCheckedChange = { TtsSettingsUiIntent.ChangeMimoStreaming(it).emit() }
        )
        PreviewButton(previewState, previewText, emit)
    }
}

@Composable
private fun AzurePanel(
    state: AzureTtsSettingsState,
    previewState: TtsPreviewState,
    previewText: String,
    emit: TtsSettingsUiIntent.() -> Unit
) {
    SettingsPanel {
        SettingsTextField(
            value = state.apiKey,
            label = stringResource(R.string.tts_api_key),
            onValueChange = { TtsSettingsUiIntent.ChangeAzureApiKey(it).emit() },
            password = true,
            imeAction = ImeAction.Next
        )
        SettingsTextField(
            value = state.region,
            label = stringResource(R.string.tts_azure_region),
            onValueChange = { TtsSettingsUiIntent.ChangeAzureRegion(it).emit() }
        )
        SettingsTextField(
            value = state.voice,
            label = stringResource(R.string.tts_voice),
            onValueChange = { TtsSettingsUiIntent.ChangeAzureVoice(it).emit() },
            imeAction = ImeAction.Done
        )
        FloatSlider(
            label = stringResource(R.string.tts_speech_rate),
            value = state.speechRate,
            valueRange = 0.5f..2f,
            onValueChange = { TtsSettingsUiIntent.ChangeAzureSpeechRate(it).emit() }
        )
        PreviewButton(previewState, previewText, emit)
    }
}

@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(300)
            bringIntoViewRequester.bringIntoView()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { isFocused = it.isFocused },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        )
    )
}

@Composable
private fun FloatSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "%.2f".format(value),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdown(
    label: String,
    selectedValue: String,
    selectedLabel: String? = null,
    values: List<T>,
    valueLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayedValue = selectedLabel ?: values.firstOrNull { valueLabel(it) == selectedValue }?.let(valueLabel)
        ?: selectedValue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = valueLabel(value),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
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
        modifier = Modifier.fillMaxWidth()
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
