package me.kafuuneko.rpclient.feature.backup.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 需要 Activity 调用系统文档选择器完成的一次性事件。 */
sealed class BackupViewEvent : IViewEvent {
    data class CreateLocalBackupDocument(val fileName: String) : BackupViewEvent()
    data object OpenLocalBackupDocument : BackupViewEvent()
    data object OpenChatArchiveDocument : BackupViewEvent()
}
