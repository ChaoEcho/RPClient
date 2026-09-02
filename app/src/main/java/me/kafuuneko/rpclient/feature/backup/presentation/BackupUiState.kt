package me.kafuuneko.rpclient.feature.backup.presentation

import me.kafuuneko.rpclient.libs.backup.BackupOperationPhase
import me.kafuuneko.rpclient.libs.backup.RemoteBackupItem

/** 完整备份页面状态，不持有备份密码或本地文件路径。 */
sealed class BackupUiState {
    data object None : BackupUiState()

    data class Normal(
        val webDavBaseUrl: String,
        val webDavUsername: String,
        val webDavPassword: String,
        val webDavRemotePath: String,
        val lastSuccessfulBackupAt: Long,
        val remoteBackups: List<RemoteBackupItem> = emptyList(),
        val operation: BackupOperationState? = null,
        val isReadingChatArchive: Boolean = false,
        val dialogState: BackupDialogState = BackupDialogState.None
    ) : BackupUiState()

    data class Finished(val previous: BackupUiState) : BackupUiState()
}

/** 当前唯一允许执行的备份任务及其可读阶段。 */
data class BackupOperationState(
    val kind: BackupOperationKind,
    val phase: BackupOperationPhase? = null
)

/** 页面支持的互斥长任务。 */
enum class BackupOperationKind {
    CreateLocal,
    ValidateLocal,
    Restore,
    TestWebDav,
    RefreshWebDav,
    UploadWebDav,
    DownloadWebDav,
    DeleteWebDav
}

/** 页面对话框状态只记录非敏感确认信息。 */
sealed class BackupDialogState {
    data object None : BackupDialogState()
    data object CreateLocal : BackupDialogState()
    data object EnterLocalRestorePassword : BackupDialogState()
    data object UploadWebDav : BackupDialogState()
    data class RestoreWebDav(val itemName: String) : BackupDialogState()
    data class DeleteWebDav(val itemName: String) : BackupDialogState()
    data class ConfirmRestore(
        val appVersionName: String,
        val createdAt: Long,
        val recordCount: Long,
        val fileCount: Long
    ) : BackupDialogState()

    /** 聊天存档导入时选择归属角色。 */
    data class ImportChatCharacterSelection(
        val title: String,
        val sourceCharacterName: String,
        val messageCount: Int,
        val query: String,
        val characters: List<ImportCharacterItem>,
        val visibleCharacters: List<ImportCharacterItem>,
        val selectedCharacterId: Long?,
        val isImporting: Boolean = false
    ) : BackupDialogState()
}

/** 导入弹窗中的候选角色条目。 */
data class ImportCharacterItem(
    val id: Long,
    val name: String,
    val details: String
)
