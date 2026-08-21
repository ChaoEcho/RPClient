package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
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

/**
 * 已完成预算裁剪的故事生成请求及其可检查元数据。
 *
 * @property request 组装并裁剪完成的大模型生成请求体
 * @property inspection 包含分段 Token 消耗与裁剪明细的 Prompt 检查器元数据
 * @property nextWorldInfoStateJson 本次生成推进后的世界书时序状态 JSON
 * @property activeCharacters 本轮正文上下文触发并激活的角色卡列表
 */
data class StoryPromptBuildResult(
    val request: LLMGenerationRequest,
    val inspection: PromptInspection,
    val nextWorldInfoStateJson: String,
    val activeCharacters: List<ActiveStoryCharacter>
)

/**
 * 必需故事上下文超过模型配置的可用 Prompt 预算时抛出的异常。
 *
 * @property requiredTokens 必需固定上下文消耗的 Token 总量
 * @property promptBudget 模型配置允许的最大 Prompt Token 预算
 * @property largestCharacterNames 占用 Token 最多的前三个角色名称列表，便于 UI 提示用户裁剪
 */
class StoryPromptBudgetException(
    val requiredTokens: Int,
    val promptBudget: Int,
    val largestCharacterNames: List<String>
) : IllegalStateException("Required story context exceeds the provider budget")

/**
 * 为连续正文生成动态上下文，并保留每段内容的 Inspector 来源。
 *
 * 核心架构与职责：
 * - 动态正文分块与距离衰减：基于 Token 预算自光标位置向头部动态截取正文片段，越靠近光标权重越高；
 * - 动态角色卡激活：扫描正文激活词，支持「始终激活」、「名称命中」与「别名命中」三种激活策略；
 * - 世界书结构化扫描与预算裁剪：支持 before/after character、AN、At Depth 以及 Outlet 自定义占位符；
 * - 精细化 Token 预算防护：若必需项超限，提取占用最大的角色卡抛出 [StoryPromptBudgetException] 供诊断。
 */
