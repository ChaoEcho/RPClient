package me.kafuuneko.rpclient.feature.characteredit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterLorebookItem
import me.kafuuneko.rpclient.feature.characteredit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditDialogState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditLoadState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditMode
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditViewEvent
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.utils.orSingleBlank
import me.kafuuneko.rpclient.libs.utils.removeAtOrSelf
import me.kafuuneko.rpclient.libs.utils.updateAt
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 角色创建与编辑页的状态持有者。
 *
 * 负责头像文件生命周期、绑定世界书有效性校验、表单脏检查及保存删除流程。
 */
class CharacterEditViewModel : CoreViewModelWithEvent<CharacterEditUiIntent, CharacterEditUiState>(
    CharacterEditUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()

    @UiIntentObserver(CharacterEditUiIntent.Init::class)
    private suspend fun onInit(intent: CharacterEditUiIntent.Init) {
        if (!isStateOf<CharacterEditUiState.None>()) return
        val lorebooks = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks()
        }
        val character = intent.characterId?.let {
            withContext(Dispatchers.IO) { mCharacterRepository.getCharacterById(it) }
        }
        val form = character?.let { CharacterEditForm.from(it) } ?: CharacterEditForm()
        CharacterEditUiState.Normal(
            mode = if (character == null) CharacterEditMode.Create else CharacterEditMode.Edit,
            form = form.ensureListInputs(),
            avatarImage = form.resolveAvatarImage(),
            availableLorebooks = lorebooks.map { CharacterLorebookItem(it.id, it.name) },
            loadState = CharacterEditLoadState.None
        ).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.UpdateCharacterLorebook::class)
    private fun onUpdateCharacterLorebook(intent: CharacterEditUiIntent.UpdateCharacterLorebook) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(
            form = uiState.form.copy(characterLorebookId = intent.lorebookId)
        ).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val lorebooks = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks()
        }
        val availableIds = lorebooks.mapTo(mutableSetOf()) { it.id }
        uiState.copy(
            form = uiState.form.withValidLorebookAssociation(availableIds),
            initialForm = uiState.initialForm.withValidLorebookAssociation(availableIds),
            availableLorebooks = lorebooks.map { CharacterLorebookItem(it.id, it.name) }
        ).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

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

    @UiIntentObserver(CharacterEditUiIntent.PickAvatarClick::class)
    private fun onPickAvatarClick() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        CharacterEditViewEvent.OpenAvatarPicker.tryEmit()
    }

    /**
     * 将选择结果复制到应用私有文件，并替换表单持有的临时头像。
     *
     * 连续选择时只删除尚未提交的上一份临时文件；数据库仍引用的原头像必须等角色保存
     * 成功或用户确认删除后才能清理。
     */
    @UiIntentObserver(CharacterEditUiIntent.AvatarSelected::class)
    private suspend fun onAvatarSelected(intent: CharacterEditUiIntent.AvatarSelected) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        val avatarUuid = runCatching {
            withContext(Dispatchers.IO) {
                val uuid = mFileRepository.saveFile(intent.uri)
                if (
                    uiState.form.avatar.isNotBlank() &&
                    uiState.form.avatar != uiState.form.originalAvatar &&
                    uiState.form.avatar != uuid
                ) {
                    mFileRepository.deleteFile(uiState.form.avatar)
                }
                uuid
            }
        }.getOrElse {
            val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
            latestState.copy(loadState = CharacterEditLoadState.None).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.character_avatar_save_failed).tryEmit()
            return
        }
        val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val form = latestState.form.copy(avatar = avatarUuid)
        latestState.copy(
            form = form,
            avatarImage = form.resolveAvatarImage(),
            loadState = CharacterEditLoadState.None
        ).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: CharacterEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.AddTag::class)
    private fun onAddTag() =
        updateForm { copy(tags = tags + "") }

    @UiIntentObserver(CharacterEditUiIntent.SetTags::class)
    private fun onSetTags(intent: CharacterEditUiIntent.SetTags) =
        updateForm { copy(tags = intent.tags.orSingleBlank()) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeTag::class)
    private fun onChangeTag(intent: CharacterEditUiIntent.ChangeTag) =
        updateForm { copy(tags = tags.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(CharacterEditUiIntent.DeleteTag::class)
    private fun onDeleteTag(intent: CharacterEditUiIntent.DeleteTag) =
        updateForm { copy(tags = tags.removeAtOrSelf(intent.index).orSingleBlank()) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeDescription::class)
    private fun onChangeDescription(intent: CharacterEditUiIntent.ChangeDescription) =
        updateForm { copy(description = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeCreatorNotes::class)
    private fun onChangeCreatorNotes(intent: CharacterEditUiIntent.ChangeCreatorNotes) =
        updateForm { copy(creatorNotes = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeCreator::class)
    private fun onChangeCreator(intent: CharacterEditUiIntent.ChangeCreator) =
        updateForm { copy(creator = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeCharacterVersion::class)
    private fun onChangeCharacterVersion(intent: CharacterEditUiIntent.ChangeCharacterVersion) =
        updateForm { copy(characterVersion = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangePersonality::class)
    private fun onChangePersonality(intent: CharacterEditUiIntent.ChangePersonality) =
        updateForm { copy(personality = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeScenario::class)
    private fun onChangeScenario(intent: CharacterEditUiIntent.ChangeScenario) =
        updateForm { copy(scenario = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.AddFirstMessage::class)
    private fun onAddFirstMessage() =
        updateForm { copy(firstMessages = firstMessages + "") }

    @UiIntentObserver(CharacterEditUiIntent.ChangeFirstMessage::class)
    private fun onChangeFirstMessage(intent: CharacterEditUiIntent.ChangeFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(CharacterEditUiIntent.DeleteFirstMessage::class)
    private fun onDeleteFirstMessage(intent: CharacterEditUiIntent.DeleteFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.removeAtOrSelf(intent.index).orSingleBlank()) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeExamplesOfDialogue::class)
    private fun onChangeExamplesOfDialogue(intent: CharacterEditUiIntent.ChangeExamplesOfDialogue) =
        updateForm { copy(examplesOfDialogue = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangePostHistoryInstructions::class)
    private fun onChangePostHistoryInstructions(intent: CharacterEditUiIntent.ChangePostHistoryInstructions) =
        updateForm { copy(postHistoryInstructions = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeSystemPrompt::class)
    private fun onChangeSystemPrompt(intent: CharacterEditUiIntent.ChangeSystemPrompt) =
        updateForm { copy(systemPrompt = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptPrompt::class)
    private fun onChangeDepthPromptPrompt(intent: CharacterEditUiIntent.ChangeDepthPromptPrompt) =
        updateForm { copy(depthPromptPrompt = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptDepth::class)
    private fun onChangeDepthPromptDepth(intent: CharacterEditUiIntent.ChangeDepthPromptDepth) =
        updateForm { copy(depthPromptDepth = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptRole::class)
    private fun onChangeDepthPromptRole(intent: CharacterEditUiIntent.ChangeDepthPromptRole) =
        updateForm { copy(depthPromptRole = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.AddAlternateGreeting::class)
    private fun onAddAlternateGreeting() =
        updateForm { copy(alternateGreetings = alternateGreetings + "") }

    @UiIntentObserver(CharacterEditUiIntent.ChangeAlternateGreeting::class)
    private fun onChangeAlternateGreeting(intent: CharacterEditUiIntent.ChangeAlternateGreeting) =
        updateForm { copy(alternateGreetings = alternateGreetings.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(CharacterEditUiIntent.DeleteAlternateGreeting::class)
    private fun onDeleteAlternateGreeting(intent: CharacterEditUiIntent.DeleteAlternateGreeting) =
        updateForm { copy(alternateGreetings = alternateGreetings.removeAtOrSelf(intent.index).orSingleBlank()) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeExtensionsJson::class)
    private fun onChangeExtensionsJson(intent: CharacterEditUiIntent.ChangeExtensionsJson) =
        updateForm { copy(extensionsJson = intent.value) }

    /** 先提交角色对新头像的引用，再清理不再使用的原头像文件。 */
    @UiIntentObserver(CharacterEditUiIntent.SaveCharacter::class)
    private suspend fun onSaveCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val character = uiState.form.toCharacterOrNullWithToast() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) {
            mCharacterRepository.saveCharacter(character)
            if (uiState.form.originalAvatar.isNotBlank() && uiState.form.originalAvatar != character.avatar) {
                mFileRepository.deleteFile(uiState.form.originalAvatar)
            }
        }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == CharacterEditMode.Create) R.string.character_created else R.string.character_saved
        ).tryEmit()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

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

    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacter::class)
    private suspend fun onConfirmDeleteCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.DeleteConfirm) return
        deleteCharacter()
    }

    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacterOnly::class)
    private suspend fun onConfirmDeleteCharacterOnly() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.DeleteWithLorebookConfirm) return
        deleteCharacter()
    }

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
            lorebookId?.let {
                mLorebookRepository.deleteLorebook(it)
            }
            mCharacterRepository.deleteCharacter(uiState.form.id)
            character?.avatar?.takeIf { it.isNotBlank() }?.let {
                mFileRepository.deleteFile(it)
            }
            pendingAvatar?.let {
                mFileRepository.deleteFile(it)
            }
        }
        AppViewEvent.PopupToastMessageByResId(R.string.character_deleted).tryEmit()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.ConfirmDiscardChanges::class)
    private suspend fun onConfirmDiscardChanges() {
        finishEditing()
    }

    @UiIntentObserver(CharacterEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
    }

    private fun updateForm(block: CharacterEditForm.() -> CharacterEditForm) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    private fun CharacterEditForm.toCharacterOrNullWithToast(): Character? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_name_empty).tryEmit()
            return null
        }
        return toCharacter()
    }

    private suspend fun CharacterEditForm.resolveAvatarImage() =
        avatar.takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.IO) { mFileRepository.loadBitmap(it)?.asImageBitmap() }
        }

    private suspend fun cleanupPendingAvatar() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.avatar.isBlank() || uiState.form.avatar == uiState.form.originalAvatar) return
        withContext(Dispatchers.IO) {
            mFileRepository.deleteFile(uiState.form.avatar)
        }
    }

    private suspend fun finishEditing() {
        cleanupPendingAvatar()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    private fun CharacterEditForm.ensureListInputs(): CharacterEditForm {
        return copy(
            tags = tags.orSingleBlank(),
            firstMessages = firstMessages.orSingleBlank(),
            alternateGreetings = alternateGreetings.orSingleBlank()
        )
    }

    private fun CharacterEditForm.withValidLorebookAssociation(
        availableLorebookIds: Set<Long>
    ): CharacterEditForm {
        if (characterLorebookId == 0L || characterLorebookId in availableLorebookIds) return this
        return copy(characterLorebookId = 0L)
    }

}
