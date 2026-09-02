package me.kafuuneko.rpclient.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.backup.presentation.BackupDialogState
import me.kafuuneko.rpclient.feature.backup.presentation.BackupOperationKind
import me.kafuuneko.rpclient.feature.backup.presentation.BackupOperationState
import me.kafuuneko.rpclient.feature.backup.presentation.BackupUiIntent
import me.kafuuneko.rpclient.feature.backup.presentation.BackupUiState
import me.kafuuneko.rpclient.feature.backup.presentation.BackupViewEvent
import me.kafuuneko.rpclient.libs.debug.AppLogger
import me.kafuuneko.rpclient.libs.backup.BackupContract
import me.kafuuneko.rpclient.libs.backup.BackupException
import me.kafuuneko.rpclient.libs.backup.BackupOperationPhase
import me.kafuuneko.rpclient.libs.backup.BackupRepository
import me.kafuuneko.rpclient.libs.backup.BackupSettingsModel
import me.kafuuneko.rpclient.libs.backup.LocalSecretStore
import me.kafuuneko.rpclient.libs.backup.RemoteBackupItem
import me.kafuuneko.rpclient.libs.backup.ValidatedBackup
import me.kafuuneko.rpclient.libs.backup.WebDavClient
import me.kafuuneko.rpclient.libs.backup.WebDavConfig
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 协调本地完整备份、Replace Restore 与 WebDAV 文件存储。 */
class BackupViewModel : CoreViewModelWithEvent<BackupUiIntent, BackupUiState>(
    BackupUiState.None
), KoinComponent {
    private val mContext by inject<Context>()
    private val mRepository by inject<BackupRepository>()
    private val mSecretStore by inject<LocalSecretStore>()
    private val mWebDavClient by inject<WebDavClient>()

    private var mOperationJob: Job? = null
    private var mPendingLocalBackupPassword: CharArray? = null
    private var mPendingRestoreSource: Uri? = null
    private var mPendingValidatedBackup: ValidatedBackup? = null
    private var mPendingRemoteItem: RemoteBackupItem? = null

    @UiIntentObserver(BackupUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<BackupUiState.None>()) return
        val initialState = BackupUiState.Normal(
            webDavBaseUrl = BackupSettingsModel.webDavBaseUrl,
            webDavUsername = BackupSettingsModel.webDavUsername,
            webDavPassword = mSecretStore.getWebDavPassword(),
            webDavRemotePath = BackupSettingsModel.webDavRemotePath,
            lastSuccessfulBackupAt = BackupSettingsModel.lastSuccessfulBackupAt
        )
        initialState.setup()
    }

    @UiIntentObserver(BackupUiIntent.Back::class)
    private fun onBack() {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        val pending = mPendingValidatedBackup
        if (pending == null) {
            BackupUiState.Finished(state).setup()
            return
        }
        mPendingValidatedBackup = null
        mOperationJob = viewModelScope.launch(Dispatchers.IO) {
            mRepository.discard(pending)
            BackupUiState.Finished(state).setup()
        }
    }

    @UiIntentObserver(BackupUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        val pending = mPendingValidatedBackup
        clearPendingDialogInputs()
        state.copy(dialogState = BackupDialogState.None).setup()
        if (pending != null) {
            mPendingValidatedBackup = null
            viewModelScope.launch(Dispatchers.IO) { mRepository.discard(pending) }
        }
    }

    @UiIntentObserver(BackupUiIntent.CreateLocalClick::class)
    private fun onCreateLocalClick() {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        state.copy(dialogState = BackupDialogState.CreateLocal).setup()
    }

    @UiIntentObserver(BackupUiIntent.SubmitLocalBackupPassword::class)
    private fun onSubmitLocalBackupPassword(intent: BackupUiIntent.SubmitLocalBackupPassword) {
        val state = normalOrNull() ?: return
        if (state.operation != null || state.dialogState !is BackupDialogState.CreateLocal) return
        val password = resolveConfirmedBackupPassword(intent.password, intent.confirmation) ?: return
        mPendingLocalBackupPassword?.fill('\u0000')
        mPendingLocalBackupPassword = password
        state.copy(dialogState = BackupDialogState.None).setup()
        BackupViewEvent.CreateLocalBackupDocument(defaultBackupFileName()).tryEmit()
    }

    @UiIntentObserver(BackupUiIntent.LocalBackupTargetSelected::class)
    private fun onLocalBackupTargetSelected(intent: BackupUiIntent.LocalBackupTargetSelected) {
        val password = mPendingLocalBackupPassword ?: return
        mPendingLocalBackupPassword = null
        val uri = intent.uri
        if (uri == null || normalOrNull()?.operation != null || mOperationJob?.isActive == true) {
            password.fill('\u0000')
            return
        }
        val started = runOperation(BackupOperationKind.CreateLocal) {
            try {
                mRepository.createLocalBackup(uri, password, ::updatePhase)
                refreshPersistedMetadata()
                AppViewEvent.PopupToastMessageByResId(R.string.backup_create_success).tryEmit()
            } finally {
                password.fill('\u0000')
            }
        }
        if (!started) password.fill('\u0000')
    }

    @UiIntentObserver(BackupUiIntent.RestoreLocalClick::class)
    private fun onRestoreLocalClick() {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        BackupViewEvent.OpenLocalBackupDocument.tryEmit()
    }

    @UiIntentObserver(BackupUiIntent.LocalRestoreSourceSelected::class)
    private fun onLocalRestoreSourceSelected(intent: BackupUiIntent.LocalRestoreSourceSelected) {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        mPendingRestoreSource = intent.uri
        if (intent.uri != null) {
            state.copy(dialogState = BackupDialogState.EnterLocalRestorePassword).setup()
        }
    }

    @UiIntentObserver(BackupUiIntent.SubmitLocalRestorePassword::class)
    private fun onSubmitLocalRestorePassword(intent: BackupUiIntent.SubmitLocalRestorePassword) {
        val state = normalOrNull() ?: return
        val source = mPendingRestoreSource ?: return
        if (state.operation != null || state.dialogState !is BackupDialogState.EnterLocalRestorePassword) return
        val password = resolveBackupPassword(intent.password) ?: return
        mPendingRestoreSource = null
        state.copy(dialogState = BackupDialogState.None).setup()
        val started = runOperation(BackupOperationKind.ValidateLocal) {
            try {
                val backup = mRepository.validateLocalBackup(source, password, ::updatePhase)
                showRestoreConfirmation(backup)
            } finally {
                password.fill('\u0000')
            }
        }
        if (!started) password.fill('\u0000')
    }

    @UiIntentObserver(BackupUiIntent.ConfirmRestore::class)
    private fun onConfirmRestore() {
        val state = normalOrNull() ?: return
        val backup = mPendingValidatedBackup ?: return
        if (state.operation != null || state.dialogState !is BackupDialogState.ConfirmRestore) return
        mPendingValidatedBackup = null
        state.copy(dialogState = BackupDialogState.None).setup()
        runOperation(BackupOperationKind.Restore) {
            mRepository.restore(backup, ::updatePhase)
            AppViewEvent.PopupToastMessageByResId(R.string.backup_restore_success).tryEmit()
            BackupUiState.Finished(uiStateFlow.value).setup()
        }
    }

    @UiIntentObserver(BackupUiIntent.ChangeWebDavBaseUrl::class)
    private fun onChangeWebDavBaseUrl(intent: BackupUiIntent.ChangeWebDavBaseUrl) {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        state.copy(webDavBaseUrl = intent.value).setup()
    }

    @UiIntentObserver(BackupUiIntent.ChangeWebDavUsername::class)
    private fun onChangeWebDavUsername(intent: BackupUiIntent.ChangeWebDavUsername) {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        state.copy(webDavUsername = intent.value).setup()
    }

    @UiIntentObserver(BackupUiIntent.ChangeWebDavPassword::class)
    private fun onChangeWebDavPassword(intent: BackupUiIntent.ChangeWebDavPassword) {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        state.copy(webDavPassword = intent.value).setup()
    }

    @UiIntentObserver(BackupUiIntent.ChangeWebDavRemotePath::class)
    private fun onChangeWebDavRemotePath(intent: BackupUiIntent.ChangeWebDavRemotePath) {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        state.copy(webDavRemotePath = intent.value).setup()
    }

    @UiIntentObserver(BackupUiIntent.SaveWebDavConfig::class)
    private fun onSaveWebDavConfig() {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        val trimmedBaseUrl = state.webDavBaseUrl.trim()
        val trimmedUsername = state.webDavUsername.trim()
        val trimmedRemotePath = state.webDavRemotePath.trim()

        BackupSettingsModel.webDavBaseUrl = trimmedBaseUrl
        BackupSettingsModel.webDavUsername = trimmedUsername
        BackupSettingsModel.webDavRemotePath = trimmedRemotePath
        mSecretStore.setWebDavPassword(state.webDavPassword)
        AppLogger.i("Backup", "WebDAV configuration saved: $trimmedBaseUrl")

        state.copy(
            webDavBaseUrl = trimmedBaseUrl,
            webDavUsername = trimmedUsername,
            webDavRemotePath = trimmedRemotePath
        ).setup()
        AppViewEvent.PopupToastMessageByResId(R.string.backup_webdav_config_saved).tryEmit()
    }

    @UiIntentObserver(BackupUiIntent.TestWebDav::class)
    private fun onTestWebDav() {
        val state = normalOrNull() ?: return
        if (state.operation != null || mOperationJob?.isActive == true) return
        val password = currentWebDavPasswordOrNull() ?: return
        runOperation(BackupOperationKind.TestWebDav) {
            val config = currentWebDavConfig()
            mWebDavClient.ensureCollection(config, password)
            mWebDavClient.testConnection(config, password)
            AppViewEvent.PopupToastMessageByResId(R.string.backup_webdav_test_success).tryEmit()
        }
    }

    @UiIntentObserver(BackupUiIntent.RefreshWebDav::class)
    private fun onRefreshWebDav() {
        val state = normalOrNull() ?: return
        if (state.operation != null || mOperationJob?.isActive == true) return
        val password = currentWebDavPasswordOrNull() ?: return
        runOperation(BackupOperationKind.RefreshWebDav) {
            refreshRemoteBackups(currentWebDavConfig(), password)
        }
    }

    @UiIntentObserver(BackupUiIntent.UploadWebDavClick::class)
    private fun onUploadWebDavClick() {
        showDialog(BackupDialogState.UploadWebDav)
    }

    @UiIntentObserver(BackupUiIntent.SubmitWebDavUpload::class)
    private fun onSubmitWebDavUpload(intent: BackupUiIntent.SubmitWebDavUpload) {
        val state = normalOrNull() ?: return
        if (state.operation != null || state.dialogState !is BackupDialogState.UploadWebDav) return
        val webDavPassword = currentWebDavPasswordOrNull() ?: return
        val backupPassword = resolveConfirmedBackupPassword(
            intent.backupPassword,
            intent.backupPasswordConfirmation
        ) ?: return
        state.copy(dialogState = BackupDialogState.None).setup()
        val started = runOperation(BackupOperationKind.UploadWebDav) {
            uploadRemoteBackup(webDavPassword, backupPassword)
            AppViewEvent.PopupToastMessageByResId(R.string.backup_webdav_upload_success).tryEmit()
        }
        if (!started) backupPassword.fill('\u0000')
    }

    @UiIntentObserver(BackupUiIntent.RestoreWebDavClick::class)
    private fun onRestoreWebDavClick(intent: BackupUiIntent.RestoreWebDavClick) {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        mPendingRemoteItem = intent.item
        state.copy(dialogState = BackupDialogState.RestoreWebDav(intent.item.name)).setup()
    }

    @UiIntentObserver(BackupUiIntent.SubmitWebDavRestore::class)
    private fun onSubmitWebDavRestore(intent: BackupUiIntent.SubmitWebDavRestore) {
        val state = normalOrNull() ?: return
        val item = mPendingRemoteItem ?: return
        if (state.operation != null || state.dialogState !is BackupDialogState.RestoreWebDav) return
        val webDavPassword = currentWebDavPasswordOrNull() ?: return
        val backupPassword = resolveBackupPassword(intent.backupPassword) ?: return
        mPendingRemoteItem = null
        state.copy(dialogState = BackupDialogState.None).setup()
        val started = runOperation(BackupOperationKind.DownloadWebDav) {
            downloadAndValidateRemote(item, webDavPassword, backupPassword)
        }
        if (!started) backupPassword.fill('\u0000')
    }

    @UiIntentObserver(BackupUiIntent.DeleteWebDavClick::class)
    private fun onDeleteWebDavClick(intent: BackupUiIntent.DeleteWebDavClick) {
        val state = normalOrNull() ?: return
        if (state.operation != null) return
        mPendingRemoteItem = intent.item
        state.copy(dialogState = BackupDialogState.DeleteWebDav(intent.item.name)).setup()
    }

    @UiIntentObserver(BackupUiIntent.ConfirmWebDavDelete::class)
    private fun onConfirmWebDavDelete() {
        val state = normalOrNull() ?: return
        val item = mPendingRemoteItem ?: return
        if (state.operation != null || state.dialogState !is BackupDialogState.DeleteWebDav) return
        val password = currentWebDavPasswordOrNull() ?: return
        mPendingRemoteItem = null
        state.copy(dialogState = BackupDialogState.None).setup()
        runOperation(BackupOperationKind.DeleteWebDav) {
            val config = currentWebDavConfig()
            mWebDavClient.delete(config, password, item)
            refreshRemoteBackups(config, password)
            AppViewEvent.PopupToastMessageByResId(R.string.backup_webdav_delete_success).tryEmit()
        }
    }

    /** 上传密文临时文件，并在任何结果下清理密码和临时文件。 */
    private suspend fun uploadRemoteBackup(webDavPassword: String, backupPassword: CharArray) {
        var encryptedFile: File? = null
        try {
            val config = currentWebDavConfig()
            mWebDavClient.ensureCollection(config, webDavPassword)
            encryptedFile = mRepository.createEncryptedBackupFile(backupPassword, ::updatePhase)
            mWebDavClient.upload(config, webDavPassword, defaultBackupFileName(), encryptedFile)
            BackupSettingsModel.lastSuccessfulBackupAt = System.currentTimeMillis()
            refreshPersistedMetadata()
            runCatching { refreshRemoteBackups(config, webDavPassword) }
        } finally {
            encryptedFile?.delete()
            backupPassword.fill('\u0000')
        }
    }

    /** 下载远端密文后复用本地校验管线，明文 staging 只进入待确认状态。 */
    private suspend fun downloadAndValidateRemote(
        item: RemoteBackupItem,
        webDavPassword: String,
        backupPassword: CharArray
    ) {
        val encryptedFile = File.createTempFile(
            "backup_download_",
            BackupContract.FILE_EXTENSION,
            mContext.cacheDir
        )
        try {
            mWebDavClient.download(currentWebDavConfig(), webDavPassword, item, encryptedFile)
            val backup = mRepository.validateEncryptedBackup(encryptedFile, backupPassword, ::updatePhase)
            showRestoreConfirmation(backup)
        } finally {
            encryptedFile.delete()
            backupPassword.fill('\u0000')
        }
    }

    /** 启动唯一长任务，并把异常映射为不含底层详情的用户消息。 */
    private fun runOperation(kind: BackupOperationKind, block: suspend () -> Unit): Boolean {
        val state = normalOrNull() ?: return false
        if (state.operation != null || mOperationJob?.isActive == true) return false
        state.copy(operation = BackupOperationState(kind)).setup()
        mOperationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                AppLogger.i("Backup", "Operation started: ${kind.name}")
                block()
                AppLogger.i("Backup", "Operation completed: ${kind.name}")
            } catch (cancelled: CancellationException) {
                AppLogger.w("Backup", "Operation cancelled: ${kind.name}")
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e("Backup", "Operation failed: ${kind.name} - ${error.message}", error)
                showError(error)
            } finally {
                // 先释放任务所有权，再发布空闲状态，避免确认操作抢在 Job 清理前进入。
                mOperationJob = null
                val current = normalOrNull()
                if (current?.operation != null) current.copy(operation = null).setup()
            }
        }
        return true
    }

    private fun updatePhase(phase: BackupOperationPhase) {
        val state = normalOrNull() ?: return
        val operation = state.operation ?: return
        state.copy(operation = operation.copy(phase = phase)).setup()
    }

    private fun showRestoreConfirmation(backup: ValidatedBackup) {
        val state = normalOrNull() ?: run {
            viewModelScope.launch(Dispatchers.IO) { mRepository.discard(backup) }
            return
        }
        mPendingValidatedBackup = backup
        state.copy(
            dialogState = BackupDialogState.ConfirmRestore(
                appVersionName = backup.manifest.appVersionName,
                createdAt = backup.manifest.createdAt,
                recordCount = backup.manifest.tableCounts.values.sum(),
                fileCount = backup.manifest.fileCount
            )
        ).setup()
    }

    private fun currentWebDavConfig(): WebDavConfig {
        val state = normalOrNull() ?: throw BackupException.WebDavUnavailable()
        return WebDavConfig(
            baseUrl = state.webDavBaseUrl,
            username = state.webDavUsername,
            remotePath = state.webDavRemotePath
        )
    }

    private fun refreshRemoteBackups(config: WebDavConfig, password: String) {
        val items = mWebDavClient.listBackups(config, password)
        AppLogger.i("Backup", "Loaded ${items.size} remote backups from WebDAV")
        normalOrNull()?.copy(remoteBackups = items)?.setup()
    }

    private fun refreshPersistedMetadata() {
        normalOrNull()?.copy(
            lastSuccessfulBackupAt = BackupSettingsModel.lastSuccessfulBackupAt
        )?.setup()
    }

    private fun resolveConfirmedBackupPassword(password: String, confirmation: String): CharArray? {
        if (password != confirmation) {
            AppViewEvent.PopupToastMessageByResId(R.string.backup_password_mismatch).tryEmit()
            return null
        }
        return resolveBackupPassword(password)
    }

    private fun resolveBackupPassword(typed: String): CharArray? {
        if (typed.isEmpty()) {
            AppViewEvent.PopupToastMessageByResId(R.string.backup_password_required).tryEmit()
            return null
        }
        return typed.toCharArray()
    }

    private fun currentWebDavPasswordOrNull(): String? {
        val password = normalOrNull()?.webDavPassword.orEmpty()
        if (password.isEmpty()) {
            AppViewEvent.PopupToastMessageByResId(R.string.backup_webdav_password_required).tryEmit()
            return null
        }
        return password
    }

    private fun showError(error: Exception) {
        val message = when (error) {
            is BackupException.UnsupportedFormat -> R.string.backup_error_unsupported_format
            is BackupException.UnsupportedVersion -> R.string.backup_error_unsupported_version
            is BackupException.WrongPasswordOrCorrupted -> R.string.backup_error_wrong_password
            is BackupException.MissingAsset -> R.string.backup_error_missing_asset
            is BackupException.StorageInsufficient -> R.string.backup_error_storage_insufficient
            is BackupException.WebDavAuthenticationFailed -> R.string.backup_error_webdav_auth
            is BackupException.WebDavInvalidResponse -> R.string.backup_error_webdav_invalid_response
            is BackupException.WebDavUnavailable -> R.string.backup_error_webdav_unavailable
            is BackupException.RestoreValidationFailed -> R.string.backup_error_validation
            else -> R.string.backup_error_generic
        }
        AppViewEvent.PopupToastMessageByResId(message).tryEmit()
    }

    private fun showDialog(dialog: BackupDialogState) {
        val state = normalOrNull() ?: return
        if (state.operation == null) state.copy(dialogState = dialog).setup()
    }

    private fun clearPendingDialogInputs() {
        mPendingRestoreSource = null
        mPendingRemoteItem = null
    }

    private fun normalOrNull(): BackupUiState.Normal? = getOrNull()

    override fun onCleared() {
        mPendingLocalBackupPassword?.fill('\u0000')
        mPendingLocalBackupPassword = null
        mPendingValidatedBackup?.stagingDirectory?.deleteRecursively()
        mPendingValidatedBackup = null
        super.onCleared()
    }

    private fun defaultBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "RPClient-Backup-$timestamp${BackupContract.FILE_EXTENSION}"
    }
}
