package me.kafuuneko.rpclient.libs.imagegeneration

import me.kafuuneko.rpclient.utils.stripThinkBlocks

/**
 * Builds the final deterministic image prompt from identity, scene context, composition, and style.
 *
 * The scene prompt is supplied by the optional refinement step (or its deterministic fallback),
 * while this builder retains ownership of all image-specific instructions.
 */
fun buildImagePrompt(
    characterName: String,
    characterDescription: String,
    scenario: String,
    scenePrompt: String,
    stylePrompt: String
): String {
    val name = characterName.trim()
    val description = characterDescription.trim()
    val baseScenario = scenario.trim()
    val scene = scenePrompt.trim()
    val style = stylePrompt.trim()
    return buildString {
        appendLine("Create a single roleplay scene image.")
        appendLine()
        appendLine("Main character identity:")
        appendLine("Name: $name")
        appendLine("Character description: $description")
        appendLine()
        appendLine("Current scene:")
        appendLine("Base scenario: $baseScenario")
        appendLine("Visible scene description: $scene")
        appendLine()
        appendLine("Composition:")
        appendLine("The roleplay character must remain the clear primary subject.")
        appendLine("Show the visible actions, pose, facial expression, and emotion in the scene description.")
        appendLine("Preserve continuity with the base scenario and include relevant visible objects.")
        appendLine("If the action involves physical or spatial interaction with the user, show the interaction clearly when useful.")
        appendLine("The user may be represented through partial framing, hands, arms, shoulder, silhouette, over-the-shoulder composition, or first-person perspective.")
        appendLine("Do not force the user out of the scene when their presence is important to the action.")
        appendLine("Keep the roleplay character visually dominant.")
        if (style.isNotEmpty()) {
            appendLine()
            appendLine("Style:")
            appendLine(style)
        }
        appendLine()
        append("Do not render dialogue, subtitles, speech bubbles, UI elements, or watermarks.")
    }
}

/**
 * Produces a deterministic visible-scene description when optional LLM refinement is unavailable.
 */
fun buildFallbackScenePrompt(
    recentUserMessage: String?,
    assistantReply: String
): String {
    val userMessage = recentUserMessage?.stripThinkBlocks().orEmpty()
    val reply = assistantReply.stripThinkBlocks()
    return buildString {
        if (userMessage.isNotEmpty()) {
            append("The user visibly says or does: ")
            append(userMessage)
        }
        if (reply.isNotEmpty()) {
            if (isNotEmpty()) append(' ')
            append("The character visibly responds: ")
            append(reply)
        }
        if (isEmpty()) {
            append("The character remains in the current scene.")
        }
    }
}
