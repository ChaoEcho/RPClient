package me.kafuuneko.rpclient.libs.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 沿用 fork v1–v9 历史，只在 v10 接入上游用量与附件，不能套用上游另一套 v4。 */
object Migration9To10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 新索引以前两列为前缀覆盖旧查询，不能把旧声明留在实际 schema 中。
        db.execSQL("DROP INDEX IF EXISTS `index_group_chat_messages_sessionId_createTime`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_createTime_id` ON `chat_messages` (`sessionId`, `createTime`, `id`)")
        db.execSQL("ALTER TABLE `llm_providers` ADD COLUMN `localTokenEstimatorType` TEXT NOT NULL DEFAULT 'Automatic'")
        db.execSQL("ALTER TABLE `llm_providers` ADD COLUMN `useServerReportedUsage` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `llm_providers` ADD COLUMN `imageInputSetting` TEXT NOT NULL DEFAULT 'Auto'")
        db.execSQL("ALTER TABLE `llm_providers` ADD COLUMN `imageTokenEstimatorType` TEXT NOT NULL DEFAULT 'Automatic'")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_chat_messages_sessionId_createTime_id` ON `group_chat_messages` (`sessionId`, `createTime`, `id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `llm_token_usage_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createTime` INTEGER NOT NULL, `providerId` INTEGER, `providerName` TEXT NOT NULL, `providerType` TEXT NOT NULL, `protocol` TEXT NOT NULL, `apiHost` TEXT NOT NULL, `apiPort` INTEGER NOT NULL, `requestedModel` TEXT NOT NULL, `effectiveModel` TEXT NOT NULL, `isStreaming` INTEGER NOT NULL, `inputTokens` INTEGER NOT NULL, `outputTokens` INTEGER NOT NULL, `inputTokenSource` TEXT NOT NULL, `outputTokenSource` TEXT NOT NULL, `cachedInputTokens` INTEGER, `reasoningTokens` INTEGER, `tokenizerName` TEXT, `durationMs` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_llm_token_usage_records_createTime` ON `llm_token_usage_records` (`createTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_llm_token_usage_records_effectiveModel_apiHost_apiPort_createTime` ON `llm_token_usage_records` (`effectiveModel`, `apiHost`, `apiPort`, `createTime`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `message_images` (`messageType` TEXT NOT NULL DEFAULT 'single', `messageId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `imageUuid` TEXT NOT NULL, `sendToModel` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`messageType`, `messageId`, `position`), FOREIGN KEY(`imageUuid`) REFERENCES `files`(`uuid`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_message_images_imageUuid` ON `message_images` (`imageUuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_images_messageType_messageId` ON `message_images` (`messageType`, `messageId`)")
        db.execSQL("UPDATE llm_providers SET useServerReportedUsage = 1 WHERE protocol IN ('Gemini', 'AnthropicMessages')")
        migrateLegacyGeneratedImages(db)
    }
}

/**
 * 数据库升级和 v1 备份恢复共用的配图转换；原字段清空后重复执行没有副作用。
 * 独立引用避免历史 UUID 同时被角色头像或多个消息使用时相互删除；原索引不擅自回收。
 */
internal fun migrateLegacyGeneratedImages(db: SupportSQLiteDatabase) {
    db.execSQL("""
        INSERT INTO files(uuid, hash, mimeType)
        SELECT 'generated-v9-' || messages.id || '-' || files.uuid, files.hash, files.mimeType
        FROM chat_messages AS messages INNER JOIN files ON files.uuid = messages.imageFileUuid
        WHERE messages.source = 'Char'
    """.trimIndent())
    db.execSQL("""
        INSERT INTO message_images(messageType, messageId, position, imageUuid, sendToModel)
        SELECT 'single', messages.id, 0, 'generated-v9-' || messages.id || '-' || files.uuid, 0
        FROM chat_messages AS messages INNER JOIN files ON files.uuid = messages.imageFileUuid
        WHERE messages.source = 'Char'
    """.trimIndent())
    db.execSQL("UPDATE chat_messages SET imageFileUuid = NULL WHERE imageFileUuid IS NOT NULL")
}
