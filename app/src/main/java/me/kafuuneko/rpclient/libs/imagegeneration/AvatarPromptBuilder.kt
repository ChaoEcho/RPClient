package me.kafuuneko.rpclient.libs.imagegeneration

const val AVATAR_IMAGE_SIZE = "1024x1024"

/**
 * 头像外貌提炼的系统提示词。
 *
 * 角色卡描述通常包含性格、背景、说话风格与关系设定，直接塞给绘图模型会稀释外貌特征，
 * 长卡尤其明显。聊天配图已经有等价的场景提炼步骤，这里补上头像侧缺失的那一半。
 */
const val AVATAR_APPEARANCE_REFINEMENT_SYSTEM_PROMPT = """
Extract only the visible physical appearance of the character for a portrait image prompt.
Include face, hair, eyes, skin, build, clothing, accessories, and distinctive visual marks.
Do not include personality, backstory, relationships, speech style, abilities, or scene and location.
Do not include art style, camera, lighting, or rendering instructions.
Output a single concise English paragraph with no labels, analysis, or extra commentary.
"""

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
