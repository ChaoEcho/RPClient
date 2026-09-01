package me.kafuuneko.rpclient.feature.backup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.backup.presentation.BackupDialogState
import me.kafuuneko.rpclient.feature.backup.presentation.BackupOperationKind
import me.kafuuneko.rpclient.feature.backup.presentation.BackupOperationState
import me.kafuuneko.rpclient.feature.backup.presentation.BackupUiIntent
import me.kafuuneko.rpclient.feature.backup.presentation.BackupUiState
import me.kafuuneko.rpclient.libs.backup.BackupOperationPhase
import me.kafuuneko.rpclient.libs.backup.RemoteBackupItem
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 完整备份、Replace Restore 与 WebDAV 管理页面的 Compose 入口。 */
@Composable
fun BackupLayout(
    uiState: BackupUiState,
    emit: BackupUiIntent.() -> Unit
) {
    when (uiState) {
        BackupUiState.None -> Unit
        is BackupUiState.Finished -> BackupLayout(uiState.previous) {}
        is BackupUiState.Normal -> {
            // 操作期间由 ViewModel 保持页面，返回键不打断长任务。
            BackHandler(enabled = uiState.operation == null) {
                emit(BackupUiIntent.Back)
            }
            BackupNormalView(uiState, emit)
        }
    }
}

@Composable
private fun BackupNormalView(
    state: BackupUiState.Normal,
    emit: BackupUiIntent.() -> Unit
) {
    val operation = state.operation
    val enabled = operation == null

    // 顶部栏沿用项目统一返回行为，长任务期间忽略竞争操作。
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.backup_title),
                onBack = { if (enabled) emit(BackupUiIntent.Back) }
            )
        }
    ) { paddingValues ->
        // 内容区保持纵向滚动，并为导航栏与输入法预留底部空间。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalBackupSection(state, enabled, emit)
            WebDavSection(state, enabled, emit)
        }
    }

    // 长任务优先显示不可取消的加载反馈，其他对话框只在空闲时显示。
    if (operation != null) {
        BackupOperationLoading(operation)
    } else {
        BackupDialogSwitch(state, emit)
    }
}

