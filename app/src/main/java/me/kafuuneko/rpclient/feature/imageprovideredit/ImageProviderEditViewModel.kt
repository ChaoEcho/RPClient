package me.kafuuneko.rpclient.feature.imageprovideredit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditDialogState
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditForm
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditUiIntent
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogRepository
import me.kafuuneko.rpclient.libs.llm.catalog.ModelCatalogState
import me.kafuuneko.rpclient.libs.llm.catalog.classifyModelCatalogFailure
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.DEFAULT_IMAGE_PROVIDER_BASE_URL
import me.kafuuneko.rpclient.libs.room.entity.DEFAULT_IMAGE_PROVIDER_CONCURRENCY
import me.kafuuneko.rpclient.libs.room.entity.DEFAULT_IMAGE_PROVIDER_SIZE
import me.kafuuneko.rpclient.libs.room.entity.ImageProvider
import me.kafuuneko.rpclient.libs.room.entity.MAX_IMAGE_PROVIDER_CONCURRENCY
import me.kafuuneko.rpclient.libs.room.entity.MIN_IMAGE_PROVIDER_CONCURRENCY
import me.kafuuneko.rpclient.libs.room.repository.ImageProviderRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 图片服务编辑页状态持有者。
 *
 * 与对话模型编辑页的差别只在字段集合：图片端点固定走 OpenAI 兼容协议，
 * 因此没有协议选择、预设模板与请求体补丁。模型目录拉取复用对话模型那套实现。
 */
