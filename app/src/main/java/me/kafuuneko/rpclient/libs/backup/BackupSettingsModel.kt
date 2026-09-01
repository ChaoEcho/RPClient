package me.kafuuneko.rpclient.libs.backup

import com.chibatching.kotpref.KotprefModel

/**
 * WebDAV 的非敏感连接设置。
 *
 * 密码由 [LocalSecretStore] 单独保存；这里的字段可以安全地用于页面展示和备份设置快照。
 */
object BackupSettingsModel : KotprefModel() {
    /** WebDAV 服务基础地址。 */
    var webDavBaseUrl by stringPref(default = "")

    /** WebDAV 登录用户名。 */
    var webDavUsername by stringPref(default = "")

    /** WebDAV 备份目录，默认以根路径和尾部斜杠表示。 */
    var webDavRemotePath by stringPref(default = "/RPClient/backups/")

    /** 最近一次成功完成备份的 Unix 时间戳，单位为毫秒。 */
    var lastSuccessfulBackupAt by longPref(default = 0L)

    /** 捕获当前设置，不包含任何密码或其他秘密。 */
    fun captureWebDavConfig(): WebDavConfig = WebDavConfig(
        baseUrl = webDavBaseUrl,
        username = webDavUsername,
        remotePath = webDavRemotePath
    )
}
