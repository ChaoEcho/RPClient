package me.kafuuneko.rpclient.feature.characteredit.model

/** 角色编辑页可绑定的模型配置摘要，不包含鉴权信息。 */
data class CharacterProviderItem(
    val id: Long,
    val name: String,
    val model: String,
    val isEnabled: Boolean
)