@Composable
private fun LocalBackupSection(
    state: BackupUiState.Normal,
    enabled: Boolean,
    emit: BackupUiIntent.() -> Unit
) {
    val lastBackup = stringResource(
        R.string.backup_last_success,
        if (state.lastSuccessfulBackupAt > 0L) {
            formatBackupTimestamp(state.lastSuccessfulBackupAt)
        } else {
            stringResource(R.string.backup_never)
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 先展示最近一次成功结果，再提供本地创建与恢复入口。
        RpSectionHeader(title = stringResource(R.string.backup_local_section))
        RpSettingsGroup {
            RpSettingsTile(
                title = lastBackup,
                icon = Icons.Rounded.Backup,
                enabled = false
            )
            RpSettingsDivider()
            RpSettingsTile(
                title = stringResource(R.string.backup_create_full),
                subtitle = stringResource(R.string.backup_create_full_desc),
                icon = Icons.Rounded.FileUpload,
                enabled = enabled,
                onClick = { emit(BackupUiIntent.CreateLocalClick) }
            )
            RpSettingsDivider()
            RpSettingsTile(
                title = stringResource(R.string.backup_restore_full),
                subtitle = stringResource(R.string.backup_restore_full_desc),
                icon = Icons.Rounded.FileDownload,
                enabled = enabled,
                onClick = { emit(BackupUiIntent.RestoreLocalClick) }
            )
        }
    }
}

@Composable
private fun WebDavSection(
    state: BackupUiState.Normal,
    enabled: Boolean,
    emit: BackupUiIntent.() -> Unit
) {
    var webDavPassword by remember(state.dialogState, state.operation?.kind) { mutableStateOf("") }
    var rememberWebDavPassword by remember(state.dialogState, state.operation?.kind) {
        mutableStateOf(true)
    }
    val isCleartext = state.webDavBaseUrl.trim().startsWith("http://", ignoreCase = true)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 配置字段与密码只在当前页面组合中保留，密码不会进入 UiState。
        RpSectionHeader(title = stringResource(R.string.backup_webdav_section))
        RpSettingsGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.webDavBaseUrl,
                    onValueChange = { emit(BackupUiIntent.ChangeWebDavBaseUrl(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_webdav_base_url)) },
                    shape = RoundedCornerShape(16.dp)
                )
                if (isCleartext) {
                    CleartextWarning()
                }
                OutlinedTextField(
                    value = state.webDavUsername,
                    onValueChange = { emit(BackupUiIntent.ChangeWebDavUsername(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_webdav_username)) },
                    shape = RoundedCornerShape(16.dp)
                )
                SecretField(
                    value = webDavPassword,
                    onValueChange = { webDavPassword = it },
                    label = stringResource(R.string.backup_webdav_password),
                    helper = rememberedPasswordHelper(state.hasRememberedWebDavPassword),
                    enabled = enabled
                )
                OutlinedTextField(
                    value = state.webDavRemotePath,
                    onValueChange = { emit(BackupUiIntent.ChangeWebDavRemotePath(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_webdav_remote_path)) },
                    shape = RoundedCornerShape(16.dp)
                )
                // 测试与刷新共用当前输入的 WebDAV 密码和记住偏好。
                RpSettingsSwitchTile(
                    title = stringResource(R.string.backup_remember_on_device),
                    checked = rememberWebDavPassword,
                    onCheckedChange = { rememberWebDavPassword = it },
                    enabled = enabled,
                    icon = Icons.Rounded.Backup
                )
                // 将连接测试与列表刷新并列，上传操作保持单独的主按钮。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            emit(BackupUiIntent.TestWebDav(webDavPassword, rememberWebDavPassword))
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null)
                        Text(
                            text = stringResource(R.string.backup_webdav_test),
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            emit(BackupUiIntent.RefreshWebDav(webDavPassword, rememberWebDavPassword))
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Text(
                            text = stringResource(R.string.backup_webdav_refresh),
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(
                    onClick = { emit(BackupUiIntent.UploadWebDavClick) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null)
                    Text(
                        text = stringResource(R.string.backup_webdav_upload),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            // 远端条目沿用设置分组的行样式，并保持操作互斥。
            RpSettingsDivider(startIndent = false)
            RemoteBackupList(state.remoteBackups, enabled, emit)
        }
    }
}

@Composable
private fun CleartextWarning() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.70f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.backup_webdav_http_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun RemoteBackupList(
    items: List<RemoteBackupItem>,
    enabled: Boolean,
    emit: BackupUiIntent.() -> Unit
) {
    // 空列表明确反馈当前 WebDAV 目录没有可恢复备份。
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.backup_webdav_empty),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // 每行展示文件元数据，并提供独立的恢复与删除确认入口。
    items.forEachIndexed { index, item ->
        if (index > 0) {
            RpSettingsDivider(startIndent = false)
        }
        RemoteBackupTile(item, enabled, emit)
    }
}

@Composable
private fun RemoteBackupTile(
    item: RemoteBackupItem,
    enabled: Boolean,
    emit: BackupUiIntent.() -> Unit
) {
    // 禁用竞争任务时同时禁用两个远端文件操作按钮。
    RpSettingsTile(
        title = item.name,
        subtitle = remoteBackupMetadata(item),
        icon = Icons.Rounded.Backup,
        enabled = enabled,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { emit(BackupUiIntent.RestoreWebDavClick(item)) },
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = stringResource(R.string.backup_remote_restore)
                    )
                }
                IconButton(
                    onClick = { emit(BackupUiIntent.DeleteWebDavClick(item)) },
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.backup_remote_delete)
                    )
                }
            }
        }
    )
}

@Composable
private fun BackupDialogSwitch(
    state: BackupUiState.Normal,
    emit: BackupUiIntent.() -> Unit
) {
    when (val dialog = state.dialogState) {
        BackupDialogState.None -> Unit
        BackupDialogState.CreateLocal -> CreateLocalDialog(BackupDialogState.CreateLocal, state, emit)
        BackupDialogState.EnterLocalRestorePassword -> LocalRestorePasswordDialog(BackupDialogState.EnterLocalRestorePassword, state, emit)
        BackupDialogState.UploadWebDav -> UploadWebDavDialog(BackupDialogState.UploadWebDav, state, emit)
        is BackupDialogState.RestoreWebDav -> RemoteRestoreDialog(dialog, state, emit)
        is BackupDialogState.DeleteWebDav -> DeleteWebDavDialog(dialog, state, emit)
        is BackupDialogState.ConfirmRestore -> ConfirmRestoreDialog(dialog, emit)
    }
}

