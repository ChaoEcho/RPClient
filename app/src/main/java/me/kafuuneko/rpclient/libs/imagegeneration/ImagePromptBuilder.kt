package me.kafuuneko.rpclient.libs.imagegeneration

/** Builds a deterministic image prompt from the current character turn. */
fun buildImagePrompt(
    characterName: String,
    characterDescription: String,
    scenario: String,
    recentUserMessage: String?,
    assistantReply: String,
    stylePrompt: String
): String {
    val userContext = recentUserMessage?.trim().orEmpty()
    val style = stylePrompt.trim()
    return buildString {
        appendLine("Create a single roleplay scene image.")
        appendLine()
        appendLine("Main character:")
        appendLine("Name: ${characterName.trim()}")
        appendLine("Character description: ${characterDescription.trim()}")
        appendLine()
        appendLine("Current scene:")
        appendLine("Base scenario: ${scenario.trim()}")
        if (userContext.isNotEmpty()) appendLine("Recent user context: $userContext")
        appendLine()
        appendLine("Current character action and emotion:")
        appendLine(assistantReply.trim())
        appendLine()
        appendLine("Composition:")
        appendLine("The roleplay character must remain the clear primary subject.")
        appendLine("Depict visible actions, pose, facial expression, and emotion implied by the current reply.")
        appendLine("Preserve continuity with the current scene.")
        appendLine()
        appendLine("If the action involves physical or spatial interaction with the user,")
        appendLine("the user may appear when useful to make that interaction visually clear.")
        appendLine("The user may be represented through partial framing, hands, arms, shoulder,")
        appendLine("silhouette, over-the-shoulder composition, or first-person perspective.")
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
