package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition

/** JVM 测试显式提供的配置；引用编译期常量，不初始化 Android 偏好存储。 */
internal class TestPromptPreferences(
    override val mainPrompt: String = AppModel.DEFAULT_MAIN_PROMPT,
    override val postHistoryInstructions: String = AppModel.DEFAULT_POST_HISTORY_INSTRUCTIONS,
    override val auxiliaryPrompt: String = AppModel.DEFAULT_AUXILIARY_PROMPT,
    override val impersonationPrompt: String = AppModel.DEFAULT_IMPERSONATION_PROMPT,
    override val newChatPrompt: String = AppModel.DEFAULT_NEW_CHAT_PROMPT,
    override val newExampleChatPrompt: String = AppModel.DEFAULT_NEW_EXAMPLE_CHAT_PROMPT,
    override val continueNudgePrompt: String = AppModel.DEFAULT_CONTINUE_NUDGE_PROMPT,
    override val groupNudgePrompt: String = AppModel.DEFAULT_GROUP_NUDGE_PROMPT,
    override val newGroupChatPrompt: String = AppModel.DEFAULT_NEW_GROUP_CHAT_PROMPT,
    override val worldInfoFormat: String = AppModel.DEFAULT_WORLD_INFO_FORMAT,
    override val scenarioFormat: String = AppModel.DEFAULT_SCENARIO_FORMAT,
    override val personalityFormat: String = AppModel.DEFAULT_PERSONALITY_FORMAT,
    override val userPersonaFormat: String = AppModel.DEFAULT_USER_PERSONA_FORMAT,
    override val summaryInjectionTemplate: String = AppModel.DEFAULT_SUMMARY_INJECTION_TEMPLATE,
    override val summaryInjectionPosition: Int = SummaryInjectionPosition.default.persistedValue,
    override val summaryInjectionDepth: Int = 2,
    override val summaryInjectionRole: Int = 0,
    override val worldInfoBudgetPercent: Int = 25,
    override val worldInfoBudgetCap: Int = 0,
    override val includeThinkInContext: Boolean = false
) : PromptPreferences
