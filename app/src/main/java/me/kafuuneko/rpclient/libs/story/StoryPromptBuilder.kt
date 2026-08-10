package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningEffortProvider
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningScope
import me.kafuuneko.rpclient.libs.prompt.DEFAULT_STRICT_PROMPT_PLACEHOLDER
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.PromptOmittedItem
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.PromptSource
import me.kafuuneko.rpclient.libs.prompt.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivationResult
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.prompt.WorldBookGenerationType
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanContext
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanMessage
import me.kafuuneko.rpclient.libs.prompt.filterEntries
import me.kafuuneko.rpclient.libs.prompt.fitWorldInfoToBudget
import me.kafuuneko.rpclient.libs.prompt.resolveWorldInfoBudget
import me.kafuuneko.rpclient.libs.prompt.retainStateEntries
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/** 已完成预算裁剪的故事生成请求及其可检查元数据。 */
data class StoryPromptBuildResult(
    val request: LLMGenerationRequest,
    val inspection: PromptInspection,
    val nextWorldInfoStateJson: String,
    val activeCharacters: List<ActiveStoryCharacter>
)

/** 必需故事上下文超过 Provider 可用 Prompt 预算。 */
class StoryPromptBudgetException(
    val requiredTokens: Int,
    val promptBudget: Int,
    val largestCharacterNames: List<String>
) : IllegalStateException("Required story context exceeds the provider budget")

