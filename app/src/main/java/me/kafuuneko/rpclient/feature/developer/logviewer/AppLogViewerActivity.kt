package me.kafuuneko.rpclient.feature.developer.logviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.developer.logviewer.presentation.AppLogViewerUiIntent
import me.kafuuneko.rpclient.feature.developer.logviewer.presentation.AppLogViewerUiState
import me.kafuuneko.rpclient.feature.developer.logviewer.presentation.AppLogViewerViewEvent
import me.kafuuneko.rpclient.feature.developer.logviewer.ui.AppLogViewerLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

class AppLogViewerActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<AppLogViewerViewModel>()

    /** 导出目标由系统文档选择器创建，Activity 只回传 URI。 */
    private val mLogExporterLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { mViewModel.emit(AppLogViewerUiIntent.SaveLogsResult(it)) }
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is AppLogViewerUiState.Finished) finish()
        }

        AppLogViewerLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(AppLogViewerUiIntent.Init)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is AppLogViewerViewEvent.CopyText -> copyLogs(viewEvent.text)
            is AppLogViewerViewEvent.OpenLogExporter ->
                mLogExporterLauncher.launch(viewEvent.fileName)
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    private fun copyLogs(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_logs), text))
        Toast.makeText(this, R.string.logs_exported, Toast.LENGTH_SHORT).show()
    }
}
