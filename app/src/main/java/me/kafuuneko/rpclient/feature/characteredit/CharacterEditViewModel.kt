package me.kafuuneko.rpclient.feature.characteredit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterLorebookItem
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterProviderItem
import me.kafuuneko.rpclient.feature.characteredit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditDialogState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditLoadState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditMode
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterPromptField
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditViewEvent
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.character.CharacterCardImportDraft
import me.kafuuneko.rpclient.libs.character.CharacterCardRepository
import me.kafuuneko.rpclient.libs.character.LorebookImportPolicy
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.imagegeneration.AVATAR_IMAGE_SIZE
import me.kafuuneko.rpclient.libs.debug.AppLogger
import me.kafuuneko.rpclient.libs.generation.RequestConcurrencyLimiter
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.imagegeneration.AVATAR_APPEARANCE_REFINEMENT_SYSTEM_PROMPT
import me.kafuuneko.rpclient.libs.imagegeneration.AvatarPromptBuilder
import me.kafuuneko.rpclient.libs.imagegeneration.ImageGenerationConfig
import me.kafuuneko.rpclient.libs.imagegeneration.OpenAICompatibleImageClient
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.imageProviderPermitKey
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.ImageProviderRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LLM_PERMIT_SCOPE_IMAGE_PROMPT
import me.kafuuneko.rpclient.utils.orSingleBlank
import me.kafuuneko.rpclient.utils.removeAtOrSelf
import me.kafuuneko.rpclient.utils.updateAt
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 角色创建与编辑页的状态持有者。
 *
 * 核心职责：
 * - 维护角色全量表单字段（基本信息、性格、场景、首条消息、示例对话、系统提示词、历史后指令、深度提示词、替代开场白等）；
 * - 管理角色头像文件生命周期（选择、暂存、替换、保存与未提交临时文件清理）；
 * - 协调角色关联世界书与独立 LLM 提供商配置的校验与动态同步；
 * - 提供多行长文本 Prompt 全屏编辑器弹窗及剪贴板复制能力；
 * - 支持未保存修改防误退脏检查弹窗及级联删除关联世界书确认。
 */
