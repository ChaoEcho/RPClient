package me.kafuuneko.rpclient.feature.imageproviderlist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImageProviderListUiIntent
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImageProviderListUiState
import me.kafuuneko.rpclient.feature.imageproviderlist.ui.ImageProviderListLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 图片服务列表页面宿主。 */
class ImageProviderListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ImageProviderListViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is ImageProviderListUiState.Finished) finish()
        }

        ImageProviderListLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(ImageProviderListUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(ImageProviderListUiIntent.Resume)
    }
}
