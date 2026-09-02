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
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.8.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
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

    RpPanel {
        RpSectionHeader(title = stringResource(R.string.tts_voice))
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
                imeAction = ImeAction.Done
            )
            SettingsDropdown(
                label = stringResource(R.string.tts_voice),
                selectedValue = state.voice,
                selectedLabel = MIMO_VOICES.firstOrNull { it.id == state.voice }?.label,
                values = MIMO_VOICES,
                valueLabel = { it.label },
                onSelect = { TtsSettingsUiIntent.ChangeMimoVoice(it.id).emit() }
            )
        }

        RpCollapsibleSettingsGroup(
            title = stringResource(R.string.advanced_settings),
            initiallyExpanded = false
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                onValueChange = { TtsSettingsUiIntent.ChangeAzureRegion(it).emit() },
                imeAction = ImeAction.Next
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsTextField(
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
private fun SettingsTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(300)
            bringIntoViewRequester.bringIntoView()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { isFocused = it.isFocused },
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        shape = RoundedCornerShape(12.dp)
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
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
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
