package me.kafuuneko.rpclient.feature.main.model

import me.kafuuneko.rpclient.feature.main.model.items.MainChatSessionItem

/** 首页按角色组织的单聊会话组。 */
data class MainChatSessionGroup(
    val characterId: String,
    val characterName: String,
    val sessions: List<MainChatSessionItem>
)