@Composable
private fun CreateLocalDialog(
    dialogState: BackupDialogState.CreateLocal,
    state: BackupUiState.Normal,
    emit: BackupUiIntent.() -> Unit
) {
    var password by remember(dialogState) { mutableStateOf("") }
    var confirmation by remember(dialogState) { mutableStateOf("") }
    var rememberOnDevice by remember(dialogState) { mutableStateOf(true) }
    // 两次输入必须一致；留空仅在本机已有记住密码时可提交。
    val canSubmit = password == confirmation &&
        (password.isNotEmpty() || state.hasRememberedBackupPassword)

    AppConfirmDialog(
        onDismissRequest = { emit(BackupUiIntent.DismissDialog) },
        title = stringResource(R.string.backup_create_dialog_title),
        modifier = Modifier.imePadding(),
        confirmEnabled = canSubmit,
        onConfirm = {
            emit(
                BackupUiIntent.SubmitLocalBackupPassword(
                    password = password,
                    confirmation = confirmation,
                    remember = rememberOnDevice
                )
            )
        }
    ) {
        DialogForm {
            SecretField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.backup_password),
                helper = rememberedPasswordHelper(state.hasRememberedBackupPassword)
            )
            SecretField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = stringResource(R.string.backup_password_confirm)
            )
            RpSettingsSwitchTile(
                title = stringResource(R.string.backup_remember_on_device),
                checked = rememberOnDevice,
                onCheckedChange = { rememberOnDevice = it },
                icon = Icons.Rounded.Backup
            )
        }
    }
}

@Composable
private fun LocalRestorePasswordDialog(
    dialogState: BackupDialogState.EnterLocalRestorePassword,
    state: BackupUiState.Normal,
    emit: BackupUiIntent.() -> Unit
) {
    var password by remember(dialogState) { mutableStateOf("") }
    var rememberOnDevice by remember(dialogState) { mutableStateOf(true) }
    // 校验阶段沿用本机记住密码的留空约定。
    val canSubmit = password.isNotEmpty() || state.hasRememberedBackupPassword

    AppConfirmDialog(
        onDismissRequest = { emit(BackupUiIntent.DismissDialog) },
        title = stringResource(R.string.backup_restore_password_title),
        modifier = Modifier.imePadding(),
        confirmEnabled = canSubmit,
        onConfirm = {
            emit(
                BackupUiIntent.SubmitLocalRestorePassword(
                    password = password,
                    remember = rememberOnDevice
                )
            )
        }
    ) {
        DialogForm {
            SecretField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.backup_password),
                helper = rememberedPasswordHelper(state.hasRememberedBackupPassword)
            )
            RpSettingsSwitchTile(
                title = stringResource(R.string.backup_remember_on_device),
                checked = rememberOnDevice,
                onCheckedChange = { rememberOnDevice = it },
                icon = Icons.Rounded.Backup
            )
        }
    }
}

@Composable
private fun UploadWebDavDialog(
    dialogState: BackupDialogState.UploadWebDav,
    state: BackupUiState.Normal,
    emit: BackupUiIntent.() -> Unit
) {
    var webDavPassword by remember(dialogState) { mutableStateOf("") }
    var rememberWebDavPassword by remember(dialogState) { mutableStateOf(true) }
    var backupPassword by remember(dialogState) { mutableStateOf("") }
    var backupPasswordConfirmation by remember(dialogState) { mutableStateOf("") }
    var rememberBackupPassword by remember(dialogState) { mutableStateOf(true) }
    // 上传同时要求 WebDAV 凭据可用与备份密码确认通过。
    val canSubmit = isPasswordAvailable(webDavPassword, state.hasRememberedWebDavPassword) &&
        backupPassword == backupPasswordConfirmation &&
        isPasswordAvailable(backupPassword, state.hasRememberedBackupPassword)

    AppConfirmDialog(
        onDismissRequest = { emit(BackupUiIntent.DismissDialog) },
        title = stringResource(R.string.backup_upload_dialog_title),
        modifier = Modifier.imePadding(),
        confirmText = stringResource(R.string.backup_webdav_upload),
        confirmEnabled = canSubmit,
        onConfirm = {
            emit(
                BackupUiIntent.SubmitWebDavUpload(
                    webDavPassword = webDavPassword,
                    rememberWebDavPassword = rememberWebDavPassword,
                    backupPassword = backupPassword,
                    backupPasswordConfirmation = backupPasswordConfirmation,
                    rememberBackupPassword = rememberBackupPassword
                )
            )
        }
    ) {
        DialogForm {
            SecretField(
                value = webDavPassword,
                onValueChange = { webDavPassword = it },
                label = stringResource(R.string.backup_webdav_password),
                helper = rememberedPasswordHelper(state.hasRememberedWebDavPassword)
            )
            RpSettingsSwitchTile(
                title = stringResource(R.string.backup_remember_on_device),
                checked = rememberWebDavPassword,
                onCheckedChange = { rememberWebDavPassword = it },
                icon = Icons.Rounded.CloudDone
            )
            SecretField(
                value = backupPassword,
                onValueChange = { backupPassword = it },
                label = stringResource(R.string.backup_password),
                helper = rememberedPasswordHelper(state.hasRememberedBackupPassword)
            )
            SecretField(
                value = backupPasswordConfirmation,
                onValueChange = { backupPasswordConfirmation = it },
                label = stringResource(R.string.backup_password_confirm)
            )
            RpSettingsSwitchTile(
                title = stringResource(R.string.backup_remember_on_device),
                checked = rememberBackupPassword,
                onCheckedChange = { rememberBackupPassword = it },
                icon = Icons.Rounded.Backup
            )
        }
    }
}

