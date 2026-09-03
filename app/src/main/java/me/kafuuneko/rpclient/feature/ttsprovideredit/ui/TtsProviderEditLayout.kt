package me.kafuuneko.rpclient.feature.ttsprovideredit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.AzureTtsSettingsState
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.MimoTtsSettingsState
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.SystemTtsSettingsState
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.TtsProviderEditUiIntent
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.TtsProviderEditUiState
import me.kafuuneko.rpclient.libs.tts.MIMO_VOICES
import me.kafuuneko.rpclient.libs.tts.descriptionRes
import me.kafuuneko.rpclient.libs.tts.titleRes
import me.kafuuneko.rpclient.libs.tts.TtsProviderType
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpCollapsibleSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpFloatSlider
import me.kafuuneko.rpclient.ui.widgets.RpFormTextField
import me.kafuuneko.rpclient.ui.widgets.RpGroupedTilePadding
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDropdown
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile

/** 单个语音服务详情页。 */
@Composable
fun TtsProviderEditLayout(
    uiState: TtsProviderEditUiState,
    emit: TtsProviderEditUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is TtsProviderEditUiState.Normal) {
        TtsProviderEditUiIntent.Back.emit()
    }
    val state = uiState as? TtsProviderEditUiState.Normal ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(state.provider.titleRes()),
            onBack = { TtsProviderEditUiIntent.Back.emit() }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(state.provider.titleRes()),
                    subtitle = stringResource(state.provider.descriptionRes())
                )
            }
            item {
                when (state.provider) {
                    TtsProviderType.System -> SystemPanel(state.system, emit)
                    TtsProviderType.Mimo -> MimoPanel(state.mimo, emit)
                    TtsProviderType.Azure -> AzurePanel(state.azure, emit)
                }
            }
        }
    }
}

@Composable
private fun SystemPanel(
    state: SystemTtsSettingsState,
    emit: TtsProviderEditUiIntent.() -> Unit
) {
    val languages = state.voices.map { it.languageTag }.distinct().sorted()
    val voices = state.voices
        .filter { it.languageTag.equals(state.languageTag, ignoreCase = true) }
        .sortedBy { it.displayName.lowercase() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RpPanel {
            RpSectionHeader(title = stringResource(R.string.tts_voice))
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
                    onSelect = { TtsProviderEditUiIntent.SelectSystemLanguage(it).emit() }
                )
                RpSettingsDropdown(
                    label = stringResource(R.string.tts_voice),
                    selectedLabel = voices.firstOrNull { it.name == state.voiceName }?.displayName
                        ?: state.voiceName,
                    values = voices,
                    valueLabel = { it.displayName },
                    onSelect = { TtsProviderEditUiIntent.SelectSystemVoice(it.name).emit() }
                )
            }
        }

        RpCollapsibleSettingsGroup(
            title = stringResource(R.string.tts_voice_parameters),
            initiallyExpanded = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RpFloatSlider(
                    title = stringResource(R.string.tts_speech_rate),
                    value = state.speechRate,
                    valueRange = 0.25f..3f,
                    onValueChange = { TtsProviderEditUiIntent.ChangeSystemSpeechRate(it).emit() }
                )
                RpFloatSlider(
                    title = stringResource(R.string.tts_pitch),
                    value = state.pitch,
                    valueRange = 0.25f..3f,
                    onValueChange = { TtsProviderEditUiIntent.ChangeSystemPitch(it).emit() }
                )
            }
        }
    }
}

@Composable
private fun MimoPanel(
    state: MimoTtsSettingsState,
    emit: TtsProviderEditUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RpPanel {
            RpSectionHeader(title = stringResource(R.string.tts_provider_mimo))
            RpFormTextField(
                value = state.baseUrl,
                label = stringResource(R.string.tts_base_url),
                onValueChange = { TtsProviderEditUiIntent.ChangeMimoBaseUrl(it).emit() },
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            )
            RpFormTextField(
                value = state.apiKey,
                label = stringResource(R.string.tts_api_key),
                onValueChange = { TtsProviderEditUiIntent.ChangeMimoApiKey(it).emit() },
                password = true,
                imeAction = ImeAction.Next
            )
            RpFormTextField(
                value = state.model,
                label = stringResource(R.string.model_name),
                onValueChange = { TtsProviderEditUiIntent.ChangeMimoModel(it).emit() },
                imeAction = ImeAction.Done
            )
            RpSettingsDropdown(
                label = stringResource(R.string.tts_voice),
                selectedLabel = MIMO_VOICES.firstOrNull { it.id == state.voice }?.label
                    ?: state.voice,
                values = MIMO_VOICES,
                valueLabel = { it.label },
                onSelect = { TtsProviderEditUiIntent.ChangeMimoVoice(it.id).emit() }
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
                    onValueChange = { TtsProviderEditUiIntent.ChangeMimoInstructions(it).emit() },
                    singleLine = false,
                    minLines = 3
                )
                RpFloatSlider(
                    title = stringResource(R.string.tts_temperature),
                    value = state.temperature,
                    valueRange = 0f..1.5f,
                    onValueChange = { TtsProviderEditUiIntent.ChangeMimoTemperature(it).emit() }
                )
                RpSettingsSwitchTile(
                    title = stringResource(R.string.tts_mimo_streaming),
                    subtitle = stringResource(R.string.tts_mimo_streaming_description),
                    checked = state.streaming,
                    onCheckedChange = { TtsProviderEditUiIntent.ChangeMimoStreaming(it).emit() },
                    contentPadding = RpGroupedTilePadding
                )
            }
        }
    }
}

@Composable
private fun AzurePanel(
    state: AzureTtsSettingsState,
    emit: TtsProviderEditUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RpPanel {
            RpSectionHeader(title = stringResource(R.string.tts_provider_azure))
            RpFormTextField(
                value = state.apiKey,
                label = stringResource(R.string.tts_api_key),
                onValueChange = { TtsProviderEditUiIntent.ChangeAzureApiKey(it).emit() },
                password = true,
                imeAction = ImeAction.Next
            )
            RpFormTextField(
                value = state.region,
                label = stringResource(R.string.tts_azure_region),
                onValueChange = { TtsProviderEditUiIntent.ChangeAzureRegion(it).emit() },
                imeAction = ImeAction.Next
            )
            RpFormTextField(
                value = state.voice,
                label = stringResource(R.string.tts_voice),
                onValueChange = { TtsProviderEditUiIntent.ChangeAzureVoice(it).emit() },
                imeAction = ImeAction.Done
            )
        }

        RpCollapsibleSettingsGroup(
            title = stringResource(R.string.tts_voice_parameters),
            initiallyExpanded = true
        ) {
            RpFloatSlider(
                title = stringResource(R.string.tts_speech_rate),
                value = state.speechRate,
                valueRange = 0.5f..2f,
                onValueChange = { TtsProviderEditUiIntent.ChangeAzureSpeechRate(it).emit() }
            )
        }
    }
}
