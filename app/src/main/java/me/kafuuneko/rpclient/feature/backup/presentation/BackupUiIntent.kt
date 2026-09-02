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
    data class ChangeWebDavPassword(val value: String) : BackupUiIntent()
    data class ChangeWebDavRemotePath(val value: String) : BackupUiIntent()
    data object SaveWebDavConfig : BackupUiIntent()
    data object TestWebDav : BackupUiIntent()
    data object RefreshWebDav : BackupUiIntent()

    data object UploadWebDavClick : BackupUiIntent()
    data class SubmitWebDavUpload(
        val backupPassword: String,
        val backupPasswordConfirmation: String
    ) : BackupUiIntent()

    data class RestoreWebDavClick(val item: RemoteBackupItem) : BackupUiIntent()
    data class SubmitWebDavRestore(
        val backupPassword: String
    ) : BackupUiIntent()

    data class DeleteWebDavClick(val item: RemoteBackupItem) : BackupUiIntent()
    data object ConfirmWebDavDelete : BackupUiIntent()

    // 聊天存档导入：与备份/恢复同属「数据与系统」，共用本页面。
    data object ImportChatClick : BackupUiIntent()
    data class ImportChatResult(val uri: Uri?) : BackupUiIntent()
    data class ChangeImportCharacterQuery(val value: String) : BackupUiIntent()
    data class SelectImportCharacter(val characterId: Long) : BackupUiIntent()
    data object ConfirmImportChat : BackupUiIntent()
}
