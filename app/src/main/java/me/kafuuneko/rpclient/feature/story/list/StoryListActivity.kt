package me.kafuuneko.rpclient.feature.story.list

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListUiIntent
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListUiState
import me.kafuuneko.rpclient.feature.story.list.ui.StoryListLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** Story 列表页宿主。 */
class StoryListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<StoryListViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is StoryListUiState.Finished) finish()
        }

        StoryListLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(StoryListUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(StoryListUiIntent.Resume)
    }
}
