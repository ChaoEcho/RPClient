package me.kafuuneko.rpclient.feature.backup.presentation

import android.net.Uri
import me.kafuuneko.rpclient.libs.backup.RemoteBackupItem

/** 完整备份页面可接收的用户操作与系统文件选择结果。 */
sealed class BackupUiIntent {
    data object Init : BackupUiIntent()
    data object Back : BackupUiIntent()
    data object DismissDialog : BackupUiIntent()

    data object CreateLocalClick : BackupUiIntent()
    data class SubmitLocalBackupPassword(
        val password: String,
        val confirmation: String
    ) : BackupUiIntent()
    data class LocalBackupTargetSelected(val uri: Uri?) : BackupUiIntent()

    data object RestoreLocalClick : BackupUiIntent()
    data class LocalRestoreSourceSelected(val uri: Uri?) : BackupUiIntent()
    data class SubmitLocalRestorePassword(
        val password: String
    ) : BackupUiIntent()
    data object ConfirmRestore : BackupUiIntent()

    data class ChangeWebDavBaseUrl(val value: String) : BackupUiIntent()
    data class ChangeWebDavUsername(val value: String) : BackupUiIntent()
    data class ChangeWebDavRemotePath(val value: String) : BackupUiIntent()
    data class SaveWebDavConfig(val password: String) : BackupUiIntent()
    data object ClearSavedWebDavPassword : BackupUiIntent()
    data class TestWebDav(val password: String) : BackupUiIntent()
    data class RefreshWebDav(val password: String) : BackupUiIntent()

    data object UploadWebDavClick : BackupUiIntent()
    data class SubmitWebDavUpload(
        val webDavPassword: String,
        val backupPassword: String,
        val backupPasswordConfirmation: String
    ) : BackupUiIntent()

    data class RestoreWebDavClick(val item: RemoteBackupItem) : BackupUiIntent()
    data class SubmitWebDavRestore(
        val webDavPassword: String,
        val backupPassword: String
    ) : BackupUiIntent()

    data class DeleteWebDavClick(val item: RemoteBackupItem) : BackupUiIntent()
    data class ConfirmWebDavDelete(
        val webDavPassword: String
    ) : BackupUiIntent()
}
