package me.kafuuneko.rpclient.feature.main

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.presentation.MainViewEvent
import me.kafuuneko.rpclient.feature.main.ui.MainLayout
import me.kafuuneko.rpclient.feature.imagecrop.ImageCropActivity
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 应用主页面宿主，承载首页与全局设置。 */
class MainActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<MainViewModel>()

    /** 用户头像选择结果只用于打开裁剪页，Activity 不直接持久化 URI 或位图。 */
    private val mUserAvatarPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { mImageCropLauncher.launch(ImageCropActivity.createIntent(this, it)) }
    }

    /** 裁剪页完成文件保存后，将方形头像 UUID 交回设置状态。 */
    private val mImageCropLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        ImageCropActivity.getResultFileUuid(result.data)?.let {
            mViewModel.emit(MainUiIntent.UserAvatarCropped(it))
        }
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is MainUiState.Finished) finish()
        }

        MainLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(MainUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(MainUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        when (viewEvent) {
            MainViewEvent.OpenUserAvatarPicker -> mUserAvatarPickerLauncher.launch("image/*")
        }
    }
}
