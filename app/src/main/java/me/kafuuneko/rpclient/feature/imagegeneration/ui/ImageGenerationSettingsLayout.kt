package me.kafuuneko.rpclient.feature.imagegeneration.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiIntent
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiState
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImagePromptProviderItem
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import androidx.compose.ui.res.stringResource

/** Standalone OpenAI-compatible image-generation settings page. */
@Composable
fun ImageGenerationSettingsLayout(
    uiState: ImageGenerationSettingsUiState,
    emit: ImageGenerationSettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is ImageGenerationSettingsUiState.Normal) {
        ImageGenerationSettingsUiIntent.Back.emit()
    }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.image_generation),
                onBack = { ImageGenerationSettingsUiIntent.Back.emit() }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            ImageGenerationSettingsUiState.None -> Unit
            is ImageGenerationSettingsUiState.Finished -> Unit
            is ImageGenerationSettingsUiState.Normal -> ImageGenerationSettingsContent(
                state = state,
                emit = emit,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun ImageGenerationSettingsContent(
    state: ImageGenerationSettingsUiState.Normal,
    emit: ImageGenerationSettingsUiIntent.() -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.image_generation_settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsPanel {
            ImagePromptProviderSelector(
                selectedProviderId = state.selectedProviderId,
                providers = state.providers,
                emit = emit
            )
            SettingsTextField(
                value = state.baseUrl,
                label = stringResource(R.string.image_generation_base_url),
                onValueChange = { ImageGenerationSettingsUiIntent.ChangeBaseUrl(it).emit() },
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            )
            SettingsTextField(
                value = state.apiKey,
                label = stringResource(R.string.image_generation_api_key),
                onValueChange = { ImageGenerationSettingsUiIntent.ChangeApiKey(it).emit() },
                password = true,
                imeAction = ImeAction.Next
            )
            SettingsTextField(
                value = state.model,
                label = stringResource(R.string.image_generation_model),
                onValueChange = { ImageGenerationSettingsUiIntent.ChangeModel(it).emit() },
                imeAction = ImeAction.Next
            )
            SettingsTextField(
                value = state.size,
                label = stringResource(R.string.image_generation_size),
                onValueChange = { ImageGenerationSettingsUiIntent.ChangeSize(it).emit() },
                imeAction = ImeAction.Next
            )
            SettingsTextField(
                value = state.stylePrompt,
                label = stringResource(R.string.image_generation_style_prompt),
                onValueChange = { ImageGenerationSettingsUiIntent.ChangeStylePrompt(it).emit() },
                singleLine = false,
                minLines = 3,
                imeAction = ImeAction.Default
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePromptProviderSelector(
    selectedProviderId: Long,
    providers: List<ImagePromptProviderItem>,
    emit: ImageGenerationSettingsUiIntent.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedLabel = selectedProvider?.displayName()
        ?: stringResource(R.string.image_prompt_follow_chat_model)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.image_prompt_model)) },
            supportingText = { Text(stringResource(R.string.image_prompt_model_helper)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            maxLines = 1
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.image_prompt_follow_chat_model)) },
                onClick = {
                    ImageGenerationSettingsUiIntent.ChangePromptProvider(0L).emit()
                    expanded = false
                }
            )
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = provider.displayName(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        ImageGenerationSettingsUiIntent.ChangePromptProvider(provider.id).emit()
                        expanded = false
                    }
                )
            }
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

@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
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
