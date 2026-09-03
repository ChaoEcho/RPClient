package me.kafuuneko.rpclient.feature.imageprovideredit

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditUiIntent
import me.kafuuneko.rpclient.feature.imageprovideredit.presentation.ImageProviderEditUiState
import me.kafuuneko.rpclient.feature.imageprovideredit.ui.ImageProviderEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 图片服务创建与编辑页面宿主。 */
class ImageProviderEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ImageProviderEditViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is ImageProviderEditUiState.Finished) finish()
        }

        ImageProviderEditLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(ImageProviderEditUiIntent.Init(providerId))
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "extra_provider_id"
    }
}
