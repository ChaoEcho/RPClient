package me.kafuuneko.rpclient.libs.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.libs.llm.model.OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                'story_characters'
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
                    "story_characters"
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
        migrated.execSQL(
            """
            INSERT INTO stories (
                id, title, content, memory, summary, authorNote, lorebookEntrySet,
                worldInfoStateJson, worldInfoGenerationStep, contentRevision,
                createTime, latestTime
            ) VALUES (202, 'draft', 'body', 'memory', 'summary', 'note', '[]', '{}', 3, 4, 5, 6)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO story_characters (
                storyId, characterId, sortOrder, activationMode, activationKeysJson
            ) VALUES (202, 101, 0, 1, '["alias"]')
            """.trimIndent()
        )
        migrated.query(
            """
            SELECT title, content, memory, summary, authorNote,
                   worldInfoGenerationStep, contentRevision
            FROM stories WHERE id = 202
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("draft", cursor.getString(0))
            assertEquals("body", cursor.getString(1))
            assertEquals("memory", cursor.getString(2))
            assertEquals("summary", cursor.getString(3))
            assertEquals("note", cursor.getString(4))
            assertEquals(3, cursor.getInt(5))
            assertEquals(4L, cursor.getLong(6))
        }
        migrated.query(
            """
            SELECT characterId, activationKeysJson
            FROM story_characters WHERE storyId = 202
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(101L, cursor.getLong(0))
            assertEquals("[\"alias\"]", cursor.getString(1))
        }
    }

    private companion object {
        const val DatabaseName = "app-migration-test"
        const val RegexDatabaseName = "app-regex-migration-test"
    }
}
