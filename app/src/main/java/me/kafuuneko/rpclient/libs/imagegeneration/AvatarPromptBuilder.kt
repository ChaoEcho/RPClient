package me.kafuuneko.rpclient.libs.imagegeneration

const val AVATAR_IMAGE_SIZE = "1024x1024"

object AvatarPromptBuilder {

    /**
     * 构造角色头像生成提示词。
     *
     * 仅接受角色名、外貌描述与头像风格提示词。
     * 严格限制单人物、单画面、方形构图，隔离场景与漫画分镜。
     */
    fun buildAvatarPrompt(
        characterName: String,
        characterDescription: String,
        avatarStylePrompt: String
    ): String {
        val name = characterName.trim()
        val description = characterDescription.trim()
        val style = avatarStylePrompt.trim()

        return buildList {
            add("Create one square profile avatar of exactly one character.")
            add("")
            add("Identity:")
            add("Name: $name")
            add("Appearance and description: $description")
            add("")
            add("Composition requirements:")
            add("- exactly one character")
            add("- one image")
            add("- one panel")
            add("- one frame")
            add("- head-and-shoulders / bust portrait")
            add("- face clearly visible")
            add("- centered composition")
            add("- character looking natural")
            add("- simple unobtrusive background")
            add("- no narrative scene")
            add("- no multiple poses")
            add("- no duplicated character")
            add("- no collage")
            add("- no split screen")
            add("- no contact sheet")
            add("- no comic page layout")
            add("- no multi-panel composition")
            if (style.isNotEmpty()) {
                add("")
                add("Style:")
                add(style)
            }
            add("")
            add("No text.")
            add("No dialogue.")
            add("No speech bubbles.")
            add("No subtitles.")
            add("No logo.")
            add("No watermark.")
        }.joinToString("\n")
    }
}

/** 顶层别名函数，便于直接导入或通过 AvatarPromptBuilder.buildAvatarPrompt 调用。 */
fun buildAvatarPrompt(
    characterName: String,
    characterDescription: String,
    avatarStylePrompt: String
): String = AvatarPromptBuilder.buildAvatarPrompt(characterName, characterDescription, avatarStylePrompt)