/** 为连续正文生成动态上下文，并保留每段内容的 Inspector 来源。 */
class StoryPromptBuilder(
    private val mCharacterActivator: StoryCharacterActivator,
    private val mContextSelector: StoryContextSelector,
    private val mWorldBookActivator: WorldBookActivator,
    private val mRequestFinalizer: PromptRequestFinalizer,
    private val mReasoningEffortProvider: LLMReasoningEffortProvider =
        LLMReasoningEffortProvider.defaults
) {
    fun build(context: StoryPromptContext): StoryPromptBuildResult {
        validateOperation(context)
        val promptBudget = context.provider.contextTokens - context.provider.maxTokens
        val tokenizer = mRequestFinalizer.tokenizerFor(context.provider)
        val selection = mContextSelector.select(
            content = context.sourceContent,
            target = context.target,
            authorNote = context.story.authorNote,
            tokenizer = tokenizer,
            promptBudget = promptBudget,
            continuationGuidance = context.continuationGuidance
        )
        val activeCharacters = mCharacterActivator.activate(
            candidates = context.characterCandidates,
            scanText = selection.activationScanText
        )
        val rawWorldInfo = mWorldBookActivator.activateStructured(
            context.toWorldBookScanContext(selection.worldBookScanText, activeCharacters)
        )
        val worldBudget = resolveWorldInfoBudget(
            promptTokenBudget = promptBudget,
            contextPercent = AppModel.worldInfoBudgetPercent,
            tokenBudgetCap = AppModel.worldInfoBudgetCap
        )
        val worldSelection = fitWorldInfoToBudget(
            result = rawWorldInfo,
            globalTokenBudget = worldBudget,
            lorebooks = context.candidateLorebooks,
            tokenizer = tokenizer
        )
        val worldInfo = worldSelection.result
        val outlets = worldInfo.outletEntries.mapValues { (_, entries) ->
            entries.joinToString("\n") { formatWorldInfo(it) }
        }
        val drafts = buildDrafts(context, selection, activeCharacters, worldInfo, outlets)
        val finalized = try {
            mRequestFinalizer.finalize(
                drafts = drafts,
                provider = context.provider,
                model = context.provider.model,
                options = LLMGenerationOptions(
                    temperature = context.provider.temperature,
                    maxTokens = context.provider.maxTokens,
                    topP = context.provider.topP
                ),
                reasoningEffort = mReasoningEffortProvider.current(LLMReasoningScope.Story),
                includeReasoningInContent = false,
                maxContextTokens = context.provider.contextTokens,
                maxResponseTokens = context.provider.maxTokens,
                postProcessingMode = PromptPostProcessingMode.fromOrdinal(
                    context.provider.promptPostProcessingMode
                ),
                strictPromptPlaceholder = DEFAULT_STRICT_PROMPT_PLACEHOLDER,
                preOmittedItems = worldSelection.omittedItems + selection.documentOmission()
            )
        } catch (error: PromptBudgetExceededException) {
            val largestCharacters = activeCharacters
                .sortedByDescending { tokenizer.countText(formatCharacterReference(it)) }
                .take(3)
                .map { it.candidate.character.name }
            throw StoryPromptBudgetException(
                requiredTokens = error.requiredTokens,
                promptBudget = error.promptBudget,
                largestCharacterNames = largestCharacters
            )
        }
        val retainedWorldInfoIds = worldInfo.activatedEntries.map { it.id }.toSet()
        val nextState = mWorldBookActivator.resolveNextState(
            rawWorldInfo
                .filterEntries(retainedWorldInfoIds)
                .retainStateEntries(finalized.inspection)
        )
        return StoryPromptBuildResult(
            request = finalized.request,
            inspection = finalized.inspection,
            nextWorldInfoStateJson = nextState.nextStateJson,
            activeCharacters = activeCharacters
        )
    }

    private fun buildDrafts(
        context: StoryPromptContext,
        selection: StoryContextSelection,
        activeCharacters: List<ActiveStoryCharacter>,
        worldInfo: WorldBookActivationResult,
        outlets: Map<String, String>
    ): List<PromptMessageDraft> = buildList {
        addRequired(
            LLMMessageRole.System,
            resolveOutlets(AppModel.storyMainPrompt, outlets),
            PromptSourceKind.StoryMainPrompt
        )
        addRequired(
            LLMMessageRole.System,
            renderStoryMemoryTemplate(
                template = AppModel.storyMemoryTemplate,
                memory = context.story.memory
            ),
            PromptSourceKind.StoryMemory
        )
        addRequired(
            LLMMessageRole.System,
            renderStorySummaryTemplate(
                template = AppModel.storySummaryTemplate,
                summary = context.story.summary
            ),
            PromptSourceKind.StorySummary
        )
        addWorldInfo(worldInfo.beforeCharacter)
        activeCharacters.forEach { active ->
            val character = active.candidate.character
            addRequired(
                role = LLMMessageRole.System,
                content = formatCharacterReference(active),
                sourceKind = PromptSourceKind.StoryCharacter,
                detail = buildCharacterDetail(active),
                referenceId = character.id
            )
        }
        addWorldInfo(worldInfo.afterCharacter)
        addWorldInfo(worldInfo.anTop)
        addRequired(
            LLMMessageRole.System,
            context.story.authorNote,
            PromptSourceKind.StoryAuthorNote
        )
        addWorldInfo(worldInfo.anBottom)
        addWorldInfo(worldInfo.exampleBefore + worldInfo.exampleAfter)

        val documentDrafts = selection.chunks.mapIndexed { index, chunk ->
            PromptMessageDraft(
                role = LLMMessageRole.User,
                content = chunk.content,
                source = PromptSource(
                    kind = PromptSourceKind.StoryDocumentContext,
                    detail = "${chunk.start}-${chunk.end}",
                    referenceId = index.toLong()
                ),
                retentionPriority = PRIORITY_DOCUMENT_MAX - chunk.distance.coerceAtMost(
                    PRIORITY_DOCUMENT_DISTANCE_LIMIT
                ),
                canDrop = !chunk.required
            )
        }
        val depthDrafts = worldInfo.depthEntries.mapNotNull { group ->
            val entries = group.entries
            if (entries.isEmpty()) {
                null
            } else {
                val sources = entries.map {
                    PromptSource(PromptSourceKind.WorldInfo, it.name, it.id)
                }
                val draft = PromptMessageDraft(
                    role = group.role,
                    content = entries.joinToString("\n") { formatWorldInfo(it) },
                    source = sources.first(),
                    sources = sources,
                    retentionPriority = PRIORITY_WORLD_INFO,
                    canDrop = entries.none { it.ignoreBudget }
                )
                StoryDepthPromptDraft(
                    draft = draft,
                    depth = group.depth,
                    order = entries.first().order,
                    tieBreaker = entries.first().id
                )
            }
        }
        addAll(insertStoryDepthPromptDrafts(documentDrafts, depthDrafts))

        addRequired(
            LLMMessageRole.System,
            resolveOutlets(
                renderContinuationGuidancePrompt(
                    template = AppModel.storyContinuationGuidancePrompt,
                    guidance = context.continuationGuidance
                ),
                outlets
            ),
            PromptSourceKind.StoryContinuationGuidance
        )
        addRequired(
            LLMMessageRole.User,
            resolveOutlets(AppModel.storyContinuePrompt, outlets),
            PromptSourceKind.StoryTask,
            detail = "Continue"
        )
    }.filter { it.content.isNotBlank() }

    private fun StoryContextSelection.documentOmission(): List<PromptOmittedItem> {
        if (omittedChunkCount == 0) return emptyList()
        return listOf(
            PromptOmittedItem(
                source = PromptSource(
                    PromptSourceKind.StoryDocumentContext,
                    "$omittedChunkCount remote chunks"
                ),
                tokenCount = omittedTokenCount,
                reason = PromptOmissionReason.ContextBudget
            )
        )
    }

    private fun MutableList<PromptMessageDraft>.addRequired(
        role: LLMMessageRole,
        content: String,
        sourceKind: PromptSourceKind,
        detail: String = "",
        referenceId: Long? = null
    ) {
        if (content.isBlank()) return
        add(
            PromptMessageDraft(
                role = role,
                content = content,
                source = PromptSource(sourceKind, detail, referenceId),
                retentionPriority = PRIORITY_REQUIRED,
                canDrop = false
            )
        )
    }

    private fun MutableList<PromptMessageDraft>.addWorldInfo(entries: List<LorebookEntry>) {
        entries.forEach { entry ->
            add(
                PromptMessageDraft(
                    role = LLMMessageRole.System,
                    content = formatWorldInfo(entry),
                    source = PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                    retentionPriority = PRIORITY_WORLD_INFO,
                    canDrop = !entry.ignoreBudget
                )
            )
        }
    }

    private fun StoryPromptContext.toWorldBookScanContext(
        scanText: String,
        activeCharacters: List<ActiveStoryCharacter>
    ): WorldBookScanContext {
        return WorldBookScanContext(
            messages = listOf(WorldBookScanMessage("", scanText)),
            currentUserMessage = null,
            totalMessageCount = story.worldInfoGenerationStep + 1,
            worldInfoStateJson = story.worldInfoStateJson,
            candidateLorebookEntries = candidateLorebookEntries,
            candidateLorebooks = candidateLorebooks,
            recursiveScanningLorebookIds = recursiveScanningLorebookIds,
            generationType = WorldBookGenerationType.Continue,
            includeNames = false,
            characterDescription = activeCharacters.joinToString("\n") {
                it.candidate.character.description
            },
            characterPersonality = activeCharacters.joinToString("\n") {
                it.candidate.character.personality
            },
            scenario = activeCharacters.joinToString("\n") {
                it.candidate.character.scenario
            }
        )
    }

    private fun validateOperation(context: StoryPromptContext) {
        require(context.target.end == context.sourceContent.length) {
            "Continue requires a cursor at the end of the document"
        }
    }

    private fun buildCharacterDetail(active: ActiveStoryCharacter): String {
        val reason = when (active.reason) {
            StoryCharacterActivationReason.Always -> "Always"
            StoryCharacterActivationReason.Name -> "Name: ${active.matchedKey}"
            StoryCharacterActivationReason.Alias -> "Alias: ${active.matchedKey}"
        }
        return "${active.candidate.character.name} · $reason · name/description/personality/scenario"
    }

    private fun formatCharacterReference(active: ActiveStoryCharacter): String {
        val character = active.candidate.character
        return buildString {
            appendLine("[Character Reference]")
            appendLine("Name: ${character.name}")
            appendLine("Description: ${character.description}")
            appendLine("Personality: ${character.personality}")
            append("Scenario: ${character.scenario}")
        }
    }

    private fun formatWorldInfo(entry: LorebookEntry): String {
        return AppModel.worldInfoFormat.replace("{0}", entry.content)
    }

    private fun resolveOutlets(content: String, outlets: Map<String, String>): String {
        return OUTLET_PATTERN.replace(content) { match ->
            outlets[match.groupValues[1].trim()].orEmpty()
        }
    }

    private companion object {
        val OUTLET_PATTERN = Regex(
            """\{\{\s*outlet::([^}]+)\s*\}\}""",
            RegexOption.IGNORE_CASE
        )
        const val PRIORITY_DOCUMENT_MAX = 2_000
        const val PRIORITY_DOCUMENT_DISTANCE_LIMIT = 1_500
        const val PRIORITY_WORLD_INFO = 4_000
        const val PRIORITY_REQUIRED = 10_000
    }
}