class ImageProviderEditViewModel :
    CoreViewModelWithEvent<ImageProviderEditUiIntent, ImageProviderEditUiState>(
        ImageProviderEditUiState.None
    ), KoinComponent {

    private val mImageProviderRepository by inject<ImageProviderRepository>()
    private val mModelCatalogRepository by inject<LLMModelCatalogRepository>()

    private var mProviderId = 0L
    private var mModelCatalogJob: Job? = null

    @UiIntentObserver(ImageProviderEditUiIntent.Init::class)
    private suspend fun onInit(intent: ImageProviderEditUiIntent.Init) {
        if (!isStateOf<ImageProviderEditUiState.None>()) return
        val provider = intent.providerId?.let { mImageProviderRepository.getProviderById(it) }
        mProviderId = provider?.id ?: 0L
        ImageProviderEditUiState.Normal(
            isCreateMode = provider == null,
            form = provider?.toEditForm() ?: ImageProviderEditForm(
                baseUrl = DEFAULT_IMAGE_PROVIDER_BASE_URL,
                size = DEFAULT_IMAGE_PROVIDER_SIZE,
                maxConcurrentRequests = DEFAULT_IMAGE_PROVIDER_CONCURRENCY.toString()
            )
        ).setup()
    }

    @UiIntentObserver(ImageProviderEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<ImageProviderEditUiState.Normal>() ?: return
        if (uiState.isSaving) return
        cancelModelCatalogQuery()
        if (uiState.form != uiState.initialForm) {
            uiState.copy(
                modelCatalogState = uiState.modelCatalogState.idleIfLoading(),
                dialogState = ImageProviderEditDialogState.UnsavedChangesConfirm
            ).setup()
            return
        }
        finishPage()
    }

    @UiIntentObserver(ImageProviderEditUiIntent.DiscardChanges::class)
    private fun onDiscardChanges() = finishPage()

    @UiIntentObserver(ImageProviderEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<ImageProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = ImageProviderEditDialogState.None).setup()
    }

    @UiIntentObserver(ImageProviderEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: ImageProviderEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(ImageProviderEditUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: ImageProviderEditUiIntent.ChangeBaseUrl) =
        updateForm(invalidateModelCatalog = true) { copy(baseUrl = intent.value) }

    @UiIntentObserver(ImageProviderEditUiIntent.ChangeApiKey::class)
    private fun onChangeApiKey(intent: ImageProviderEditUiIntent.ChangeApiKey) =
        updateForm(invalidateModelCatalog = true) { copy(apiKey = intent.value) }

    @UiIntentObserver(ImageProviderEditUiIntent.ChangeModel::class)
    private fun onChangeModel(intent: ImageProviderEditUiIntent.ChangeModel) =
        updateForm { copy(model = intent.value) }

    @UiIntentObserver(ImageProviderEditUiIntent.ChangeSize::class)
    private fun onChangeSize(intent: ImageProviderEditUiIntent.ChangeSize) =
        updateForm { copy(size = intent.value) }

    @UiIntentObserver(ImageProviderEditUiIntent.ChangeMaxConcurrentRequests::class)
    private fun onChangeMaxConcurrentRequests(
        intent: ImageProviderEditUiIntent.ChangeMaxConcurrentRequests
    ) = updateForm { copy(maxConcurrentRequests = intent.value.filter { it.isDigit() }) }

    /** 拉取该端点的可用模型；图片服务同样走 OpenAI 兼容的 `/models`。 */
    @UiIntentObserver(ImageProviderEditUiIntent.QueryModels::class)
    private fun onQueryModels() {
        val uiState = getOrNull<ImageProviderEditUiState.Normal>() ?: return
        if (mModelCatalogJob?.isActive == true) return
        if (uiState.form.baseUrl.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.base_url_empty).tryEmit()
            return
        }
        val config = uiState.form.toCatalogConfig()
        uiState.copy(modelCatalogState = ModelCatalogState.Loading).setup()
        mModelCatalogJob = viewModelScope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                val models = withContext(Dispatchers.IO) {
                    mModelCatalogRepository.listModels(config)
                }
                getOrNull<ImageProviderEditUiState.Normal>()
                    ?.copy(modelCatalogState = ModelCatalogState.Loaded(models))
                    ?.setup()
            } catch (_: CancellationException) {
                // 用户主动取消或修改连接配置，不提示失败
            } catch (throwable: Throwable) {
                val failure = classifyModelCatalogFailure(throwable) ?: return@launch
                getOrNull<ImageProviderEditUiState.Normal>()
                    ?.copy(modelCatalogState = ModelCatalogState.Failed(failure))
                    ?.setup()
            } finally {
                if (mModelCatalogJob === runningJob) mModelCatalogJob = null
            }
        }
    }

    @UiIntentObserver(ImageProviderEditUiIntent.CancelModelQuery::class)
    private fun onCancelModelQuery() {
        val uiState = getOrNull<ImageProviderEditUiState.Normal>() ?: return
        cancelModelCatalogQuery()
        if (uiState.modelCatalogState is ModelCatalogState.Loading) {
            uiState.copy(modelCatalogState = ModelCatalogState.Idle).setup()
        }
    }

    @UiIntentObserver(ImageProviderEditUiIntent.ShowModelPicker::class)
    private fun onShowModelPicker() {
        val uiState = getOrNull<ImageProviderEditUiState.Normal>() ?: return
        val models = (uiState.modelCatalogState as? ModelCatalogState.Loaded)?.models ?: return
        if (models.isEmpty()) return
        uiState.copy(dialogState = ImageProviderEditDialogState.ModelPicker(models)).setup()
    }

    @UiIntentObserver(ImageProviderEditUiIntent.SelectAvailableModel::class)
    private fun onSelectAvailableModel(intent: ImageProviderEditUiIntent.SelectAvailableModel) {
        val uiState = getOrNull<ImageProviderEditUiState.Normal>() ?: return
        if (uiState.dialogState !is ImageProviderEditDialogState.ModelPicker) return
        uiState.copy(
            form = uiState.form.copy(model = intent.modelId),
            dialogState = ImageProviderEditDialogState.None
        ).setup()
    }

    @UiIntentObserver(ImageProviderEditUiIntent.SaveClick::class)
    private suspend fun onSaveClick() {
        val uiState = getOrNull<ImageProviderEditUiState.Normal>() ?: return
        if (uiState.isSaving) return
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        uiState.copy(isSaving = true).setup()
        mImageProviderRepository.saveProvider(provider)
        AppViewEvent.PopupToastMessageByResId(R.string.image_generation_saved).tryEmit()
        finishPage()
    }

    private fun updateForm(
        invalidateModelCatalog: Boolean = false,
        transform: ImageProviderEditForm.() -> ImageProviderEditForm
    ) {
        val uiState = getOrNull<ImageProviderEditUiState.Normal>() ?: return
        if (invalidateModelCatalog) cancelModelCatalogQuery()
        uiState.copy(
            form = uiState.form.transform(),
            modelCatalogState = if (invalidateModelCatalog) {
                ModelCatalogState.Idle
            } else {
                uiState.modelCatalogState
            }
        ).setup()
    }

    private fun cancelModelCatalogQuery() {
        mModelCatalogJob?.cancel()
        mModelCatalogJob = null
    }

    private fun finishPage() {
        cancelModelCatalogQuery()
        ImageProviderEditUiState.finished(uiStateFlow.value).setup()
    }

    private fun ModelCatalogState.idleIfLoading() =
        if (this is ModelCatalogState.Loading) ModelCatalogState.Idle else this

    /** 借用对话模型的目录查询实现：图片端点同样是 OpenAI 兼容的 `/models`。 */
    private fun ImageProviderEditForm.toCatalogConfig() = LLMProviderConfig(
        name = name.trim(),
        providerType = LLMProviderType.Custom,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim()
    )

    private fun ImageProviderEditForm.toProviderOrNullWithToast(): ImageProvider? {
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
        val concurrency = maxConcurrentRequests.trim().toIntOrNull()
        if (concurrency == null ||
            concurrency !in MIN_IMAGE_PROVIDER_CONCURRENCY..MAX_IMAGE_PROVIDER_CONCURRENCY
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_params_invalid).tryEmit()
            return null
        }
        return ImageProvider(
            id = mProviderId,
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            size = size.trim().ifBlank { DEFAULT_IMAGE_PROVIDER_SIZE },
            maxConcurrentRequests = concurrency
        )
    }
}

private fun ImageProvider.toEditForm() = ImageProviderEditForm(
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    size = size,
    maxConcurrentRequests = maxConcurrentRequests.toString()
)