class CharacterEditViewModel : CoreViewModelWithEvent<CharacterEditUiIntent, CharacterEditUiState>(
    CharacterEditUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mImageProviderRepository by inject<ImageProviderRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mCharacterCardRepository by inject<CharacterCardRepository>()
    private val mImageClient by inject<OpenAICompatibleImageClient>()
    private val mRequestConcurrencyLimiter by inject<RequestConcurrencyLimiter>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private var mPendingUpdate: CharacterCardImportDraft? = null

    /** 初始化编辑页，拉取候选世界书与提供商列表，并加载目标角色或新建空表单。 */
    @UiIntentObserver(CharacterEditUiIntent.Init::class)
    private suspend fun onInit(intent: CharacterEditUiIntent.Init) {
        if (!isStateOf<CharacterEditUiState.None>()) return
        // 在 IO 线程并发查询候选世界书列表与提供商列表
        val (lorebooks, providers) = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks() to mLLMRepository.getAllProviders()
        }
        // 若传入角色 ID 则查询角色实体详情
        val character = intent.characterId?.let {
            withContext(Dispatchers.IO) { mCharacterRepository.getCharacterById(it) }
        }
        // 查询该角色独立绑定的 LLM 提供商 ID
        val llmProviderId = character?.let {
            withContext(Dispatchers.IO) { mCharacterRepository.getLLMProviderId(it.id) }
        } ?: 0L
        // 转换实体为 UI 表单数据
        val form = character?.let {
            CharacterEditForm.from(it, llmProviderId)
        } ?: CharacterEditForm()
        // 构建初始 UI 状态
        CharacterEditUiState.Normal(
            mode = if (character == null) CharacterEditMode.Create else CharacterEditMode.Edit,
            form = form.ensureListInputs(),
            avatarImage = form.resolveAvatarImage(),
            availableLorebooks = lorebooks.map { CharacterLorebookItem(it.id, it.name) },
            availableProviders = providers
                .filter { it.isEnabled || it.id == form.llmProviderId }
                .map { provider ->
                    CharacterProviderItem(
                        id = provider.id,
                        name = provider.name,
                        model = provider.model,
                        isEnabled = provider.isEnabled
                    )
                },
            loadState = CharacterEditLoadState.None
        ).setup()
    }

    /** 更新角色绑定的世界书 ID。 */
    @UiIntentObserver(CharacterEditUiIntent.UpdateCharacterLorebook::class)
    private fun onUpdateCharacterLorebook(intent: CharacterEditUiIntent.UpdateCharacterLorebook) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(
            form = uiState.form.copy(characterLorebookId = intent.lorebookId)
        ).setup()
    }

    /** 选择角色绑定的独立 LLM 提供商。 */
    @UiIntentObserver(CharacterEditUiIntent.SelectLLMProvider::class)
    private fun onSelectLLMProvider(intent: CharacterEditUiIntent.SelectLLMProvider) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(
            form = uiState.form.copy(llmProviderId = intent.providerId),
            availableProviders = uiState.availableProviders.filter {
                it.isEnabled || it.id == intent.providerId
            }
        ).setup()
    }

    /** 页面恢复可见时重新校验世界书与模型提供商有效性，清理失效外键关联。 */
    @UiIntentObserver(CharacterEditUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        // 重新拉取最新的世界书与提供商列表
        val (lorebooks, providers) = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks() to mLLMRepository.getAllProviders()
        }
        val availableLorebookIds = lorebooks.mapTo(mutableSetOf()) { it.id }
        val availableProviderIds = providers.mapTo(mutableSetOf()) { it.id }
        // 校验并重置可能已被外部删除的世界书或提供商外键
        uiState.copy(
            form = uiState.form
                .withValidLorebookAssociation(availableLorebookIds)
                .withValidProviderAssociation(availableProviderIds),
            initialForm = uiState.initialForm
                .withValidLorebookAssociation(availableLorebookIds)
                .withValidProviderAssociation(availableProviderIds),
            availableLorebooks = lorebooks.map { CharacterLorebookItem(it.id, it.name) },
            availableProviders = providers
                .filter { it.isEnabled || it.id == uiState.form.llmProviderId }
                .map { provider ->
                    CharacterProviderItem(
                        id = provider.id,
                        name = provider.name,
                        model = provider.model,
                        isEnabled = provider.isEnabled
                    )
                }
        ).setup()
    }

    /** 打开世界书管理列表页面。 */
    @UiIntentObserver(CharacterEditUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    /** 处理返回操作，若有未保存修改弹出防误退弹窗，否则清理临时文件并退出。 */
    @UiIntentObserver(CharacterEditUiIntent.Back::class)
    private suspend fun onBack() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (
            uiState.loadState == CharacterEditLoadState.Saving ||
            uiState.loadState == CharacterEditLoadState.Deleting
        ) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(dialogState = CharacterEditDialogState.UnsavedChangesConfirm).setup()
            return
        }
        finishEditing()
    }

    /** 打开头像来源操作面板。 */
    @UiIntentObserver(CharacterEditUiIntent.PickAvatarClick::class)
    private fun onPickAvatarClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.loadState != CharacterEditLoadState.None) return
        uiState.copy(dialogState = CharacterEditDialogState.AvatarActions).setup()
    }

    /** 从头像操作面板进入既有相册选择与裁剪流程。 */
    @UiIntentObserver(CharacterEditUiIntent.ChooseAvatarFromAlbum::class)
    private fun onChooseAvatarFromAlbum() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState != CharacterEditDialogState.AvatarActions || uiState.isAvatarGenerating) return
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
        CharacterEditViewEvent.OpenAvatarPicker.tryEmit()
    }

    /**
     * 接收裁剪页生成的文件，并替换表单持有的临时头像。
     *
     * 连续选择时只删除尚未提交的上一份临时文件；数据库仍引用的原头像必须等角色保存
     * 成功或用户确认删除后才能清理。
     */
    @UiIntentObserver(CharacterEditUiIntent.AvatarCropped::class)
    private suspend fun onAvatarCropped(intent: CharacterEditUiIntent.AvatarCropped) {
        if (!replaceDraftAvatar(intent.fileUuid)) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_avatar_save_failed).tryEmit()
        }
    }

    /** 清空自定义头像，恢复既有首字母与稳定色默认头像。 */
    @UiIntentObserver(CharacterEditUiIntent.RestoreDefaultAvatar::class)
    private suspend fun onRestoreDefaultAvatar() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState != CharacterEditDialogState.AvatarActions || uiState.isAvatarGenerating) return
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
        if (!replaceDraftAvatar("")) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_avatar_save_failed).tryEmit()
        }
    }

    /** 根据当前尚未保存的角色表单直接生成方形头像。 */
    @UiIntentObserver(CharacterEditUiIntent.GenerateAvatar::class)
    private fun onGenerateAvatar() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (
            uiState.dialogState != CharacterEditDialogState.AvatarActions ||
            uiState.isAvatarGenerating
        ) return

        val characterName = uiState.form.name
        val characterDescription = uiState.form.description
        val characterProviderId = uiState.form.llmProviderId
        uiState.copy(
            dialogState = CharacterEditDialogState.None,
            isAvatarGenerating = true
        ).setup()
        viewModelScope.launch {
            val provider = withContext(Dispatchers.IO) {
                mImageProviderRepository.getSelectedProvider()
            }
            val config = provider?.let {
                ImageGenerationConfig.fromProvider(it, size = AVATAR_IMAGE_SIZE)
            }
            if (provider == null || config == null || !config.isConfigured) {
                getOrNull<CharacterEditUiState.Normal>()
                    ?.copy(isAvatarGenerating = false)
                    ?.setup()
                AppViewEvent.PopupToastMessageByResId(R.string.image_generation_not_configured)
                    .tryEmit()
                return@launch
            }
            var generatedUuid: String? = null
            try {
                val prompt = AvatarPromptBuilder.buildAvatarPrompt(
                    characterName = characterName,
                    characterDescription = refineAvatarAppearance(
                        characterName = characterName,
                        characterDescription = characterDescription,
                        characterProviderId = characterProviderId
                    ),
                    avatarStylePrompt = AppModel.imageGenerationAvatarStylePrompt
                )
                // 与聊天配图共用同一条图片服务的配额，否则并发上限对头像生成形同虚设。
                val generated = mRequestConcurrencyLimiter.withPermit(
                    key = imageProviderPermitKey(provider.id),
                    limit = provider.maxConcurrentRequests
                ) {
                    mImageClient.generate(config, prompt)
                }
                generatedUuid = mFileRepository.saveBytes(generated.bytes, generated.mimeType)
                val avatarUuid = requireNotNull(generatedUuid)
                generatedUuid = null // replaceDraftAvatar now owns cleanup for this file.
                val attached = replaceDraftAvatar(avatarUuid)
                if (!attached) error("Generated avatar could not be attached")
            } catch (cancellation: CancellationException) {
                generatedUuid?.let { uuid ->
                    withContext(NonCancellable + Dispatchers.IO) {
                        mFileRepository.deleteFile(uuid)
                    }
                }
                throw cancellation
            } catch (_: Exception) {
                generatedUuid?.let { uuid ->
                    withContext(Dispatchers.IO) { mFileRepository.deleteFile(uuid) }
                }
                AppViewEvent.PopupToastMessageByResId(R.string.avatar_generation_failed).tryEmit()
            } finally {
                getOrNull<CharacterEditUiState.Normal>()?.let { latestState ->
                    if (latestState.isAvatarGenerating) {
                        latestState.copy(isAvatarGenerating = false).setup()
                    }
                }
            }
        }
    }

    /**
     * 用模型把角色描述提炼成纯外貌段落。
     *
     * 与聊天配图的场景提炼是同一手法：角色卡里性格、背景、关系占了大头，原样喂给绘图
     * 模型会把外貌特征稀释掉。任何失败都回退到原始描述，绝不因此挡住头像生成。
     */
    private suspend fun refineAvatarAppearance(
        characterName: String,
        characterDescription: String,
        characterProviderId: Long
    ): String {
        val description = characterDescription.trim()
        if (description.length < AVATAR_REFINEMENT_MIN_LENGTH) return description
        return try {
            val provider = withContext(Dispatchers.IO) {
                mProviderSelectionResolver.requireImagePromptProvider(characterProviderId)
            }
            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithProvider(
                    provider = provider,
                    request = LLMGenerationRequest(
                        messages = listOf(
                            LLMMessage(
                                LLMMessageRole.System,
                                AVATAR_APPEARANCE_REFINEMENT_SYSTEM_PROMPT
                            ),
                            LLMMessage(
                                LLMMessageRole.User,
                                "Character name:\n${characterName.trim().ifBlank { "(none)" }}\n\n" +
                                    "Character description:\n$description"
                            )
                        ),
                        options = LLMGenerationOptions(
                            temperature = AVATAR_REFINEMENT_TEMPERATURE,
                            maxTokens = AVATAR_REFINEMENT_MAX_TOKENS
                        ),
                        includeReasoningInContent = false,
                        captureReasoning = false,
                        isPromptFinalized = true
                    ),
                    permitScope = LLM_PERMIT_SCOPE_IMAGE_PROMPT
                )
            }
            response.content.trim().takeIf { it.isNotEmpty() } ?: description
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLogger.w("Image", "Avatar appearance refinement failed: ${error.message}")
            description
        }
    }

    /** 修改角色名称。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: CharacterEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    /** 追加一个空的标签项。 */
    @UiIntentObserver(CharacterEditUiIntent.AddTag::class)
    private fun onAddTag() =
        updateForm { copy(tags = tags + "") }

    /** 批量设置标签列表。 */
    @UiIntentObserver(CharacterEditUiIntent.SetTags::class)
    private fun onSetTags(intent: CharacterEditUiIntent.SetTags) =
        updateForm { copy(tags = intent.tags.orSingleBlank()) }

    /** 修改指定索引处的标签。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeTag::class)
    private fun onChangeTag(intent: CharacterEditUiIntent.ChangeTag) =
        updateForm { copy(tags = tags.updateAt(intent.index, intent.value)) }

    /** 删除指定索引处的标签。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteTag::class)
    private fun onDeleteTag(intent: CharacterEditUiIntent.DeleteTag) =
        updateForm { copy(tags = tags.removeAtOrSelf(intent.index).orSingleBlank()) }

    /** 修改角色描述（Description）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDescription::class)
    private fun onChangeDescription(intent: CharacterEditUiIntent.ChangeDescription) =
        updateForm { copy(description = intent.value) }

    /** 修改作者附言（Creator Notes）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCreatorNotes::class)
    private fun onChangeCreatorNotes(intent: CharacterEditUiIntent.ChangeCreatorNotes) =
        updateForm { copy(creatorNotes = intent.value) }

    /** 修改创作者名称（Creator）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCreator::class)
    private fun onChangeCreator(intent: CharacterEditUiIntent.ChangeCreator) =
        updateForm { copy(creator = intent.value) }

    /** 修改角色卡版本号（Character Version）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCharacterVersion::class)
    private fun onChangeCharacterVersion(intent: CharacterEditUiIntent.ChangeCharacterVersion) =
        updateForm { copy(characterVersion = intent.value) }

    /** 修改性格描述（Personality）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePersonality::class)
    private fun onChangePersonality(intent: CharacterEditUiIntent.ChangePersonality) =
        updateForm { copy(personality = intent.value) }

    /** 修改场景设定（Scenario）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeScenario::class)
    private fun onChangeScenario(intent: CharacterEditUiIntent.ChangeScenario) =
        updateForm { copy(scenario = intent.value) }

    /** 追加一个空的首条消息（First Message）输入项。 */
    @UiIntentObserver(CharacterEditUiIntent.AddFirstMessage::class)
    private fun onAddFirstMessage() =
        updateForm { copy(firstMessages = firstMessages + "") }

    /** 修改指定索引处的首条消息。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeFirstMessage::class)
    private fun onChangeFirstMessage(intent: CharacterEditUiIntent.ChangeFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.updateAt(intent.index, intent.value)) }

    /** 删除指定索引处的首条消息。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteFirstMessage::class)
    private fun onDeleteFirstMessage(intent: CharacterEditUiIntent.DeleteFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.removeAtOrSelf(intent.index).orSingleBlank()) }

    /** 修改示例对话（Examples of Dialogue）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeExamplesOfDialogue::class)
    private fun onChangeExamplesOfDialogue(intent: CharacterEditUiIntent.ChangeExamplesOfDialogue) =
        updateForm { copy(examplesOfDialogue = intent.value) }

    /** 修改历史后指令（Post-History Instructions）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePostHistoryInstructions::class)
    private fun onChangePostHistoryInstructions(intent: CharacterEditUiIntent.ChangePostHistoryInstructions) =
        updateForm { copy(postHistoryInstructions = intent.value) }

    /** 修改主系统提示词（System Prompt）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeSystemPrompt::class)
    private fun onChangeSystemPrompt(intent: CharacterEditUiIntent.ChangeSystemPrompt) =
        updateForm { copy(systemPrompt = intent.value) }

    /** 修改角色深度提示词内容（Depth Prompt）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptPrompt::class)
    private fun onChangeDepthPromptPrompt(intent: CharacterEditUiIntent.ChangeDepthPromptPrompt) =
        updateForm { copy(depthPromptPrompt = intent.value) }

    /** 修改角色深度提示词注入深度（Depth Prompt Depth）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptDepth::class)
    private fun onChangeDepthPromptDepth(intent: CharacterEditUiIntent.ChangeDepthPromptDepth) =
        updateForm { copy(depthPromptDepth = intent.value) }

    /** 修改角色深度提示词消息角色（Depth Prompt Role）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptRole::class)
    private fun onChangeDepthPromptRole(intent: CharacterEditUiIntent.ChangeDepthPromptRole) =
        updateForm { copy(depthPromptRole = intent.value) }

    /** 追加一个空的替代开场白（Alternate Greeting）输入项。 */
    @UiIntentObserver(CharacterEditUiIntent.AddAlternateGreeting::class)
    private fun onAddAlternateGreeting() =
        updateForm { copy(alternateGreetings = alternateGreetings + "") }

    /** 修改指定索引处的替代开场白。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeAlternateGreeting::class)
    private fun onChangeAlternateGreeting(intent: CharacterEditUiIntent.ChangeAlternateGreeting) =
        updateForm { copy(alternateGreetings = alternateGreetings.updateAt(intent.index, intent.value)) }

    /** 删除指定索引处的替代开场白。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteAlternateGreeting::class)
    private fun onDeleteAlternateGreeting(intent: CharacterEditUiIntent.DeleteAlternateGreeting) =
        updateForm { copy(alternateGreetings = alternateGreetings.removeAtOrSelf(intent.index).orSingleBlank()) }

    /** 修改扩展 JSON 字符串。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeExtensionsJson::class)
    private fun onChangeExtensionsJson(intent: CharacterEditUiIntent.ChangeExtensionsJson) =
        updateForm { copy(extensionsJson = intent.value) }

    /** 打开长文本字段的全屏 Prompt 编辑器弹窗。 */
    @UiIntentObserver(CharacterEditUiIntent.ShowPromptEditor::class)
    private fun onShowPromptEditor(intent: CharacterEditUiIntent.ShowPromptEditor) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val draftText = uiState.form.promptText(intent.field) ?: return
        uiState.copy(
            dialogState = CharacterEditDialogState.PromptEditor(intent.field, draftText)
        ).setup()
    }

    /** 更新全屏 Prompt 编辑器中的临时草稿文本。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePromptEditorDraft::class)
    private fun onChangePromptEditorDraft(intent: CharacterEditUiIntent.ChangePromptEditorDraft) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        uiState.copy(dialogState = dialogState.copy(draftText = intent.value)).setup()
    }

    /** 复制全屏 Prompt 编辑器中的草稿文本到剪贴板。 */
    @UiIntentObserver(CharacterEditUiIntent.CopyPromptEditorText::class)
    private fun onCopyPromptEditorText() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        CharacterEditViewEvent.CopyText(dialogState.draftText).tryEmit()
    }

    /** 确认并回写全屏 Prompt 编辑器中的内容至主表单。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmPromptEditor::class)
    private fun onConfirmPromptEditor() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        uiState.copy(
            form = uiState.form.withPromptText(dialogState.field, dialogState.draftText),
            dialogState = CharacterEditDialogState.None
        ).setup()
    }

    /** 先提交角色对新头像的引用，再清理不再使用的原头像文件。 */
    @UiIntentObserver(CharacterEditUiIntent.SaveCharacter::class)
    private suspend fun onSaveCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val character = uiState.form.toCharacterOrNullWithToast() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        // 在 IO 线程保存角色及关联的 LLM 提供商配置
        withContext(Dispatchers.IO) {
            mCharacterRepository.saveCharacterWithLLMProvider(
                character = character,
                llmProviderId = uiState.form.llmProviderId
            )
            // 提交成功后安全删除被替换的原头像物理文件
            if (uiState.form.originalAvatar.isNotBlank() && uiState.form.originalAvatar != character.avatar) {
                mFileRepository.deleteFile(uiState.form.originalAvatar)
            }
        }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == CharacterEditMode.Create) R.string.character_created else R.string.character_saved
        ).tryEmit()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 打开角色卡更新来源选择，仅已有角色可用。 */
    @UiIntentObserver(CharacterEditUiIntent.UpdateCharacterClick::class)
    private fun onUpdateCharacterClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.mode != CharacterEditMode.Edit || uiState.loadState != CharacterEditLoadState.None) return
        mPendingUpdate = null
        uiState.copy(dialogState = CharacterEditDialogState.UpdateSource).setup()
    }

    /** 从来源选择面板进入 JSON 粘贴编辑器。 */
    @UiIntentObserver(CharacterEditUiIntent.PasteUpdateJsonClick::class)
    private fun onPasteUpdateJsonClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState != CharacterEditDialogState.UpdateSource) return
        uiState.copy(dialogState = CharacterEditDialogState.UpdateJsonEditor("")).setup()
    }

    /** 从来源选择面板打开系统 JSON 文件选择器。 */
    @UiIntentObserver(CharacterEditUiIntent.PickUpdateJsonFileClick::class)
    private fun onPickUpdateJsonFileClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState != CharacterEditDialogState.UpdateSource) return
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
        CharacterEditViewEvent.OpenCharacterUpdateFilePicker.tryEmit()
    }

    /** 更新粘贴 JSON 编辑器草稿。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeUpdateJsonDraft::class)
    private fun onChangeUpdateJsonDraft(intent: CharacterEditUiIntent.ChangeUpdateJsonDraft) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? CharacterEditDialogState.UpdateJsonEditor ?: return
        uiState.copy(dialogState = dialog.copy(draftText = intent.value)).setup()
    }

    /** 解析粘贴的角色卡 JSON；失败时保留编辑器与原草稿。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmUpdateJson::class)
    private suspend fun onConfirmUpdateJson() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? CharacterEditDialogState.UpdateJsonEditor ?: return
        try {
            prepareCharacterUpdate(mCharacterCardRepository.readImportFromJson(dialog.draftText))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.invalid_character_card_json).tryEmit()
        }
    }

    /** 解析系统文件选择器返回的 JSON 角色卡。 */
    @UiIntentObserver(CharacterEditUiIntent.UpdateCharacterJsonSelected::class)
    private suspend fun onUpdateCharacterJsonSelected(intent: CharacterEditUiIntent.UpdateCharacterJsonSelected) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.mode != CharacterEditMode.Edit || uiState.loadState != CharacterEditLoadState.None) return
        try {
            prepareCharacterUpdate(mCharacterCardRepository.readImportFromUri(intent.uri))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.update_character_failed).tryEmit()
        }
    }

    /** 低预算内嵌世界书改为跟随全局预算后继续确认。 */
    @UiIntentObserver(CharacterEditUiIntent.UpdateCharacterWithGlobalLorebookBudget::class)
    private fun onUpdateCharacterWithGlobalLorebookBudget() {
        resolvePendingUpdateBudget(followGlobal = true)
    }

    /** 保留低预算内嵌世界书的原固定预算后继续确认。 */
    @UiIntentObserver(CharacterEditUiIntent.UpdateCharacterWithOriginalLorebookBudget::class)
    private fun onUpdateCharacterWithOriginalLorebookBudget() {
        resolvePendingUpdateBudget(followGlobal = false)
    }

    /** 用户最终确认后覆盖持久化角色，并重新加载编辑表单而不退出页面。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmCharacterUpdate::class)
    private suspend fun onConfirmCharacterUpdate() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.ConfirmCharacterUpdate) return
        val draft = mPendingUpdate ?: return
        val characterId = uiState.form.id.takeIf { uiState.mode == CharacterEditMode.Edit && it != 0L } ?: return
        mPendingUpdate = null
        uiState.copy(
            loadState = CharacterEditLoadState.Saving,
            dialogState = CharacterEditDialogState.None
        ).setup()

        try {
            val (character, llmProviderId) = withContext(NonCancellable + Dispatchers.IO) {
                uiState.form.avatar
                    .takeIf { it.isNotBlank() && it != uiState.form.originalAvatar }
                    ?.let { mFileRepository.deleteFile(it) }
                mCharacterCardRepository.updateFromDraft(characterId, draft)
                val updated = requireNotNull(mCharacterRepository.getCharacterById(characterId))
                updated to mCharacterRepository.getLLMProviderId(characterId)
            }
            showReloadedCharacter(character, llmProviderId)
            AppViewEvent.PopupToastMessageByResId(R.string.character_updated).tryEmit()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            reloadPersistedCharacterAfterFailedUpdate(characterId)
            AppViewEvent.PopupToastMessageByResId(R.string.update_character_failed).tryEmit()
        }
    }

    /** 点击删除角色，检查是否有关联世界书并弹出相应确认对话框。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteCharacterClick::class)
    private suspend fun onDeleteCharacterClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.isNew) {
            cleanupPendingAvatar()
            CharacterEditUiState.finished(uiStateFlow.value).setup()
            return
        }
        val savedCharacter = withContext(Dispatchers.IO) {
            mCharacterRepository.getCharacterById(uiState.form.id)
        } ?: run {
            CharacterEditUiState.finished(uiStateFlow.value).setup()
            return
        }
        val associatedLorebook = savedCharacter.characterLorebookId
            .takeIf { it != 0L }
            ?.let { lorebookId ->
                withContext(Dispatchers.IO) {
                    mLorebookRepository.getLorebookById(lorebookId)
                }
            }
        uiState.copy(
            dialogState = if (associatedLorebook == null) {
                CharacterEditDialogState.DeleteConfirm(
                    characterName = savedCharacter.name
                )
            } else {
                CharacterEditDialogState.DeleteWithLorebookConfirm(
                    characterName = savedCharacter.name,
                    lorebookId = associatedLorebook.id,
                    lorebookName = associatedLorebook.name
                )
            }
        ).setup()
    }

    /** 确认删除无关联世界书的角色。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacter::class)
    private suspend fun onConfirmDeleteCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.DeleteConfirm) return
        deleteCharacter()
    }

    /** 用户确认仅删除角色本身，保留关联世界书。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacterOnly::class)
    private suspend fun onConfirmDeleteCharacterOnly() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.DeleteWithLorebookConfirm) return
        deleteCharacter()
    }

    /** 用户确认一并删除角色及其绑定的专属世界书。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacterWithLorebook::class)
    private suspend fun onConfirmDeleteCharacterWithLorebook() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.DeleteWithLorebookConfirm
            ?: return
        deleteCharacter(lorebookId = dialogState.lorebookId)
    }

    /**
     * 删除角色及其头像；只有用户在关联确认中明确选择时才一并删除世界书。
     *
     * 表单尚未提交的新头像不受数据库实体追踪，需要在同一流程中额外清理。
     *
     * @param lorebookId 需一并删除的关联世界书 ID（若有）
     */
    private suspend fun deleteCharacter(lorebookId: Long? = null) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        uiState.copy(
            loadState = CharacterEditLoadState.Deleting,
            dialogState = CharacterEditDialogState.None
        ).setup()
        val pendingAvatar = uiState.form.avatar
            .takeIf { it.isNotBlank() && it != uiState.form.originalAvatar }
        withContext(Dispatchers.IO) {
            val character = mCharacterRepository.getCharacterById(uiState.form.id)
            // 可选删除关联的世界书
            lorebookId?.let {
                mLorebookRepository.deleteLorebook(it)
            }
            // 删除角色实体
            mCharacterRepository.deleteCharacter(uiState.form.id)
            // 删除已持久化的旧头像文件
            character?.avatar?.takeIf { it.isNotBlank() }?.let {
                mFileRepository.deleteFile(it)
            }
            // 删除未持久化的新头像临时文件
            pendingAvatar?.let {
                mFileRepository.deleteFile(it)
            }
        }
        AppViewEvent.PopupToastMessageByResId(R.string.character_deleted).tryEmit()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 确认放弃未保存的修改，直接退出编辑流程。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDiscardChanges::class)
    private suspend fun onConfirmDiscardChanges() {
        finishEditing()
    }

    /** 关闭头像或更新来源操作面板；动作已切换到下一弹窗时自动忽略随后到达的关闭回调。 */
    @UiIntentObserver(CharacterEditUiIntent.DismissActionDialog::class)
    private fun onDismissActionDialog() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (
            uiState.dialogState != CharacterEditDialogState.AvatarActions &&
            uiState.dialogState != CharacterEditDialogState.UpdateSource
        ) return
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
    }

    /** 关闭当前显示的非操作面板弹窗。 */
    @UiIntentObserver(CharacterEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (
            uiState.dialogState is CharacterEditDialogState.UpdateJsonEditor ||
            uiState.dialogState is CharacterEditDialogState.LowEmbeddedLorebookBudgetConfirm ||
            uiState.dialogState is CharacterEditDialogState.ConfirmCharacterUpdate
        ) {
            mPendingUpdate = null
        }
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
    }

    /**
     * 用新 UUID 替换当前草稿头像；相册裁剪、AI 生成和恢复默认共用同一临时文件规则。
     *
     * 数据库原头像仅记录在 [CharacterEditForm.originalAvatar]，这里绝不删除；当前未提交头像
     * 被替换时立即清理。调用失败或页面已结束时，新产生的文件也会被补偿删除。
     */
    private suspend fun replaceDraftAvatar(fileUuid: String): Boolean {
        val initialState = getOrNull<CharacterEditUiState.Normal>()
        if (initialState == null) {
            fileUuid.takeIf { it.isNotBlank() }?.let {
                withContext(NonCancellable + Dispatchers.IO) { mFileRepository.deleteFile(it) }
            }
            return false
        }
        return try {
            val avatarImage = fileUuid.takeIf { it.isNotBlank() }?.let {
                withContext(Dispatchers.IO) { mFileRepository.loadBitmap(it)?.asImageBitmap() }
            }
            val latestState = getOrNull<CharacterEditUiState.Normal>() ?: run {
                fileUuid.takeIf { it.isNotBlank() }?.let {
                    withContext(NonCancellable + Dispatchers.IO) { mFileRepository.deleteFile(it) }
                }
                return false
            }
            withContext(Dispatchers.IO) {
                latestState.form.avatar
                    .takeIf {
                        it.isNotBlank() &&
                            it != latestState.form.originalAvatar &&
                            it != fileUuid
                    }
                    ?.let { mFileRepository.deleteFile(it) }
            }
            val finalState = getOrNull<CharacterEditUiState.Normal>() ?: run {
                fileUuid.takeIf { it.isNotBlank() }?.let {
                    withContext(NonCancellable + Dispatchers.IO) { mFileRepository.deleteFile(it) }
                }
                return false
            }
            finalState.copy(
                form = finalState.form.copy(avatar = fileUuid),
                avatarImage = avatarImage
            ).setup()
            true
        } catch (cancellation: CancellationException) {
            fileUuid.takeIf { it.isNotBlank() }?.let {
                withContext(NonCancellable + Dispatchers.IO) { mFileRepository.deleteFile(it) }
            }
            throw cancellation
        } catch (_: Exception) {
            fileUuid.takeIf { it.isNotBlank() }?.let {
                withContext(NonCancellable + Dispatchers.IO) { mFileRepository.deleteFile(it) }
            }
            false
        }
    }



    /** 保存解析结果，并按内嵌世界书预算决定下一步确认弹窗。 */
    private fun prepareCharacterUpdate(draft: CharacterCardImportDraft) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.mode != CharacterEditMode.Edit) return
        mPendingUpdate = draft
        uiState.copy(
            dialogState = if (LorebookImportPolicy.requiresLowBudgetConfirmation(draft.card)) {
                CharacterEditDialogState.LowEmbeddedLorebookBudgetConfirm(
                    importedTokenBudget = requireNotNull(draft.card.embeddedLorebook).lorebook.tokenBudget
                )
            } else {
                pendingUpdateDialog(uiState, draft)
            }
        ).setup()
    }

    /** 应用用户选择的内嵌世界书预算策略并进入最终更新确认。 */
    private fun resolvePendingUpdateBudget(followGlobal: Boolean) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.LowEmbeddedLorebookBudgetConfirm) return
        val pending = mPendingUpdate ?: return
        val resolved = pending.copy(
            card = LorebookImportPolicy.resolveBudget(pending.card, followGlobal)
        )
        mPendingUpdate = resolved
        uiState.copy(dialogState = pendingUpdateDialog(uiState, resolved)).setup()
    }

    /** 构造最终覆盖确认，脏表单提示只在确有未保存编辑时显示。 */
    private fun pendingUpdateDialog(
        uiState: CharacterEditUiState.Normal,
        draft: CharacterCardImportDraft
    ) = CharacterEditDialogState.ConfirmCharacterUpdate(
        currentName = uiState.form.name,
        importedName = draft.card.character.name,
        hasEmbeddedLorebook = draft.card.embeddedLorebook != null,
        willDiscardUnsavedChanges = uiState.form.hasUnsavedChangesFrom(uiState.initialForm)
    )

    /** 用数据库最新角色重置表单、基线与头像，保持当前编辑页。 */
    private suspend fun showReloadedCharacter(character: Character, llmProviderId: Long) {
        val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val form = CharacterEditForm.from(character, llmProviderId).ensureListInputs()
        latestState.copy(
            form = form,
            initialForm = form,
            avatarImage = form.resolveAvatarImage(),
            loadState = CharacterEditLoadState.None,
            dialogState = CharacterEditDialogState.None
        ).setup()
    }

    /** 更新失败后恢复数据库版本，避免表单继续引用已清理的临时头像。 */
    private suspend fun reloadPersistedCharacterAfterFailedUpdate(characterId: Long) {
        val persisted = withContext(Dispatchers.IO) {
            val character = mCharacterRepository.getCharacterById(characterId)
            character?.let { it to mCharacterRepository.getLLMProviderId(characterId) }
        }
        if (persisted != null) {
            showReloadedCharacter(persisted.first, persisted.second)
        } else {
            getOrNull<CharacterEditUiState.Normal>()?.copy(
                loadState = CharacterEditLoadState.None,
                dialogState = CharacterEditDialogState.None
            )?.setup()
        }
    }

    /** 辅助方法：以不可变方式更新当前角色表单数据。 */
    private fun updateForm(block: CharacterEditForm.() -> CharacterEditForm) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    /** 将表单转换为角色实体，名称为空时弹出 Toast 提示并返回 null。 */
    private fun CharacterEditForm.toCharacterOrNullWithToast(): Character? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_name_empty).tryEmit()
            return null
        }
        return toCharacter()
    }

    /** 异步加载当前表单持有的头像图片并解码为 ImageBitmap。 */
    private suspend fun CharacterEditForm.resolveAvatarImage() =
        avatar.takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.IO) { mFileRepository.loadBitmap(it)?.asImageBitmap() }
        }

    /** 清理尚未提交保存的新头像临时物理文件。 */
    private suspend fun cleanupPendingAvatar() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.avatar.isBlank() || uiState.form.avatar == uiState.form.originalAvatar) return
        withContext(Dispatchers.IO) {
            mFileRepository.deleteFile(uiState.form.avatar)
        }
    }

    /** 结束编辑流程：清理未提交的临时头像文件并迁移至 Finished 状态。 */
    private suspend fun finishEditing() {
        cleanupPendingAvatar()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 确保所有列表输入项至少包含一个输入框。 */
    private fun CharacterEditForm.ensureListInputs(): CharacterEditForm {
        return copy(
            tags = tags.orSingleBlank(),
            firstMessages = firstMessages.orSingleBlank(),
            alternateGreetings = alternateGreetings.orSingleBlank()
        )
    }

    /** 校验绑定的世界书 ID 是否仍然存在，若已被删除则重置为 0。 */
    private fun CharacterEditForm.withValidLorebookAssociation(
        availableLorebookIds: Set<Long>
    ): CharacterEditForm {
        if (characterLorebookId == 0L || characterLorebookId in availableLorebookIds) return this
        return copy(characterLorebookId = 0L)
    }

    /** 校验绑定的 LLM 提供商 ID 是否仍然存在，若已被删除则重置为 0。 */
    private fun CharacterEditForm.withValidProviderAssociation(
        availableProviderIds: Set<Long>
    ): CharacterEditForm {
        if (llmProviderId == 0L || llmProviderId in availableProviderIds) return this
        return copy(llmProviderId = 0L)
    }

    /** 根据目标枚举字段读取对应的 Prompt 文本内容。 */
    private fun CharacterEditForm.promptText(field: CharacterPromptField): String? {
        return when (field) {
            CharacterPromptField.Description -> description
            CharacterPromptField.Personality -> personality
            CharacterPromptField.Scenario -> scenario
            is CharacterPromptField.FirstMessage -> firstMessages.getOrNull(field.index)
            CharacterPromptField.DialogueExamples -> examplesOfDialogue
            CharacterPromptField.SystemPrompt -> systemPrompt
            CharacterPromptField.PostHistoryInstructions -> postHistoryInstructions
            CharacterPromptField.DepthPrompt -> depthPromptPrompt
            is CharacterPromptField.AlternateGreeting -> alternateGreetings.getOrNull(field.index)
        }
    }

    /** 根据目标枚举字段将 Prompt 编辑器中的文本回写至表单对应字段。 */
    private fun CharacterEditForm.withPromptText(
        field: CharacterPromptField,
        text: String
    ): CharacterEditForm {
        return when (field) {
            CharacterPromptField.Description -> copy(description = text)
            CharacterPromptField.Personality -> copy(personality = text)
            CharacterPromptField.Scenario -> copy(scenario = text)
            is CharacterPromptField.FirstMessage -> copy(
                firstMessages = firstMessages.updateAt(field.index, text)
            )
            CharacterPromptField.DialogueExamples -> copy(examplesOfDialogue = text)
            CharacterPromptField.SystemPrompt -> copy(systemPrompt = text)
            CharacterPromptField.PostHistoryInstructions -> copy(postHistoryInstructions = text)
            CharacterPromptField.DepthPrompt -> copy(depthPromptPrompt = text)
            is CharacterPromptField.AlternateGreeting -> copy(
                alternateGreetings = alternateGreetings.updateAt(field.index, text)
            )
        }
    }

    private companion object {
        /** 短描述本身就是外貌，多一次模型往返只会增加延迟。 */
        const val AVATAR_REFINEMENT_MIN_LENGTH = 80
        const val AVATAR_REFINEMENT_TEMPERATURE = 0.2f
        const val AVATAR_REFINEMENT_MAX_TOKENS = 220
    }
}