/** 尚未插入连续正文上下文的 At Depth 消息。 */
internal data class StoryDepthPromptDraft(
    val draft: PromptMessageDraft,
    val depth: Int,
    val order: Int,
    val tieBreaker: Long
)

/**
 * 以未注入的正文列表为坐标批量插入 At Depth 消息，避免前序插入改变后续深度。
 */
internal fun insertStoryDepthPromptDrafts(
    documentDrafts: List<PromptMessageDraft>,
    depthDrafts: List<StoryDepthPromptDraft>
): List<PromptMessageDraft> {
    if (depthDrafts.isEmpty()) return documentDrafts
    val injections = depthDrafts
        .groupBy { depthDraft ->
            (documentDrafts.size - depthDraft.depth.coerceAtLeast(0))
                .coerceIn(0, documentDrafts.size)
        }
        .mapValues { (_, drafts) ->
            drafts.sortedWith(
                compareBy<StoryDepthPromptDraft> { it.order }
                    .thenBy { it.tieBreaker }
            )
        }
    return buildList {
        for (index in 0..documentDrafts.size) {
            injections[index]?.forEach { add(it.draft) }
            if (index < documentDrafts.size) add(documentDrafts[index])
        }
    }
}

internal fun renderContinuationGuidancePrompt(template: String, guidance: String): String {
    val normalized = guidance.trim()
    if (normalized.isEmpty()) return ""
    return template.replace("{{guidance}}", normalized)
}

internal fun renderStoryMemoryTemplate(template: String, memory: String): String {
    if (memory.isBlank()) return ""
    return template.replace("{{memory}}", memory, ignoreCase = true)
}

internal fun renderStorySummaryTemplate(template: String, summary: String): String {
    if (summary.isBlank()) return ""
    return template.replace("{{summary}}", summary, ignoreCase = true)
}
