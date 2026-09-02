package me.kafuuneko.rpclient.feature.backup

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.backup.presentation.BackupUiIntent
import me.kafuuneko.rpclient.feature.backup.presentation.BackupUiState
import me.kafuuneko.rpclient.feature.backup.presentation.BackupViewEvent
import me.kafuuneko.rpclient.feature.backup.ui.BackupLayout
import me.kafuuneko.rpclient.libs.backup.BackupContract
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.GetContentWithMimeTypes
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 完整备份页面宿主，桥接系统文档创建与选择器。 */
class BackupActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<BackupViewModel>()

    private val mCreateBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(BackupContract.MIME_TYPE)
    ) { uri ->
        mViewModel.emit(BackupUiIntent.LocalBackupTargetSelected(uri))
    }

    private val mOpenBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        mViewModel.emit(BackupUiIntent.LocalRestoreSourceSelected(uri))
    }

    /** 对话文件只在 ViewModel 完成解析并由用户选择角色后才会写入数据库。 */
    private val mChatImportLauncher = registerForActivityResult(
        GetContentWithMimeTypes()
    ) { uri ->
        uri ?: return@registerForActivityResult
        mViewModel.emit(BackupUiIntent.ImportChatResult(uri))
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is BackupUiState.Finished) finish()
        }

        BackupLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(BackupUiIntent.Init)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is BackupViewEvent.CreateLocalBackupDocument -> {
                mCreateBackupLauncher.launch(viewEvent.fileName)
            }

            BackupViewEvent.OpenLocalBackupDocument -> {
                mOpenBackupLauncher.launch(
                    arrayOf(BackupContract.MIME_TYPE, "application/zip", "*/*")
                )
            }

            BackupViewEvent.OpenChatArchiveDocument -> {
                mChatImportLauncher.launch(
                    arrayOf(
                        "application/x-ndjson",
                        "application/json",
                        "text/plain",
                        "application/octet-stream"
                    )
                )
            }

            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