@Composable
private fun RemoteRestoreDialog(
    dialogState: BackupDialogState.RestoreWebDav,
    state: BackupUiState.Normal,
    emit: BackupUiIntent.() -> Unit
) {
    var webDavPassword by remember(dialogState) { mutableStateOf("") }
    var rememberWebDavPassword by remember(dialogState) { mutableStateOf(true) }
    var backupPassword by remember(dialogState) { mutableStateOf("") }
    var rememberBackupPassword by remember(dialogState) { mutableStateOf(true) }
    // 远端恢复在提交前同时收集两套密码与各自的记住偏好。
    val canSubmit = isPasswordAvailable(webDavPassword, state.hasRememberedWebDavPassword) &&
        isPasswordAvailable(backupPassword, state.hasRememberedBackupPassword)

    AppConfirmDialog(
        onDismissRequest = { emit(BackupUiIntent.DismissDialog) },
        title = stringResource(
            R.string.backup_remote_restore_dialog_title,
            dialogState.itemName
        ),
        message = stringResource(R.string.backup_restore_confirm_message),
        modifier = Modifier.imePadding(),
        confirmText = stringResource(R.string.backup_remote_restore),
        confirmEnabled = canSubmit,
        onConfirm = {
            emit(
                BackupUiIntent.SubmitWebDavRestore(
                    webDavPassword = webDavPassword,
                    rememberWebDavPassword = rememberWebDavPassword,
                    backupPassword = backupPassword,
                    rememberBackupPassword = rememberBackupPassword
                )
            )
        }
    ) {
        DialogForm {
            Text(
                text = dialogState.itemName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SecretField(
                value = webDavPassword,
                onValueChange = { webDavPassword = it },
                label = stringResource(R.string.backup_webdav_password),
                helper = rememberedPasswordHelper(state.hasRememberedWebDavPassword)
            )
            RpSettingsSwitchTile(
                title = stringResource(R.string.backup_remember_on_device),
                checked = rememberWebDavPassword,
                onCheckedChange = { rememberWebDavPassword = it },
                icon = Icons.Rounded.CloudDone
            )
            SecretField(
                value = backupPassword,
                onValueChange = { backupPassword = it },
                label = stringResource(R.string.backup_password),
                helper = rememberedPasswordHelper(state.hasRememberedBackupPassword)
            )
            RpSettingsSwitchTile(
                title = stringResource(R.string.backup_remember_on_device),
                checked = rememberBackupPassword,
                onCheckedChange = { rememberBackupPassword = it },
                icon = Icons.Rounded.Backup
            )
        }
    }
}

@Composable
private fun DeleteWebDavDialog(
    dialogState: BackupDialogState.DeleteWebDav,
    state: BackupUiState.Normal,
    emit: BackupUiIntent.() -> Unit
) {
    var webDavPassword by remember(dialogState) { mutableStateOf("") }
    var rememberWebDavPassword by remember(dialogState) { mutableStateOf(true) }
    // 删除仍需 WebDAV 凭据；空白输入可由本机记住密码补足。
    val canSubmit = isPasswordAvailable(webDavPassword, state.hasRememberedWebDavPassword)

    AppDangerDialog(
        onDismissRequest = { emit(BackupUiIntent.DismissDialog) },
        title = stringResource(R.string.backup_delete_remote_title),
        message = stringResource(R.string.backup_delete_remote_message, dialogState.itemName),
        modifier = Modifier.imePadding(),
        confirmEnabled = canSubmit,
        onConfirm = {
            emit(
                BackupUiIntent.ConfirmWebDavDelete(
                    webDavPassword = webDavPassword,
                    rememberWebDavPassword = rememberWebDavPassword
                )
            )
        }
    ) {
        DialogForm {
            SecretField(
                value = webDavPassword,
                onValueChange = { webDavPassword = it },
                label = stringResource(R.string.backup_webdav_password),
                helper = rememberedPasswordHelper(state.hasRememberedWebDavPassword)
            )
            RpSettingsSwitchTile(
                title = stringResource(R.string.backup_remember_on_device),
                checked = rememberWebDavPassword,
                onCheckedChange = { rememberWebDavPassword = it },
                icon = Icons.Rounded.CloudDone
            )
        }
    }
}

@Composable
private fun ConfirmRestoreDialog(
    dialogState: BackupDialogState.ConfirmRestore,
    emit: BackupUiIntent.() -> Unit
) {
    // 已校验的备份仍需显式确认，避免误触发不可逆的全量替换。
    AppDangerDialog(
        onDismissRequest = { emit(BackupUiIntent.DismissDialog) },
        title = stringResource(R.string.backup_restore_confirm_title),
        message = stringResource(R.string.backup_restore_confirm_message),
        modifier = Modifier.imePadding(),
        confirmText = stringResource(R.string.backup_restore_full),
        onConfirm = { emit(BackupUiIntent.ConfirmRestore) }
    ) {
        DialogForm {
            Text(
                text = stringResource(
                    R.string.backup_restore_confirm_details,
                    dialogState.appVersionName,
                    formatBackupTimestamp(dialogState.createdAt),
                    dialogState.recordCount,
                    dialogState.fileCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DialogForm(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helper: String? = null,
    enabled: Boolean = true
) {
    // 密码只存在于当前组合生命周期，并始终使用掩码输入。
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        supportingText = helper?.let { text -> { Text(text) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun rememberedPasswordHelper(remembered: Boolean): String? {
    return if (remembered) stringResource(R.string.backup_use_remembered_password) else null
}

private fun isPasswordAvailable(value: String, remembered: Boolean): Boolean {
    return value.isNotEmpty() || remembered
}

@Composable
private fun BackupOperationLoading(operation: BackupOperationState) {
    val phase = operation.phase ?: BackupOperationPhase.Preparing
    LoadingDialog(
        title = stringResource(operation.kind.titleRes()),
        description = stringResource(phase.descriptionRes())
    )
}

private fun BackupOperationKind.titleRes(): Int {
    return when (this) {
        BackupOperationKind.CreateLocal -> R.string.backup_operation_create_local
        BackupOperationKind.ValidateLocal -> R.string.backup_operation_validate_local
        BackupOperationKind.Restore -> R.string.backup_operation_restore
        BackupOperationKind.TestWebDav -> R.string.backup_operation_test_webdav
        BackupOperationKind.RefreshWebDav -> R.string.backup_operation_refresh_webdav
        BackupOperationKind.UploadWebDav -> R.string.backup_operation_upload_webdav
        BackupOperationKind.DownloadWebDav -> R.string.backup_operation_download_webdav
        BackupOperationKind.DeleteWebDav -> R.string.backup_operation_delete_webdav
    }
}

private fun BackupOperationPhase.descriptionRes(): Int {
    return when (this) {
        BackupOperationPhase.Preparing -> R.string.backup_phase_preparing
        BackupOperationPhase.ExportingDatabase -> R.string.backup_phase_exporting_database
        BackupOperationPhase.ExportingFiles -> R.string.backup_phase_exporting_files
        BackupOperationPhase.Encrypting -> R.string.backup_phase_encrypting
        BackupOperationPhase.Validating -> R.string.backup_phase_validating
        BackupOperationPhase.RestoringDatabase -> R.string.backup_phase_restoring_database
        BackupOperationPhase.RestoringSettings -> R.string.backup_phase_restoring_settings
        BackupOperationPhase.Finishing -> R.string.backup_phase_finishing
    }
}

private fun remoteBackupMetadata(item: RemoteBackupItem): String {
    val size = formatBackupSize(item.size)
    val modified = item.modifiedAt?.takeIf { it > 0L }?.let(::formatBackupTimestamp)
    return if (modified == null) size else "$size · $modified"
}

private fun formatBackupTimestamp(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatBackupSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}
