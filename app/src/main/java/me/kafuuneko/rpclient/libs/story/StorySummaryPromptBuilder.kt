package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningEffortProvider
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningScope
import me.kafuuneko.rpclient.libs.prompt.DEFAULT_STRICT_PROMPT_PLACEHOLDER
import me.kafuuneko.rpclient.libs.prompt.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.PromptSource
import me.kafuuneko.rpclient.libs.prompt.PromptSourceKind
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 使用与故事续写相同的正文裁剪规则，构建单次剧情摘要请求。 */
class StorySummaryPromptBuilder(
    private val mContextSelector: StoryContextSelector,
    private val mRequestFinalizer: PromptRequestFinalizer,
    private val mReasoningEffortProvider: LLMReasoningEffortProvider =
        LLMReasoningEffortProvider.defaults
) {
    fun build(
        memory: String,
        currentSummary: String,
        content: String,
        provider: LLMProvider
    ): LLMGenerationRequest {
        require(content.isNotBlank()) { "Story manuscript cannot be blank" }
        val responseTokens = AppModel.summaryResponseTokens.coerceAtLeast(1)
        val promptBudget = provider.contextTokens - responseTokens
        require(promptBudget > 0) {
            "Summary response token reserve must be smaller than the context token limit."
        }
        val selection = mContextSelector.select(
            content = content,
            target = StoryEditTarget(content.length, content.length),
            authorNote = "",
            tokenizer = mRequestFinalizer.tokenizerFor(provider),
            promptBudget = promptBudget
        )
        val drafts = buildList {
            addRequired(
                AppModel.storySummarizePrompt.replace(
                    "{{words}}",
                    AppModel.summaryWordsLimit.toString(),
                    ignoreCase = true
                ),
                PromptSourceKind.StoryMainPrompt
            )
            addRequired(
                renderStoryMemoryTemplate(AppModel.storyMemoryTemplate, memory),
                PromptSourceKind.StoryMemory
            )
            addRequired(
                renderStorySummaryTemplate(
                    AppModel.storySummaryTemplate,
                    currentSummary
                ),
                PromptSourceKind.StorySummary
            )
            selection.chunks.forEachIndexed { index, chunk ->
                add(
                    PromptMessageDraft(
                        role = LLMMessageRole.User,
                        content = chunk.content,
                        source = PromptSource(
                            kind = PromptSourceKind.StoryDocumentContext,
                            detail = "${chunk.start}-${chunk.end}",
                            referenceId = index.toLong()
                        ),
                        retentionPriority = PRIORITY_DOCUMENT_MAX -
                            chunk.distance.coerceAtMost(PRIORITY_DOCUMENT_DISTANCE_LIMIT),
                        canDrop = !chunk.required
                    )
                )
            }
        }
        return mRequestFinalizer.finalize(
            drafts = drafts,
            provider = provider,
            model = provider.model,
            options = LLMGenerationOptions(
                temperature = provider.temperature,
                maxTokens = responseTokens,
                topP = provider.topP
            ),
            reasoningEffort = mReasoningEffortProvider.current(LLMReasoningScope.Story),
            includeReasoningInContent = false,
            maxContextTokens = provider.contextTokens,
            maxResponseTokens = responseTokens,
            postProcessingMode = PromptPostProcessingMode.fromOrdinal(
                provider.promptPostProcessingMode
            ),
            strictPromptPlaceholder = DEFAULT_STRICT_PROMPT_PLACEHOLDER
        ).request
    }

    private fun MutableList<PromptMessageDraft>.addRequired(
        content: String,
        sourceKind: PromptSourceKind
    ) {
        if (content.isBlank()) return
        add(
            PromptMessageDraft(
                role = LLMMessageRole.System,
                content = content,
                source = PromptSource(sourceKind),
                retentionPriority = PRIORITY_REQUIRED,
                canDrop = false
            )
        )
    }

    private companion object {
        const val PRIORITY_DOCUMENT_MAX = 2_000
        const val PRIORITY_DOCUMENT_DISTANCE_LIMIT = 1_500
        const val PRIORITY_REQUIRED = 10_000
    }
}
