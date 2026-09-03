package me.kafuuneko.rpclient.feature.tts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.tts.presentation.TtsPreviewState
import me.kafuuneko.rpclient.feature.tts.presentation.TtsProviderListItem
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiIntent
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiState
import me.kafuuneko.rpclient.libs.tts.descriptionRes
import me.kafuuneko.rpclient.libs.tts.titleRes
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpCollapsibleSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpFormTextField
import me.kafuuneko.rpclient.ui.widgets.RpNavigationChevron
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagPill

/** 语音服务列表页：三张卡片显示选用状态，参数在各自详情页里改。 */
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
    emit: TtsSettingsUiIntent.() -> Unit
) {
    val defaultPreviewText = stringResource(R.string.tts_preview_text)
    var previewText by rememberSaveable { mutableStateOf(defaultPreviewText) }

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
                title = stringResource(R.string.tts_settings_title),
                subtitle = stringResource(R.string.tts_settings_subtitle)
            )
        }
        item {
            RpSectionHeader(title = stringResource(R.string.tts_provider))
        }
        items(state.providers, key = { it.provider }) { item ->
            TtsProviderCard(
                item = item,
                onClick = { TtsSettingsUiIntent.OpenProviderEdit(item.provider).emit() },
                onSelectCurrent = { TtsSettingsUiIntent.SelectProvider(item.provider).emit() }
            )
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
private fun TtsProviderCard(
    item: TtsProviderListItem,
    onClick: () -> Unit,
    onSelectCurrent: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (item.isCurrent) 1.5.dp else 0.5.dp,
            color = if (item.isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TtsProviderStatusDot(isConfigured = item.isConfigured)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(item.provider.titleRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(item.provider.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (item.isCurrent) {
                RpTagPill(text = stringResource(R.string.current_badge))
            } else {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onSelectCurrent)
                ) {
                    Text(
                        text = stringResource(R.string.set_as_current),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            RpNavigationChevron()
        }
    }
}

@Composable
private fun TtsProviderStatusDot(isConfigured: Boolean) {
    val dotColor = if (isConfigured) Color(0xFF10B981) else MaterialTheme.colorScheme.error
    Surface(
        shape = CircleShape,
        color = dotColor.copy(alpha = 0.2f),
        modifier = Modifier.size(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = dotColor, modifier = Modifier.size(7.dp)) {}
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
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
