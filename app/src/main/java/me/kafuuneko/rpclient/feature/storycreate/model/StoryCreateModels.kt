package me.kafuuneko.rpclient.feature.storycreate.model

/** 创建 Story 时尚未持久化的角色与世界书选择。 */
data class StoryCreateForm(
    val title: String = "",
    val includeUserPersona: Boolean = false,
    val characterActivationModes: Map<Long, StoryCreateCharacterActivationMode> = emptyMap(),
    val selectedLorebookEntryIds: Set<Long> = emptySet()
) {
    /** 当前已选中的角色 ID 集合。 */
    val selectedCharacterIds: Set<Long>
        get() = characterActivationModes.keys

    /** 查询指定角色的激活模式；若未显式指定则默认回退至 Auto。 */
    fun activationModeOf(characterId: Long): StoryCreateCharacterActivationMode {
        return characterActivationModes[characterId] ?: StoryCreateCharacterActivationMode.Auto
    }

    /** 切换指定角色的选中状态；首个选中的角色默认为主角（Primary）。 */
    fun toggleCharacterSelection(characterId: Long): StoryCreateForm {
        if (characterId in characterActivationModes) {
            return copy(characterActivationModes = characterActivationModes - characterId)
        }
        val initialMode = if (characterActivationModes.isEmpty()) {
            StoryCreateCharacterActivationMode.Primary
        } else {
            StoryCreateCharacterActivationMode.Auto
        }
        return copy(
            characterActivationModes = characterActivationModes + (characterId to initialMode)
        )
    }

    /** 设置指定角色的激活模式；若设为 Primary 则自动将既有主角降级为 Auto。 */
    fun setCharacterActivationMode(
        characterId: Long,
        activationMode: StoryCreateCharacterActivationMode
    ): StoryCreateForm {
        if (characterId !in characterActivationModes) return this
        val nextModes = if (activationMode == StoryCreateCharacterActivationMode.Primary) {
            characterActivationModes.mapValues { (id, currentMode) ->
                when {
                    id == characterId -> StoryCreateCharacterActivationMode.Primary
                    currentMode == StoryCreateCharacterActivationMode.Primary -> {
                        StoryCreateCharacterActivationMode.Auto
                    }
                    else -> currentMode
                }
            }
        } else {
            characterActivationModes + (characterId to activationMode)
        }
        return copy(characterActivationModes = nextModes)
    }
}

/** 新建 Story 页面中的角色激活方式，不暴露 Room 的持久化取值。 */
enum class StoryCreateCharacterActivationMode {
    /** 主角：常驻置顶注入，单篇故事仅允许一个主角。 */
    Primary,
    /** 常驻配角：常驻注入。 */
    Always,
    /** 自动匹配：根据正文提及关键词动态激活。 */
    Auto
}

/** 新建 Story 页面中的角色卡候选项。 */
data class StoryCreateCharacterItem(
    val id: Long,
    val name: String,
    val description: String,
    val tags: List<String>,
    val linkedLorebookId: Long? = null,
    val linkedLorebookName: String? = null
)

/** 新建 Story 页面中可独立开启或关闭的世界书条目。 */
data class StoryCreateLorebookEntryItem(
    val id: Long,
    val lorebookName: String,
    val name: String,
    val content: String,
    val keywords: List<String>,
    val constant: Boolean,
    val order: Int,
    val depth: Int
)

/** 新建 Story 页面中的世界书分组。 */
data class StoryCreateLorebookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val entries: List<StoryCreateLorebookEntryItem>
) {
    val entryCount: Int
        get() = entries.size
}