class StoryPromptBuilder(
    private val mCharacterActivator: StoryCharacterActivator,
    private val mContextSelector: StoryContextSelector,
    private val mWorldBookActivator: WorldBookActivator,
    private val mRequestFinalizer: PromptRequestFinalizer
) {
    /**
     * 构建故事连续正文 AI 续写的生成请求及元数据。
     *
     * 处理步骤：
     * - 校验光标是否位于文档末尾；
     * - 依据模型配置计算最大可用 Prompt Token 预算；
     * - 动态截取靠近光标的正文片段作为直接上下文；
     * - 扫描正文并激活相关联的故事角色卡；
     * - 扫描并激活世界书条目，并根据预算比例进行溢出裁剪；
     * - 组装包含主提示词、设定、角色、正文与 At Depth 注入项的消息草稿；
     * - 调用 Finalizer 执行最终 Token 预算裁剪与格式后处理。
     *
     * @param context 故事 Prompt 构建上下文
     * @return 包含请求体、世界书下一状态与 Inspector 明细的构建结果
     * @throws StoryPromptBudgetException 当核心必需上下文超过模型 Token 上限时抛出
     */
    fun build(context: StoryPromptContext): StoryPromptBuildResult {
        // 校验光标与操作合法性
        validateOperation(context)
        // 计算可用 Prompt Token 预算（总上下文扣除最大响应 Token）
        val promptBudget = context.provider.contextTokens - context.provider.maxTokens
        val tokenizer = mRequestFinalizer.tokenizerFor(context.provider)
        // 正文上下文动态分块与相关性裁剪
        val selection = mContextSelector.select(
            content = context.sourceContent,
            target = context.target,
            authorNote = context.story.authorNote,
            tokenizer = tokenizer,
            promptBudget = promptBudget,
            continuationGuidance = context.continuationGuidance
        )
        // 扫描激活当前正文涉及的故事角色卡
        val activeCharacters = mCharacterActivator.activate(
            candidates = context.characterCandidates,
            scanText = selection.activationScanText
        )
        // 扫描并激活世界书条目
        val rawWorldInfo = mWorldBookActivator.activateStructured(
            context.toWorldBookScanContext(selection.worldBookScanText, activeCharacters)
        )
        // 解析世界书 Token 预算并执行裁剪
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
        // 提取 Outlet 占位字典
        val outlets = worldInfo.outletEntries.mapValues { (_, entries) ->
            entries.joinToString("\n") { formatWorldInfo(it) }
        }
        // 组装全部分区消息草稿列表
        val drafts = buildDrafts(context, selection, activeCharacters, worldInfo, outlets)
        // 调用 Finalizer 执行协议后处理与 Token 预算裁剪
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
            // 捕获预算超限异常并提取占用最大的角色卡以供诊断
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
        // 解析世界书时序推进状态并组装构建结果
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

    /**
     * 组装包含设定、角色卡、世界书、正文分块与续写指令的完整草稿列表。
     *
     * @param context 故事构建上下文
     * @param selection 正文分块选择结果
     * @param activeCharacters 激活的角色列表
     * @param worldInfo 激活的世界书条目结果
     * @param outlets Outlet 占位替换映射字典
     * @return 组装完成的消息草稿列表
     */
    private fun buildDrafts(
        context: StoryPromptContext,
        selection: StoryContextSelection,
        activeCharacters: List<ActiveStoryCharacter>,
        worldInfo: WorldBookActivationResult,
        outlets: Map<String, String>
    ): List<PromptMessageDraft> = buildList {
        // 注入故事主提示词
        addRequired(
            LLMMessageRole.System,
            resolveOutlets(AppModel.storyMainPrompt, outlets),
            PromptSourceKind.StoryMainPrompt
        )
        // 注入设定记忆与摘要
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
        // 注入 beforeCharacter 世界书条目
        addWorldInfo(worldInfo.beforeCharacter)
        // 注入激活的角色参考卡
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
        // 注入 afterCharacter 与 AN Top 世界书条目
        addWorldInfo(worldInfo.afterCharacter)
        addWorldInfo(worldInfo.anTop)
        // 注入作者注释与 AN Bottom
        addRequired(
            LLMMessageRole.System,
            context.story.authorNote,
            PromptSourceKind.StoryAuthorNote
        )
        addWorldInfo(worldInfo.anBottom)
        // 注入示例对话位置世界书
        addWorldInfo(worldInfo.exampleBefore + worldInfo.exampleAfter)

        // 将正文分块转换为消息草稿（按距离递减保留优先级）
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
        // 转换并按 Depth 插入世界书条目
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

        // 注入续写引导词与最终续写触发指令
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

    /** 生成因预算不足而被裁剪的正文片段统计元数据。 */
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

    /** 向草稿列表添加不可裁剪的必需消息片段。 */
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

    /** 向草稿列表批量添加世界书条目消息草稿。 */
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

    /** 将故事构建上下文转换为世界书扫描器上下文。 */
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

    /** 校验当前续写操作的光标位置是否合法（必须位于正文末尾）。 */
    private fun validateOperation(context: StoryPromptContext) {
        require(context.target.end == context.sourceContent.length) {
            "Continue requires a cursor at the end of the document"
        }
    }

    /** 组装角色激活原因与详细诊断描述文本。 */
    private fun buildCharacterDetail(active: ActiveStoryCharacter): String {
        val reason = when (active.reason) {
            StoryCharacterActivationReason.Always -> "Always"
            StoryCharacterActivationReason.Name -> "Name: ${active.matchedKey}"
            StoryCharacterActivationReason.Alias -> "Alias: ${active.matchedKey}"
        }
        return "${active.candidate.character.name} · $reason · name/description/personality/scenario"
    }

    /** 格式化故事上下文中的角色卡参考结构。 */
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

    /** 使用全局模板格式化世界书条目内容。 */
    private fun formatWorldInfo(entry: LorebookEntry): String {
        return AppModel.worldInfoFormat.replace("{0}", entry.content)
    }

    /** 替换内容中形如 `{{outlet::key}}` 的自定义世界书插槽占位符。 */
    private fun resolveOutlets(content: String, outlets: Map<String, String>): String {
        return OUTLET_PATTERN.replace(content) { match ->
            outlets[match.groupValues[1].trim()].orEmpty()
        }
    }

    private companion object {
        /** 匹配 `{{outlet::name}}` 格式自定义世界书插槽的正则表达式。 */
        val OUTLET_PATTERN = Regex(
            """\{\{\s*outlet::([^}]+)\s*\}\}""",
            RegexOption.IGNORE_CASE
        )
        /** 紧贴光标位置正文片段的最大保留优先级。 */
        const val PRIORITY_DOCUMENT_MAX = 2_000
        /** 正文片段随距离衰减的最大优先级衰减距离跨度。 */
        const val PRIORITY_DOCUMENT_DISTANCE_LIMIT = 1_500
        /** 激活世界书条目的上下文保留优先级。 */
        const val PRIORITY_WORLD_INFO = 4_000
        /** 不可裁剪的必需核心内容（主提示词、设定、角色卡等）的最高保留优先级。 */
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
 *
 * @param documentDrafts 正文分块消息草稿列表
 * @param depthDrafts 待插入的 At Depth 条目列表
 * @return 合并了深度注入项的消息草稿列表
 */
internal fun insertStoryDepthPromptDrafts(
    documentDrafts: List<PromptMessageDraft>,
    depthDrafts: List<StoryDepthPromptDraft>
): List<PromptMessageDraft> {
    if (depthDrafts.isEmpty()) return documentDrafts
    // 按相对深度对注入项进行分组与次级排序
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
    // 遍历正文片段流式插入对应的深度条目
    return buildList {
        for (index in 0..documentDrafts.size) {
            injections[index]?.forEach { add(it.draft) }
            if (index < documentDrafts.size) add(documentDrafts[index])
        }
    }
}

/** 渲染故事续写引导词模板。 */
internal fun renderContinuationGuidancePrompt(template: String, guidance: String): String {
    val normalized = guidance.trim()
    if (normalized.isEmpty()) return ""
    return template.replace("{{guidance}}", normalized)
}

/** 渲染故事设定记忆（Memory）模板。 */
internal fun renderStoryMemoryTemplate(template: String, memory: String): String {
    if (memory.isBlank()) return ""
    return template.replace("{{memory}}", memory, ignoreCase = true)
}

/** 渲染故事摘要（Summary）模板。 */
internal fun renderStorySummaryTemplate(template: String, summary: String): String {
    if (summary.isBlank()) return ""
    return template.replace("{{summary}}", summary, ignoreCase = true)
}
