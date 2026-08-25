package me.kafuuneko.rpclient.libs.defaults

/** 应用自动生成并持久化的稳定英文名称。 */
object DefaultNames {
    const val USER = "You"
    const val IMPORTED_CHAT = "Imported chat"
    const val IMPORTED_STORY = "Imported story"
    const val STORY_CHAPTER = "Chapter 1"
    const val IMPORTED_WORLD_BOOK = "Imported world book"
}

/** 将用户显示名称归一化为可安全用于展示、持久化和 Prompt 宏的值。 */
fun String.normalizedUserName(): String = trim().ifBlank { DefaultNames.USER }
