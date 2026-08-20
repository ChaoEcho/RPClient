package me.kafuuneko.rpclient.feature.story.create

import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.story.create.model.StoryCreateCharacterItem
import me.kafuuneko.rpclient.feature.story.create.model.StoryCreateForm
import me.kafuuneko.rpclient.feature.story.create.model.StoryCreateLorebookEntryItem
import me.kafuuneko.rpclient.feature.story.create.model.StoryCreateLorebookGroupItem
import me.kafuuneko.rpclient.feature.story.create.presentation.StoryCreateLoadState
import me.kafuuneko.rpclient.feature.story.create.presentation.StoryCreateUiIntent
import me.kafuuneko.rpclient.feature.story.create.presentation.StoryCreateUiState
import me.kafuuneko.rpclient.feature.story.editor.StoryEditorActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterSelection
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.libs.utils.toggleAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 新建 Story 页状态持有者，负责初始角色、世界书配置和原子创建。 */
class StoryCreateViewModel : CoreViewModelWithEvent<StoryCreateUiIntent, StoryCreateUiState>(
    StoryCreateUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mStoryRepository by inject<StoryRepository>()

    @UiIntentObserver(StoryCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<StoryCreateUiState.None>()) return
        StoryCreateUiState.Normal().setup()
        try {
            val data = withContext(Dispatchers.IO) { loadOptions() }
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            current.copy(
                loadState = StoryCreateLoadState.Ready,
                characters = data.characters,
                lorebookGroups = data.lorebookGroups,
                visibleLorebookGroups = data.lorebookGroups
            ).setup()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            current.copy(loadState = StoryCreateLoadState.Ready).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_settings_load_failed).tryEmit()
        }
    }

    @UiIntentObserver(StoryCreateUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<StoryCreateUiState.Normal>() ?: return
        if (uiState.loadState == StoryCreateLoadState.Creating) return
        StoryCreateUiState.finished(uiState).setup()
    }

    @UiIntentObserver(StoryCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: StoryCreateUiIntent.ChangeTitle) {
        updateReadyForm { copy(title = intent.value) }
    }

    @UiIntentObserver(StoryCreateUiIntent.ToggleCharacter::class)
    private fun onToggleCharacter(intent: StoryCreateUiIntent.ToggleCharacter) {
        val uiState = readyState() ?: return
        val character = uiState.characters.firstOrNull { it.id == intent.characterId } ?: return
        val selecting = character.id !in uiState.form.selectedCharacterIds
        val linkedEntryIds = character.linkedLorebookId
            ?.let { lorebookId ->
                uiState.lorebookGroups
                    .firstOrNull { it.lorebookId == lorebookId }
                    ?.entries
                    ?.mapTo(mutableSetOf()) { it.id }
            }
            .orEmpty()
        updateReadyForm {
            copy(
                selectedCharacterIds = selectedCharacterIds.toggle(character.id),
                selectedLorebookEntryIds = if (selecting) {
                    selectedLorebookEntryIds + linkedEntryIds
                } else {
                    selectedLorebookEntryIds
                }
            )
        }
    }

    @UiIntentObserver(StoryCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: StoryCreateUiIntent.ChangeLorebookQuery) {
        val uiState = readyState() ?: return
        uiState.copy(
            lorebookQuery = intent.value,
            visibleLorebookGroups = uiState.lorebookGroups.filterForQuery(intent.value)
        ).setup()
    }

    @UiIntentObserver(StoryCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: StoryCreateUiIntent.ToggleLorebook) {
        val uiState = readyState() ?: return
        val group = uiState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?: return
        val entryIds = group.entries.mapTo(mutableSetOf()) { it.id }
        if (entryIds.isEmpty()) return
        updateReadyForm {
            copy(selectedLorebookEntryIds = selectedLorebookEntryIds.toggleAll(entryIds))
        }
    }

    @UiIntentObserver(StoryCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(intent: StoryCreateUiIntent.ToggleLorebookEntry) {
        val uiState = readyState() ?: return
        if (uiState.lorebookGroups.none { group -> group.entries.any { it.id == intent.entryId } }) {
            return
        }
        updateReadyForm {
            copy(selectedLorebookEntryIds = selectedLorebookEntryIds.toggle(intent.entryId))
        }
    }

    /** Story 与初始引用必须在同一事务中创建，避免配置校验失败后留下空 Story。 */
    @UiIntentObserver(StoryCreateUiIntent.CreateStory::class)
    private suspend fun onCreateStory() {
        val uiState = readyState() ?: return
        val title = uiState.form.title.trim()
        if (title.isEmpty()) {
            AppViewEvent.PopupToastMessageByResId(R.string.story_title_required).tryEmit()
            return
        }
        uiState.copy(loadState = StoryCreateLoadState.Creating).setup()
        try {
            val storyId = withContext(Dispatchers.IO) {
                mStoryRepository.createStoryWithConfiguration(
                    title = title,
                    lorebookEntryIds = uiState.form.selectedLorebookEntryIds.sorted(),
                    characterSelections = uiState.characters
                        .filter { it.id in uiState.form.selectedCharacterIds }
                        .map { character ->
                            StoryCharacterSelection(
                                characterId = character.id,
                                activationMode = StoryCharacter.ACTIVATION_AUTO,
                                activationKeys = emptyList()
                            )
                        }
                )
            }
            AppViewEvent.StartActivity(
                activity = StoryEditorActivity::class.java,
                extras = Bundle().apply {
                    putLong(StoryEditorActivity.EXTRA_STORY_ID, storyId)
                }
            ).emitAndAwait()
            StoryCreateUiState.finished(uiStateFlow.value).setup()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            current.copy(loadState = StoryCreateLoadState.Ready).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_save_failed).tryEmit()
        }
    }

    private suspend fun loadOptions(): StoryCreateOptions {
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val lorebookNames = lorebooks.associate { it.id to it.name }
        val groups = lorebooks.map { lorebook ->
            StoryCreateLorebookGroupItem(
                lorebookId = lorebook.id,
                lorebookName = lorebook.name,
                entries = mLorebookRepository.getEntriesByLorebookId(lorebook.id)
                    .sortedBy { it.order }
                    .map { entry ->
                        StoryCreateLorebookEntryItem(
                            id = entry.id,
                            lorebookName = lorebook.name,
                            name = entry.name,
                            content = entry.content,
                            keywords = entry.getKeywordList(),
                            constant = entry.constant,
                            order = entry.order,
                            depth = entry.depth
                        )
                    }
            )
        }.filter { it.entries.isNotEmpty() }
        val characters = mCharacterRepository.getAllCharacters().map { character ->
            StoryCreateCharacterItem(
                id = character.id,
                name = character.name,
                description = character.description,
                tags = character.getCharacterTagList(),
                linkedLorebookId = character.characterLorebookId.takeIf { it > 0L },
                linkedLorebookName = lorebookNames[character.characterLorebookId]
            )
        }
        return StoryCreateOptions(characters, groups)
    }

    private fun readyState(): StoryCreateUiState.Normal? {
        return getOrNull<StoryCreateUiState.Normal>()
            ?.takeIf { it.loadState == StoryCreateLoadState.Ready }
    }

    private fun updateReadyForm(update: StoryCreateForm.() -> StoryCreateForm) {
        val uiState = readyState() ?: return
        uiState.copy(form = uiState.form.update()).setup()
    }

    private fun List<StoryCreateLorebookGroupItem>.filterForQuery(
        query: String
    ): List<StoryCreateLorebookGroupItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return this
        return mapNotNull { group ->
            val groupMatches = group.lorebookName.contains(normalizedQuery, ignoreCase = true)
            val entries = group.entries.filter { entry ->
                entry.name.contains(normalizedQuery, ignoreCase = true) ||
                    entry.content.contains(normalizedQuery, ignoreCase = true) ||
                    entry.keywords.any { it.contains(normalizedQuery, ignoreCase = true) }
            }
            when {
                groupMatches -> group
                entries.isNotEmpty() -> group.copy(entries = entries)
                else -> null
            }
        }
    }

    private data class StoryCreateOptions(
        val characters: List<StoryCreateCharacterItem>,
        val lorebookGroups: List<StoryCreateLorebookGroupItem>
    )
}
