package me.kafuuneko.rpclient.feature.story.create.presentation

import me.kafuuneko.rpclient.feature.story.create.model.StoryCreateCharacterItem
import me.kafuuneko.rpclient.feature.story.create.model.StoryCreateForm
import me.kafuuneko.rpclient.feature.story.create.model.StoryCreateLorebookGroupItem

/** 新建 Story 页面状态树。 */
sealed class StoryCreateUiState {
    data object None : StoryCreateUiState()

    data class Normal(
        val loadState: StoryCreateLoadState = StoryCreateLoadState.Loading,
        val form: StoryCreateForm = StoryCreateForm(),
        val characters: List<StoryCreateCharacterItem> = emptyList(),
        val lorebookQuery: String = "",
        val lorebookGroups: List<StoryCreateLorebookGroupItem> = emptyList(),
        val visibleLorebookGroups: List<StoryCreateLorebookGroupItem> = lorebookGroups
    ) : StoryCreateUiState()

    data class Finished(val previous: StoryCreateUiState) : StoryCreateUiState()

    companion object {
        fun finished(previous: StoryCreateUiState): StoryCreateUiState {
            return if (previous is Finished) previous else Finished(previous)
        }
    }
}

/** 新建 Story 页面的加载和提交状态。 */
sealed class StoryCreateLoadState {
    data object Loading : StoryCreateLoadState()
    data object Ready : StoryCreateLoadState()
    data object Creating : StoryCreateLoadState()
}
