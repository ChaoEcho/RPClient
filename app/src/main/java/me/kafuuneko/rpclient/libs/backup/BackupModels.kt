package me.kafuuneko.rpclient.libs.backup

/** RPClient 完整备份格式的稳定常量与显式表契约。 */
object BackupContract {
    const val FORMAT = "rpclient-backup"
    const val BACKUP_VERSION = 1
    const val CONTAINER_VERSION = 1
    const val DATABASE_VERSION = 5
    const val KDF_ITERATIONS = 200_000
    const val MIME_TYPE = "application/octet-stream"
    const val FILE_EXTENSION = ".rpbackup"
    const val PAGE_SIZE = 256

    val requiredTableEntries = listOf(
        "tables/characters.jsonl",
        "tables/character_llm_provider_associations.jsonl",
        "tables/lorebooks.jsonl",
        "tables/lorebook_entries.jsonl",
        "tables/chat_sessions.jsonl",
        "tables/chat_messages.jsonl",
        "tables/llm_providers.jsonl",
        "tables/files.jsonl",
        "tables/group_chat_sessions.jsonl",
        "tables/group_chat_members.jsonl",
        "tables/group_chat_messages.jsonl",
        "tables/group_chat_summaries.jsonl",
        "tables/regex_scripts.jsonl",
        "tables/regex_character_authorizations.jsonl",
        "tables/stories.jsonl",
        "tables/story_volumes.jsonl",
        "tables/story_chapters.jsonl",
        "tables/story_characters.jsonl",
        "tables/story_lorebook_entries.jsonl"
    )

    /**
     * 本版本之后才出现的表。
     *
     * 旧备份里没有这些条目，缺失时按空表处理；若按必需表校验，升级后所有历史备份都会被判为损坏。
     */
    val optionalTableEntries = listOf(
        "tables/image_providers.jsonl"
    )
}

/** 解密后 ZIP 中的版本化清单。 */
data class BackupManifest(
    val format: String = BackupContract.FORMAT,
    val backupVersion: Int = BackupContract.BACKUP_VERSION,
    val containerVersion: Int = BackupContract.CONTAINER_VERSION,
    val appVersionCode: Int,
    val appVersionName: String,
    val databaseVersion: Int = BackupContract.DATABASE_VERSION,
    val createdAt: Long,
    val tableCounts: Map<String, Long>,
    val fileCount: Long
)

/** 页面可展示但不含敏感数据的备份执行阶段。 */
enum class BackupOperationPhase {
    Preparing,
    ExportingDatabase,
    ExportingFiles,
    Encrypting,
    Validating,
    RestoringDatabase,
    RestoringSettings,
    Finishing
}

/** WebDAV 非敏感连接配置。 */
data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val remotePath: String
)

/** WebDAV 目录中的加密完整备份元数据。 */
data class RemoteBackupItem(
    val name: String,
    val href: String,
    val size: Long,
    val modifiedAt: Long?
)

/** 已验证并解压到私有 staging 的备份。 */
data class ValidatedBackup(
    val manifest: BackupManifest,
    val stagingDirectory: java.io.File,
    val preferencesFile: java.io.File,
    val tableFiles: Map<String, java.io.File>,
    val assetFiles: Map<String, java.io.File>
)

/** 备份领域内部异常只暴露稳定分类，具体底层错误不得直接进入 UI。 */
sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class UnsupportedFormat(cause: Throwable? = null) : BackupException("unsupported_format", cause)
    class UnsupportedVersion(cause: Throwable? = null) : BackupException("unsupported_version", cause)
    class WrongPasswordOrCorrupted(cause: Throwable? = null) :
        BackupException("wrong_password_or_corrupted", cause)
    class MissingAsset(cause: Throwable? = null) : BackupException("missing_asset", cause)
    class StorageInsufficient(cause: Throwable? = null) :
        BackupException("storage_insufficient", cause)
    class WebDavAuthenticationFailed(cause: Throwable? = null) :
        BackupException("webdav_authentication_failed", cause)
    class WebDavUnavailable(cause: Throwable? = null) :
        BackupException("webdav_unavailable", cause)
    class WebDavInvalidResponse(cause: Throwable? = null) :
        BackupException("webdav_invalid_response", cause)
    class RestoreValidationFailed(cause: Throwable? = null) :
        BackupException("restore_validation_failed", cause)
    class GenericFailure(cause: Throwable? = null) : BackupException("generic_failure", cause)
}
