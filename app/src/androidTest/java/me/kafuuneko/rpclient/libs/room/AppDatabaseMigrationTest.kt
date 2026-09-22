package me.kafuuneko.rpclient.libs.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.libs.llm.model.OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_removesHistoricalLogsAndKeepsBusinessRows() {
        migrationHelper.createDatabase(DatabaseName, 1).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions
                ) VALUES (101, 'character', '', '[]', 'description', 'personality',
                    'scenario', '[]', '', '')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO chat_sessions (
                    id, characterId, createTime, latestTime, lorebookEntrySet, title, userNote,
                    userName, userDescription, worldInfoStateJson, autoSummaryPaused
                ) VALUES (202, 101, 1, 2, '[]', 'session', '', 'user', '', '{}', 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    sendTemperature, sendTopP, promptPostProcessingMode, isEnabled,
                    createTime, updateTime
                ) VALUES (404, 'provider', 'Custom', 'OpenAICompatible',
                    'https://example.invalid', '', 'model', '', 0.8, 1.0, 1200, 8192,
                    1, 1, 0, 1, 4, 4)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO lorebooks (
                    id, name, description, scanDepth, tokenBudget,
                    recursiveScanning, extensionsJson
                ) VALUES
                    (501, 'legacy-default', '', 2, 25, 0, '{}'),
                    (502, 'explicit-budget', '', 2, 256, 0, '{}')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_request_logs (
                    id, createTime, providerName, providerType, protocol, model, isStreaming,
                    requestJson, responseJson
                ) VALUES (303, 3, 'provider', 'Custom', 'OpenAICompatible', 'model', 0,
                    '{"prompt":"PRIVATE_SENTINEL_92f1"}',
                    '{"content":"PRIVATE_SENTINEL_92f1"}')
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DatabaseName,
            2,
            true
        )

        migrated.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tableNames = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertFalse(tableNames.contains("llm_request_logs"))
        }
        migrated.query("SELECT name FROM character WHERE id = 101").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("character", cursor.getString(0))
        }
        migrated.query("SELECT title, latestTime FROM chat_sessions WHERE id = 202").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("session", cursor.getString(0))
            assertEquals(2L, cursor.getLong(1))
        }
        migrated.query(
            "SELECT tokenEstimateReservePercent FROM llm_providers WHERE id = 404"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(15, cursor.getInt(0))
        }
        migrated.query(
            "SELECT id, tokenBudget FROM lorebooks ORDER BY id"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(501L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(true, cursor.moveToNext())
            assertEquals(502L, cursor.getLong(0))
            assertEquals(256, cursor.getInt(1))
        }
    }

    @Test
    fun migrate3To4_addsNullableImageLinkToMessages() {
        migrationHelper.createDatabase(ImageDatabaseName, 3).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions
                ) VALUES (101, 'character', '', '[]', '', '', '', '[]', '', '')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO chat_sessions (
                    id, characterId, createTime, latestTime, lorebookEntrySet, title, userNote,
                    userName, userDescription, worldInfoStateJson, autoSummaryPaused
                ) VALUES (202, 101, 1, 1, '[]', 'session', '', 'user', '', '{}', 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO chat_messages (
                    id, sessionId, createTime, source, content, coveredMessageId
                ) VALUES (303, 202, 2, 'Char', 'legacy message', NULL)
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            ImageDatabaseName,
            4,
            true
        )
        migrated.query(
            "SELECT imageFileUuid FROM chat_messages WHERE id = 303"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertNull(cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrate4To5_addsNullableMimoVoiceOverrideToSessions() {
        migrationHelper.createDatabase(VoiceDatabaseName, 4).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions
                ) VALUES (101, 'character', '', '[]', '', '', '', '[]', '', '')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO chat_sessions (
                    id, characterId, createTime, latestTime, lorebookEntrySet, title, userNote,
                    userName, userDescription, worldInfoStateJson, autoSummaryPaused
                ) VALUES (202, 101, 1, 2, '[]', 'session', '', 'user', '', '{}', 0)
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            VoiceDatabaseName,
            5,
            true
        )

        migrated.query(
            "SELECT mimoTtsVoiceOverride FROM chat_sessions WHERE id = 202"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertNull(cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrate5To6_addsProviderConcurrencyWithSerialDefault() {
        migrationHelper.createDatabase(ConcurrencyDatabaseName, 5).apply {
            execSQL(
                """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model, customHeadersJson,
                    requestBodyPatchJson, temperature, topP, maxTokens, contextTokens,
                    tokenEstimateReservePercent, sendTemperature, sendTopP,
                    promptPostProcessingMode, isEnabled, createTime, updateTime
                ) VALUES (401, 'provider', 'OpenAI', 'OpenAICompatible', 'https://example.com/v1',
                    '', 'gpt-test', '{}', '{}', 1.0, 1.0, 1024, 8192, 15, 1, 1, 0, 1, 1, 2)
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            ConcurrencyDatabaseName,
            6,
            true
        )

        // 既有配置必须落在串行默认值上，升级后不能突然把并发放开。
        migrated.query(
            "SELECT maxConcurrentRequests FROM llm_providers WHERE id = 401"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrate6To7_addsGroupChatFieldsWithCompatibleDefaults() {
        migrationHelper.createDatabase(GroupChatDatabaseName, 6).apply {
            execSQL(
                """
                INSERT INTO group_chat_sessions (
                    id, title, createTime, latestTime, userName, userDescription, scenario,
                    userNote, lorebookEntrySet, worldInfoStateJson, systemPromptOverride,
                    groupNudgePromptOverride, newGroupChatPromptOverride, activationStrategy,
                    allowSelfResponses, characterCardMode, includeMutedCards, autoModeEnabled,
                    trimOtherSpeakers, autoSummaryPaused
                ) VALUES (101, 'Crew', 1, 2, 'You', '', '', '', '[]', '{}', '', '', '',
                    'Natural', 0, 'Swap', 0, 0, 1, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO group_chat_messages (
                    id, sessionId, createTime, source, content, speakerCharacterId,
                    speakerNameSnapshot, generationBatchId
                ) VALUES (201, 101, 3, 'User', 'Question', NULL, 'You', NULL)
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            GroupChatDatabaseName,
            7,
            true
        )

        migrated.query(
            "SELECT naturalMaxSpeakers, autoModeMaxRounds FROM group_chat_sessions WHERE id = 101"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
            assertEquals(2, cursor.getInt(1))
        }
        migrated.query(
            "SELECT replyToMessageId FROM group_chat_messages WHERE id = 201"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.isNull(0))
        }
        migrated.query("PRAGMA table_info(group_chat_sessions)").use { cursor ->
            val defaults = buildMap {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    if (name == "naturalMaxSpeakers" || name == "autoModeMaxRounds") {
                        put(name, cursor.getString(4))
                    }
                }
            }
            assertEquals(
                mapOf(
                    "naturalMaxSpeakers" to "2",
                    "autoModeMaxRounds" to "2"
                ),
                defaults
            )
        }
        migrated.close()
    }

    @Test
    fun migrate2To3_addsCurrentStorageAndKeepsCharacters() {
        migrationHelper.createDatabase(RegexDatabaseName, 2).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions, extensionsJson
                ) VALUES (
                    101, 'character', '', '[]', '', '', '', '', '', '',
                    '{"regex_scripts":[{"id":"legacy"}],"vendor":{"kept":true}}'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    tokenEstimateReservePercent, sendTemperature, sendTopP,
                    promptPostProcessingMode, isEnabled, createTime, updateTime
                ) VALUES (404, 'provider', 'OpenRouter', 'OpenAICompatible',
                    'https://openrouter.ai/api/v1', '', 'model', '', 0.8, 1.0,
                    1200, 8192, 15, 1, 1, 0, 1, 4, 4)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    tokenEstimateReservePercent, sendTemperature, sendTopP,
                    promptPostProcessingMode, isEnabled, createTime, updateTime
                ) VALUES (405, 'custom', 'Custom', 'OpenAICompatible',
                    'https://example.invalid', '', 'model', '', 0.8, 1.0,
                    1200, 8192, 15, 1, 1, 0, 1, 4, 4)
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            RegexDatabaseName,
            3,
            true
        )

        migrated.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table' AND name IN (
                'character_llm_provider_associations',
                'regex_scripts',
                'regex_character_authorizations',
                'stories',
                'story_characters',
                'story_chapters',
                'story_lorebook_entries',
                'story_volumes'
            )
            ORDER BY name
            """.trimIndent()
        ).use { cursor ->
            val tableNames = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertEquals(
                listOf(
                    "character_llm_provider_associations",
                    "regex_character_authorizations",
                    "regex_scripts",
                    "stories",
                    "story_chapters",
                    "story_characters",
                    "story_lorebook_entries",
                    "story_volumes"
                ),
                tableNames
            )
        }
        migrated.query("SELECT extensionsJson FROM character WHERE id = 101").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.getString(0).contains("regex_scripts"))
        }
        migrated.query("SELECT COUNT(*) FROM character_llm_provider_associations").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        migrated.query(
            "SELECT id, requestBodyPatchJson FROM llm_providers WHERE id IN (404, 405) ORDER BY id"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(404L, cursor.getLong(0))
            assertEquals(
                OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON,
                cursor.getString(1)
            )
            assertEquals(true, cursor.moveToNext())
            assertEquals(405L, cursor.getLong(0))
            assertEquals("{}", cursor.getString(1))
        }
    }

    @Test
    fun migrate9To10_addsTokenUsageStorageWithoutSensitivePayloadColumns() {
        migrationHelper.createDatabase(TokenUsageDatabaseName, 9).apply {
            execSQL(
                """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, requestBodyPatchJson, temperature, topP,
                    maxTokens, contextTokens, tokenEstimateReservePercent,
                    sendTemperature, sendTopP, promptPostProcessingMode,
                    isEnabled, createTime, updateTime
                ) VALUES (
                    404, 'existing-openai-compatible', 'ChatGPT', 'OpenAICompatible',
                    'https://proxy.example.invalid/v1', '', 'model', '', '{}',
                    0.8, 1.0, 1200, 8192, 15, 1, 1, 0, 1, 4, 4
                ), (
                    405, 'existing-gemini', 'Gemini', 'Gemini',
                    'https://generativelanguage.googleapis.com', '', 'model', '', '{}',
                    0.8, 1.0, 1200, 8192, 15, 1, 1, 0, 1, 4, 4
                ), (
                    406, 'existing-anthropic', 'Claude', 'AnthropicMessages',
                    'https://api.anthropic.com', '', 'model', '', '{}',
                    0.8, 1.0, 1200, 8192, 15, 1, 0, 0, 1, 4, 4
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TokenUsageDatabaseName,
            AppDatabase.VERSION,
            true,
            me.kafuuneko.rpclient.libs.room.migration.Migration9To10
        )

        // 表结构由 Room 校验，额外约束统计表不得保存敏感载荷。
        migrated.query("PRAGMA table_info(llm_token_usage_records)").use { cursor ->
            val columnNames = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
            assertFalse(columnNames.contains("requestJson"))
            assertFalse(columnNames.contains("responseJson"))
            assertFalse(columnNames.contains("apiKey"))
            assertFalse(columnNames.contains("baseUrl"))
        }
        migrated.query(
            """
            SELECT id, useServerReportedUsage, localTokenEstimatorType, imageInputSetting, imageTokenEstimatorType
            FROM llm_providers
            WHERE id IN (404, 405, 406)
            ORDER BY id
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(404L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("Automatic", cursor.getString(2))
            assertEquals("Auto", cursor.getString(3))
            assertEquals("Automatic", cursor.getString(4))
            assertEquals(true, cursor.moveToNext())
            assertEquals(405L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("Automatic", cursor.getString(2))
            assertEquals("Auto", cursor.getString(3))
            assertEquals("Automatic", cursor.getString(4))
            assertEquals(true, cursor.moveToNext())
            assertEquals(406L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("Automatic", cursor.getString(2))
            assertEquals("Auto", cursor.getString(3))
            assertEquals("Automatic", cursor.getString(4))
        }

        // 验证 message_images 表及其字段在 3→4 迁移中正确生成
        migrated.query("PRAGMA table_info(message_images)").use { cursor ->
            val columnNames = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
            assertEquals(setOf("messageType", "messageId", "position", "imageUuid"), columnNames)
        }
    }

    @Test
    fun migrate7To8_createsEmptyImageProviderTable() {
        migrationHelper.createDatabase(ImageProviderDatabaseName, 7).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions
                ) VALUES (101, 'character', '', '[]', '', '', '', '[]', '', '')
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            ImageProviderDatabaseName,
            8,
            true
        )

        // 建表迁移不搬运任何数据：旧的单条图片配置存在 Kotpref 里，
        // 由 ImageProviderRepository.ensureDefaultProvider() 在首次读取时播种。
        migrated.query("SELECT COUNT(*) FROM image_providers").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM character WHERE id = 101").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrate8To9_addsEmptyVisualIdentityToExistingCharacters() {
        migrationHelper.createDatabase(VisualIdentityDatabaseName, 8).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions
                ) VALUES (101, 'character', '', '[]', 'long description', '', '', '[]', '', '')
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            VisualIdentityDatabaseName,
            9,
            true
        )

        // 既有角色必须落在空缓存上：迁移不能凭空造出一份没提炼过的外貌。
        migrated.query(
            "SELECT description, visualIdentity FROM character WHERE id = 101"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("long description", cursor.getString(0))
            assertEquals("", cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun everyForkSchemaMigratesToCurrentWithoutReplacingHistory() {
        for (version in 1..9) {
            val name = "fork-history-$version-to-current"
            migrationHelper.createDatabase(name, version).close()
            migrationHelper.runMigrationsAndValidate(
                name, AppDatabase.VERSION, true,
                me.kafuuneko.rpclient.libs.room.migration.Migration9To10
            ).close()
        }
    }

    @Test
    fun migrate9To10RetainsCharacterAndMovesGeneratedImageToDisplayOnlyAttachment() {
        val name = "fork-generated-image-to-v10"
        migrationHelper.createDatabase(name, 9).apply {
            execSQL("""
                INSERT INTO character (id, name, avatar, characterTags, description, personality,
                    scenario, firstMessages, examplesOfDialogue, postHistoryInstructions)
                VALUES (101, 'fixture', 'old-image', '[]', '', '', '', '[]', '', '')
            """.trimIndent())
            execSQL("""
                INSERT INTO chat_sessions (id, characterId, createTime, latestTime, lorebookEntrySet,
                    title, userNote, userName, userDescription, worldInfoStateJson, autoSummaryPaused)
                VALUES (202, 101, 1, 2, '[]', 'session', '', 'user', '', '{}', 0)
            """.trimIndent())
            execSQL("INSERT INTO files(uuid, hash, mimeType) VALUES ('old-image', ?, 'image/png')", arrayOf("a".repeat(64)))
            execSQL("""
                INSERT INTO chat_messages(id, sessionId, createTime, source, content, imageFileUuid)
                VALUES (303, 202, 1, 'Char', 'reply', 'old-image')
            """.trimIndent())
            close()
        }
        migrationHelper.runMigrationsAndValidate(name, AppDatabase.VERSION, true,
            me.kafuuneko.rpclient.libs.room.migration.Migration9To10).use { db ->
            db.query("SELECT imageUuid, sendToModel FROM message_images WHERE messageId=303").use {
                assertEquals(true, it.moveToFirst())
                assertEquals("generated-v9-303-old-image", it.getString(0))
                assertEquals(0, it.getInt(1))
            }
            db.query("SELECT imageFileUuid FROM chat_messages WHERE id=303").use {
                assertEquals(true, it.moveToFirst())
                assertEquals(true, it.isNull(0))
            }
            db.query("SELECT COUNT(*) FROM files WHERE uuid='old-image'").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    private companion object {
        const val TokenUsageDatabaseName = "app-token-usage-migration-test"
        const val DatabaseName = "app-migration-test"
        const val RegexDatabaseName = "app-regex-migration-test"
        const val ImageDatabaseName = "app-image-migration-test"
        const val VoiceDatabaseName = "app-voice-migration-test"
        const val ConcurrencyDatabaseName = "app-concurrency-migration-test"
        const val GroupChatDatabaseName = "app-group-chat-migration-test"
        const val ImageProviderDatabaseName = "app-image-provider-migration-test"
        const val VisualIdentityDatabaseName = "app-visual-identity-migration-test"
    }
}
