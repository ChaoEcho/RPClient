package me.kafuuneko.rpclient.feature.llmprovideredit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.CredentialEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderCredentialResolver
import me.kafuuneko.rpclient.feature.llmprovideredit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.llmprovideredit.model.toEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditDialogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditLoadState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditModelCatalogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditRequestExtensionsState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.adapter.hasValidOpenRouterRoutingPreferences
import me.kafuuneko.rpclient.libs.llm.adapter.protectedRequestBodyPaths
import me.kafuuneko.rpclient.libs.llm.adapter.readOpenRouterRoutingPreferences
import me.kafuuneko.rpclient.libs.llm.adapter.validateRequestBodyPatch
import me.kafuuneko.rpclient.libs.llm.adapter.withOpenRouterFallbacks
import me.kafuuneko.rpclient.libs.llm.adapter.withOpenRouterPreferredProvider
import me.kafuuneko.rpclient.libs.llm.adapter.withOpenRouterPreferredProviderEnabled
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogRepository
import me.kafuuneko.rpclient.libs.llm.catalog.classifyModelCatalogFailure
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.MIN_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 模型供应商编辑页状态持有者，负责表单校验、连接测试与配置持久化。 */
class LLMProviderEditViewModel :
    CoreViewModelWithEvent<LLMProviderEditUiIntent, LLMProviderEditUiState>(
        LLMProviderEditUiState.None
    ), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()
    private val mLLMClientFactory by inject<LLMClientFactory>()
    private val mModelCatalogRepository by inject<LLMModelCatalogRepository>()
    /** 当前连接测试任务；重复测试或离开页面时用于取消旧请求。 */
    private var mTestJob: Job? = null
    /** 模型目录查询与生成测试互不替代，因此使用独立任务管理取消。 */
    private var mModelCatalogJob: Job? = null
    private var mApiKeyReplacement: String? = null
    private var mCustomHeadersReplacement: String? = null
    private var mInitialApiKey = ""
    private var mInitialCustomHeaders = ""

    @UiIntentObserver(LLMProviderEditUiIntent.Init::class)
    private suspend fun onInit(intent: LLMProviderEditUiIntent.Init) {
        if (!isStateOf<LLMProviderEditUiState.None>()) return
        val provider = intent.providerId?.let { mLLMRepository.getProviderById(it) }
        mInitialApiKey = provider?.apiKey.orEmpty()
        mInitialCustomHeaders = provider?.customHeadersJson.orEmpty()
        val form = provider?.toEditForm() ?: LLMProviderEditForm()
        LLMProviderEditUiState.Normal(
            mode = if (provider == null) LLMProviderEditMode.Create else LLMProviderEditMode.Edit,
            form = form,
            requestExtensionsState = form.toRequestExtensionsState()
        ).setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.loadState is LLMProviderEditLoadState.Saving) return
        cancelNetworkTasks()
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(
                testState = LLMProviderEditTestState.None,
                modelCatalogState = if (
                    uiState.modelCatalogState is LLMProviderEditModelCatalogState.Loading
                ) {
                    LLMProviderEditModelCatalogState.Idle
                } else {
                    uiState.modelCatalogState
                },
                dialogState = LLMProviderEditDialogState.UnsavedChangesConfirm
            ).setup()
            return
        }
        finishPage()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: LLMProviderEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProviderType::class)
    private fun onChangeProviderType(intent: LLMProviderEditUiIntent.ChangeProviderType) =
        updateForm(invalidateModelCatalog = true) {
            copy(providerType = intent.value)
        }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProtocol::class)
    private fun onChangeProtocol(intent: LLMProviderEditUiIntent.ChangeProtocol) =
        updateForm(invalidateModelCatalog = true) {
            val capabilities = LLMProviderCapabilities.forProtocol(intent.value)
            copy(
                protocol = intent.value,
                sendTemperature = capabilities.defaultSendTemperature,
                sendTopP = capabilities.defaultSendTopP
            )
        }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: LLMProviderEditUiIntent.ChangeBaseUrl) =
        updateForm(invalidateModelCatalog = true) {
            copy(baseUrl = intent.value)
        }

    @UiIntentObserver(LLMProviderEditUiIntent.ShowApiKeyEditor::class)
    private fun onShowApiKeyEditor() = showDialog(LLMProviderEditDialogState.ApiKeyEditor)

    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmApiKeyReplacement::class)
    private fun onConfirmApiKeyReplacement(
        intent: LLMProviderEditUiIntent.ConfirmApiKeyReplacement
    ) {
        if (intent.value.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.api_key_required).tryEmit()
            return
        }
        mApiKeyReplacement = intent.value
        updateForm(invalidateModelCatalog = true) {
            copy(apiKeyEditMode = CredentialEditMode.Replace)
        }
        closeDialog()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ClearApiKey::class)
    private fun onClearApiKey() {
        mApiKeyReplacement = null
        updateForm(invalidateModelCatalog = true) {
            copy(apiKeyEditMode = CredentialEditMode.Clear)
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.KeepExistingApiKey::class)
    private fun onKeepExistingApiKey() {
        mApiKeyReplacement = null
        updateForm(invalidateModelCatalog = true) {
            copy(apiKeyEditMode = CredentialEditMode.KeepExisting)
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeModel::class)
    private fun onChangeModel(intent: LLMProviderEditUiIntent.ChangeModel) =
        updateForm { copy(model = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.QueryModels::class)
    private fun onQueryModels() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (mModelCatalogJob?.isActive == true) return
        val provider = uiState.form.toCatalogConfigOrNullWithToast() ?: return
        uiState.copy(
            modelCatalogState = LLMProviderEditModelCatalogState.Loading
        ).setup()
        mModelCatalogJob = viewModelScope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                val models = withContext(Dispatchers.IO) {
                    mModelCatalogRepository.listModels(provider)
                }
                val latestState =
                    getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    modelCatalogState = LLMProviderEditModelCatalogState.Loaded(
                        models = models
                    )
                ).setup()
            } catch (_: CancellationException) {
                // 用户主动取消或修改连接配置时，不应显示查询失败。
            } catch (throwable: Throwable) {
                val failure = classifyModelCatalogFailure(throwable)
                    ?: return@launch
                val latestState =
                    getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    modelCatalogState = LLMProviderEditModelCatalogState.Failed(
                        failure = failure
                    )
                ).setup()
            } finally {
                if (mModelCatalogJob === runningJob) mModelCatalogJob = null
            }
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.CancelModelQuery::class)
    private fun onCancelModelQuery() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        cancelModelCatalogQuery()
        if (uiState.modelCatalogState is LLMProviderEditModelCatalogState.Loading) {
            uiState.copy(
                modelCatalogState = LLMProviderEditModelCatalogState.Idle
            ).setup()
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ShowModelPicker::class)
    private fun onShowModelPicker() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val catalogState = uiState.modelCatalogState
            as? LLMProviderEditModelCatalogState.Loaded
            ?: return
        if (catalogState.models.isEmpty()) return
        uiState.copy(
            dialogState = LLMProviderEditDialogState.ModelPicker(
                searchQuery = "",
                items = catalogState.models
            )
        ).setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeModelSearch::class)
    private fun onChangeModelSearch(intent: LLMProviderEditUiIntent.ChangeModelSearch) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState
            as? LLMProviderEditDialogState.ModelPicker
            ?: return
        val models = (
            uiState.modelCatalogState as? LLMProviderEditModelCatalogState.Loaded
        )?.models ?: return
        uiState.copy(
            dialogState = dialogState.copy(
                searchQuery = intent.value,
                items = models.filterForSearch(intent.value)
            )
        ).setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.SelectAvailableModel::class)
    private fun onSelectAvailableModel(intent: LLMProviderEditUiIntent.SelectAvailableModel) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.dialogState !is LLMProviderEditDialogState.ModelPicker) return
        updateForm { copy(model = intent.modelId) }
        closeDialog()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ShowCustomHeadersEditor::class)
    private fun onShowCustomHeadersEditor() =
        showDialog(LLMProviderEditDialogState.CustomHeadersEditor)

    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement::class)
    private fun onConfirmCustomHeadersReplacement(
        intent: LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement
    ) {
        val isObject = runCatching {
            JsonParser.parseString(intent.value).isJsonObject
        }.getOrDefault(false)
        if (!isObject) {
            AppViewEvent.PopupToastMessageByResId(R.string.custom_headers_json_invalid).tryEmit()
            return
        }
        mCustomHeadersReplacement = intent.value
        updateForm(invalidateModelCatalog = true) {
            copy(customHeadersEditMode = CredentialEditMode.Replace)
        }
        closeDialog()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ClearCustomHeaders::class)
    private fun onClearCustomHeaders() {
        mCustomHeadersReplacement = null
        updateForm(invalidateModelCatalog = true) {
            copy(customHeadersEditMode = CredentialEditMode.Clear)
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.KeepExistingCustomHeaders::class)
    private fun onKeepExistingCustomHeaders() {
        mCustomHeadersReplacement = null
        updateForm(invalidateModelCatalog = true) {
            copy(customHeadersEditMode = CredentialEditMode.KeepExisting)
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ShowRequestBodyPatchDialog::class)
    private fun onShowRequestBodyPatchDialog() {
        val form = getOrNull<LLMProviderEditUiState.Normal>()?.form ?: return
        showDialog(LLMProviderEditDialogState.RequestBodyPatchEditor(form.requestBodyPatchJson))
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmRequestBodyPatch::class)
    private fun onConfirmRequestBodyPatch(intent: LLMProviderEditUiIntent.ConfirmRequestBodyPatch) {
        val form = getOrNull<LLMProviderEditUiState.Normal>()?.form ?: return
        val value = intent.value.trim().ifBlank { "{}" }
        if (validateRequestBodyPatch(
                value,
                protectedRequestBodyPaths(form.protocol)
            ).isFailure
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.request_body_patch_invalid).tryEmit()
            return
        }
        if (form.providerType == LLMProviderType.OpenRouter &&
            !value.hasValidOpenRouterRoutingPreferences()
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.request_body_patch_invalid).tryEmit()
            return
        }
        updateForm { copy(requestBodyPatchJson = value) }
        closeDialog()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleOpenRouterPreferredProvider::class)
    private fun onToggleOpenRouterPreferredProvider(
        intent: LLMProviderEditUiIntent.ToggleOpenRouterPreferredProvider
    ) = updateForm {
        copy(
            requestBodyPatchJson = requestBodyPatchJson
                .withOpenRouterPreferredProviderEnabled(intent.value)
        )
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeOpenRouterPreferredProvider::class)
    private fun onChangeOpenRouterPreferredProvider(
        intent: LLMProviderEditUiIntent.ChangeOpenRouterPreferredProvider
    ) = updateForm {
        copy(
            requestBodyPatchJson = requestBodyPatchJson
                .withOpenRouterPreferredProvider(intent.value)
        )
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleOpenRouterFallbacks::class)
    private fun onToggleOpenRouterFallbacks(
        intent: LLMProviderEditUiIntent.ToggleOpenRouterFallbacks
    ) = updateForm {
        copy(requestBodyPatchJson = requestBodyPatchJson.withOpenRouterFallbacks(intent.value))
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTemperature::class)
    private fun onChangeTemperature(intent: LLMProviderEditUiIntent.ChangeTemperature) =
        updateForm { copy(temperature = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTopP::class)
    private fun onChangeTopP(intent: LLMProviderEditUiIntent.ChangeTopP) =
        updateForm { copy(topP = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeMaxTokens::class)
    private fun onChangeMaxTokens(intent: LLMProviderEditUiIntent.ChangeMaxTokens) =
        updateForm { copy(maxTokens = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeContextTokens::class)
    private fun onChangeContextTokens(intent: LLMProviderEditUiIntent.ChangeContextTokens) =
        updateForm { copy(contextTokens = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTokenEstimateReservePercent::class)
    private fun onChangeTokenEstimateReservePercent(
        intent: LLMProviderEditUiIntent.ChangeTokenEstimateReservePercent
    ) = updateForm { copy(tokenEstimateReservePercent = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleSendTemperature::class)
    private fun onToggleSendTemperature(intent: LLMProviderEditUiIntent.ToggleSendTemperature) =
        updateForm { copy(sendTemperature = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleSendTopP::class)
    private fun onToggleSendTopP(intent: LLMProviderEditUiIntent.ToggleSendTopP) =
        updateForm { copy(sendTopP = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.SelectPostProcessingMode::class)
    private fun onSelectPostProcessingMode(
        intent: LLMProviderEditUiIntent.SelectPostProcessingMode
    ) = updateForm { copy(promptPostProcessingMode = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleEnabled::class)
    private fun onToggleEnabled(intent: LLMProviderEditUiIntent.ToggleEnabled) =
        updateForm { copy(isEnabled = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.SaveClick::class)
    private suspend fun onSaveClick() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        cancelNetworkTasks()
        uiState.copy(loadState = LLMProviderEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) { mLLMRepository.saveProvider(provider) }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == LLMProviderEditMode.Create) R.string.model_created else R.string.model_saved
        ).tryEmit()
        finishPage()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.TestClick::class)
    private fun onTestClick() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        if (mTestJob?.isActive == true) return
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        uiState.copy(testState = LLMProviderEditTestState.Testing).setup()
        mTestJob = viewModelScope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                val response = withContext(Dispatchers.IO) {
                    mLLMClientFactory.create(provider.toConfig()).generate(
                        "Please reply with a short English sentence: Model test successful."
                    )
                }
                val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    testState = LLMProviderEditTestState.Success(
                        response.content.ifBlank { "Model test successful" }
                    )
                ).setup()
            } catch (_: CancellationException) {
                // 用户主动取消目录查询属于正常操作，不应展示为连接失败。
            } catch (_: Throwable) {
                val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    testState = LLMProviderEditTestState.Failed
                ).setup()
            } finally {
                if (mTestJob === runningJob) mTestJob = null
            }
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.CancelTest::class)
    private fun onCancelTest() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        cancelTest()
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.testState is LLMProviderEditTestState.Testing) {
            uiState.copy(testState = LLMProviderEditTestState.None).setup()
        }
    }

    private fun cancelTest() {
        mTestJob?.cancel()
        mTestJob = null
    }

    override fun onCleared() {
        cancelNetworkTasks()
        clearSensitiveDrafts()
        super.onCleared()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        cancelNetworkTasks()
        finishPage()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        closeDialog()
    }

    /**
     * 统一更新表单字段，并清理测试结果。
     */
    private fun updateForm(
        invalidateModelCatalog: Boolean = false,
        block: LLMProviderEditForm.() -> LLMProviderEditForm
    ) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        cancelTest()
        if (invalidateModelCatalog) cancelModelCatalogQuery()
        val updatedForm = uiState.form.block()
        uiState.copy(
            form = updatedForm,
            requestExtensionsState = updatedForm.toRequestExtensionsState(),
            testState = LLMProviderEditTestState.None,
            modelCatalogState = if (invalidateModelCatalog) {
                LLMProviderEditModelCatalogState.Idle
            } else {
                uiState.modelCatalogState
            }
        ).setup()
    }

    private fun LLMProviderEditForm.toCatalogConfigOrNullWithToast(): LLMProviderConfig? {
        if (baseUrl.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.base_url_empty).tryEmit()
            return null
        }
        val credentials = resolveCredentials() ?: return null
        return LLMProviderConfig(
            name = name.trim(),
            providerType = providerType,
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            apiKey = credentials.apiKey.trim(),
            model = model.trim(),
            customHeadersJson = credentials.customHeadersJson.trim(),
            requestBodyPatchJson = requestBodyPatchJson.trim().ifBlank { "{}" }
        )
    }

    /**
     * 校验表单并转换为数据库实体，失败时给出对应提示。
     */
    private fun LLMProviderEditForm.toProviderOrNullWithToast(): LLMProvider? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.model_name_empty).tryEmit()
            return null
        }
        if (baseUrl.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.base_url_empty).tryEmit()
            return null
        }
        if (model.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.model_name_required).tryEmit()
            return null
        }
        val parsedMaxTokens = maxTokens.toIntOrNull()
        val parsedContextTokens = contextTokens.toIntOrNull()
        if (parsedMaxTokens != null &&
            parsedContextTokens != null &&
            parsedMaxTokens >= parsedContextTokens
        ) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.max_tokens_must_be_less_than_context
            ).tryEmit()
            return null
        }
        if (tokenEstimateReservePercent !in
            MIN_TOKEN_ESTIMATE_RESERVE_PERCENT..MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
        ) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.token_estimate_reserve_invalid
            ).tryEmit()
            return null
        }
        if (validateRequestBodyPatch(
                requestBodyPatchJson,
                protectedRequestBodyPaths(protocol)
            ).isFailure
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.request_body_patch_invalid).tryEmit()
            return null
        }
        if (providerType == LLMProviderType.OpenRouter &&
            !requestBodyPatchJson.hasValidOpenRouterRoutingPreferences()
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.request_body_patch_invalid).tryEmit()
            return null
        }
        val credentials = resolveCredentials() ?: return null
        val provider = toProviderOrNull(
            apiKey = credentials.apiKey,
            customHeadersJson = credentials.customHeadersJson
        )
        if (provider == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_params_invalid).tryEmit()
        }
        return provider
    }

    private fun LLMProviderEditForm.resolveCredentials() =
        LLMProviderCredentialResolver.resolve(
            form = this,
            initialApiKey = mInitialApiKey,
            initialCustomHeaders = mInitialCustomHeaders,
            apiKeyReplacement = mApiKeyReplacement,
            customHeadersReplacement = mCustomHeadersReplacement
        )

    private fun LLMProviderEditForm.toRequestExtensionsState():
        LLMProviderEditRequestExtensionsState {
        val routing = requestBodyPatchJson.readOpenRouterRoutingPreferences()
        return LLMProviderEditRequestExtensionsState(
            isOpenRouter = providerType == LLMProviderType.OpenRouter,
            usesPreferredProvider = routing.usesPreferredProvider,
            preferredProvider = routing.preferredProvider,
            allowFallbacks = routing.allowFallbacks
        )
    }

    private fun List<LLMAvailableModel>.filterForSearch(
        query: String
    ): List<LLMAvailableModel> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return this
        return filter { model ->
            model.id.contains(normalizedQuery, ignoreCase = true) ||
                model.displayName.contains(normalizedQuery, ignoreCase = true)
        }
    }

    private fun cancelModelCatalogQuery() {
        mModelCatalogJob?.cancel()
        mModelCatalogJob = null
    }

    private fun cancelNetworkTasks() {
        cancelTest()
        cancelModelCatalogQuery()
    }

    private fun showDialog(dialogState: LLMProviderEditDialogState) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = dialogState).setup()
    }

    private fun closeDialog() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = LLMProviderEditDialogState.None).setup()
    }

    private fun finishPage() {
        clearSensitiveDrafts()
        LLMProviderEditUiState.finished(uiStateFlow.value).setup()
    }

    private fun clearSensitiveDrafts() {
        mApiKeyReplacement = null
        mCustomHeadersReplacement = null
        mInitialApiKey = ""
        mInitialCustomHeaders = ""
    }

}
