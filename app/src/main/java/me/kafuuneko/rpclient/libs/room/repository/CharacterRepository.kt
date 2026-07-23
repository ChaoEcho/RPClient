package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.normalizeRegexScriptIds
import me.kafuuneko.rpclient.libs.regex.toEntity
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.utils.toJsonString
import me.kafuuneko.rpclient.utils.toStringList

/** 角色实体读写及标签、开场白序列化的业务仓库。 */
class CharacterRepository(
    private val mAppDatabase: AppDatabase,
    private val mGson: Gson,
    private val mRegexCodec: RegexScriptCodec
) {
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mRegexDao = mAppDatabase.getRegexScriptDao()

    /**
     * 获取所有角色。
     *
     * @return 角色列表。
     */
    suspend fun getAllCharacters(): List<Character> {
        return mCharacterDao.getAllCharacters()
    }

    /**
     * 根据角色 id 获取角色详情。
     *
     * @param id 角色 id。
     * @return 匹配的角色；如果不存在则返回 null。
     */
    suspend fun getCharacterById(id: Long): Character? {
        return mCharacterDao.getCharacterById(id)
    }

    /**
     * 保存角色。
     *
     * 当 id 为 0 时创建新角色；否则更新已有角色。
     *
     * @param character 要保存的角色。
     * @return 保存后的角色 id。
     */
    suspend fun saveCharacter(character: Character): Long {
        return mAppDatabase.withTransaction {
            saveCharacterInTransaction(character)
        }
    }

    /**
     * 更新已有角色。
     *
     * @param character 要更新的角色。
     */
    suspend fun updateCharacter(character: Character) {
        saveCharacter(character)
    }

    /**
     * 在事务内重读角色并只修改扩展 JSON，保留同时期提交的其他角色字段。
     *
     * @return 更新后的角色；角色已不存在时返回 null。
     */
    suspend fun updateCharacterExtensions(
        id: Long,
        transform: (String) -> String
    ): Character? {
        return mAppDatabase.withTransaction {
            val current = mCharacterDao.getCharacterById(id) ?: return@withTransaction null
            val updated = current.copy(extensionsJson = transform(current.extensionsJson))
            saveCharacterInTransaction(updated)
            mCharacterDao.getCharacterById(id)
        }
    }

    /**
     * 删除指定角色。
     *
     * @param id 角色 id。
     */
    suspend fun deleteCharacter(id: Long) {
        mCharacterDao.deleteCharacterById(id)
    }

    /**
     * 获取角色的所有开场白列表。
     *
     * @param id 角色 id。
     * @return 开场白列表；如果角色不存在或开场白为空则返回空列表。
     */
    suspend fun getCharacterFirstMessages(id: Long): List<String> {
        return getCharacterById(id)?.getChatFirstMessageList() ?: emptyList()
    }

    /**
     * 更新角色的开场白列表。
     * 自动使用 "<START>" 将列表拼接为字符串并保存。
     *
     * @param id 角色 id。
     * @param messages 开场白列表。
     * @return 更新是否成功（如果角色不存在则返回 false）。
     */
    suspend fun updateCharacterFirstMessages(id: Long, messages: List<String>): Boolean {
        val character = getCharacterById(id) ?: return false
        val newFirstMessages = messages.joinToString("<START>")
        updateCharacter(character.copy(firstMessages = newFirstMessages))
        return true
    }

    /**
     * 获取角色的所有标签列表。
     * 自动将 JSON 字符串解析为列表。
     *
     * @param id 角色 id。
     * @return 标签列表；如果角色不存在或解析失败则返回空列表。
     */
    suspend fun getCharacterTags(id: Long): List<String> {
        val character = getCharacterById(id) ?: return emptyList()
        return mGson.toStringList(character.characterTags)
    }

    /**
     * 更新角色的标签列表。
     * 自动将其转换为 JSON 字符串并保存。
     *
     * @param id 角色 id。
     * @param tags 标签列表。
     * @return 更新是否成功（如果角色不存在则返回 false）。
     */
    suspend fun updateCharacterTags(id: Long, tags: List<String>): Boolean {
        val character = getCharacterById(id) ?: return false
        updateCharacter(character.copy(characterTags = mGson.toJsonString(tags)))
        return true
    }

    /**
     * 保存角色并在同一事务内提取显式提供的 `extensions.regex_scripts`。
     *
     * 本地 Character 只保留未知扩展；Regex 表是脚本的唯一权威来源。扩展中没有该字段时保留
     * 既有脚本，显式空数组则清空对应角色脚本。
     */
    private suspend fun saveCharacterInTransaction(character: Character): Long {
        val extraction = mRegexCodec.extractFromCharacterExtensions(character.extensionsJson)
        val persistedCharacter = if (extraction.hadRegexScripts) {
            character.copy(extensionsJson = extraction.extensionsJson)
        } else {
            character
        }
        val characterId = if (persistedCharacter.id == 0L) {
            mCharacterDao.insertOrReplace(persistedCharacter)
        } else {
            mCharacterDao.update(persistedCharacter)
            persistedCharacter.id
        }
        if (extraction.hadRegexScripts) {
            mRegexDao.deleteCharacterScripts(characterId)
            val scripts = extraction.scripts.normalizeRegexScriptIds()
            if (scripts.isNotEmpty()) {
                mRegexDao.insertScripts(
                    scripts.mapIndexed { index, script ->
                        script.toEntity(characterId, index, mGson)
                    }
                )
            }
        }
        return characterId
    }
}
