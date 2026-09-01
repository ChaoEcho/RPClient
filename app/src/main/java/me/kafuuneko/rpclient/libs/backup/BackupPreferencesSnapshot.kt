package me.kafuuneko.rpclient.libs.backup

import me.kafuuneko.rpclient.libs.AppModel

/**
 * 完整备份中显式维护的用户偏好契约。
 *
 * 安装身份、升级记账、默认模型初始化状态和本机安全凭据不属于该快照。
 */
data class BackupPreferencesSnapshot(
    val version: Int = CURRENT_VERSION,
    val currentLLMProvider: Long,
    val summaryLLMProvider: Long,
    val imagePromptLLMProvider: Long,
    val ttsProvider: String,
    val ttsSystemLanguageTag: String,
    val ttsSystemVoiceName: String,
    val ttsSystemSpeechRate: Float,
    val ttsSystemPitch: Float,
    val ttsMimoBaseUrl: String,
    val ttsMimoApiKey: String,
    val ttsMimoModel: String,
    val ttsMimoVoice: String,
    val ttsMimoInstructions: String,
    val ttsMimoTemperature: Float,
    val ttsMimoStreaming: Boolean,
    val ttsAzureApiKey: String,
    val ttsAzureRegion: String,
    val ttsAzureVoice: String,
    val ttsAzureSpeechRate: Float,
    val imageGenerationBaseUrl: String,
    val imageGenerationApiKey: String,
    val imageGenerationModel: String,
    val imageGenerationSize: String,
    val imageGenerationStylePrompt: String,
    val mainPrompt: String,
    val summarizePrompt: String,
    val postHistoryInstructions: String,
    val auxiliaryPrompt: String,
    val impersonationPrompt: String,
    val newChatPrompt: String,
    val newExampleChatPrompt: String,
    val continueNudgePrompt: String,
    val replaceEmptyMessagePrompt: String,
    val worldInfoFormat: String,
    val scenarioFormat: String,
    val personalityFormat: String,
    val userPersonaFormat: String,
    val groupNudgePrompt: String,
    val newGroupChatPrompt: String,
    val groupSummarizePrompt: String,
    val storyMainPrompt: String,
    val storyMemoryTemplate: String,
    val storySummaryTemplate: String,
    val storySummarizePrompt: String,
    val storyContinuationGuidancePrompt: String,
    val storyContinuePrompt: String,
    val streamEnabled: Boolean,
    val userName: String,
    val userAvatar: String,
    val userDescription: String,
    val summaryWordsLimit: Int,
    val autoSummaryEnabled: Boolean,
    val summaryTriggerMessageCount: Int,
    val summaryMaxMessagesPerRequest: Int,
    val summaryResponseTokens: Int,
    val summaryInjectionTemplate: String,
    val summaryInjectionPosition: Int,
    val summaryInjectionDepth: Int,
    val summaryInjectionRole: Int,
    val worldInfoBudgetPercent: Int,
    val worldInfoBudgetCap: Int,
    val worldInfoOverflowAlert: Boolean,
    val contextTrimmingAlert: Boolean,
    val exampleDialogueBehavior: Int,
    val includeThinkInContext: Boolean,
    val debugModeEnabled: Boolean,
    val autoGenerateImageAfterReply: Boolean = false
) {
    /** 将快照应用到当前安装，同时保留安装身份与升级记账。 */
    fun apply() {
        // 模型、语音和图片服务配置包含恢复后继续使用所需的凭据
        AppModel.currentLLMProvider = currentLLMProvider
        AppModel.summaryLLMProvider = summaryLLMProvider
        AppModel.imagePromptLLMProvider = imagePromptLLMProvider
        AppModel.ttsProvider = ttsProvider
        AppModel.ttsSystemLanguageTag = ttsSystemLanguageTag
        AppModel.ttsSystemVoiceName = ttsSystemVoiceName
        AppModel.ttsSystemSpeechRate = ttsSystemSpeechRate
        AppModel.ttsSystemPitch = ttsSystemPitch
        AppModel.ttsMimoBaseUrl = ttsMimoBaseUrl
        AppModel.ttsMimoApiKey = ttsMimoApiKey
        AppModel.ttsMimoModel = ttsMimoModel
        AppModel.ttsMimoVoice = ttsMimoVoice
        AppModel.ttsMimoInstructions = ttsMimoInstructions
        AppModel.ttsMimoTemperature = ttsMimoTemperature
        AppModel.ttsMimoStreaming = ttsMimoStreaming
        AppModel.ttsAzureApiKey = ttsAzureApiKey
        AppModel.ttsAzureRegion = ttsAzureRegion
        AppModel.ttsAzureVoice = ttsAzureVoice
        AppModel.ttsAzureSpeechRate = ttsAzureSpeechRate
        AppModel.imageGenerationBaseUrl = imageGenerationBaseUrl
        AppModel.imageGenerationApiKey = imageGenerationApiKey
        AppModel.imageGenerationModel = imageGenerationModel
        AppModel.imageGenerationSize = imageGenerationSize
        AppModel.imageGenerationStylePrompt = imageGenerationStylePrompt
        // Prompt 和用户身份按原值恢复，不对用户文本做规范化或裁剪
        AppModel.mainPrompt = mainPrompt
        AppModel.summarizePrompt = summarizePrompt
        AppModel.postHistoryInstructions = postHistoryInstructions
        AppModel.auxiliaryPrompt = auxiliaryPrompt
        AppModel.impersonationPrompt = impersonationPrompt
        AppModel.newChatPrompt = newChatPrompt
        AppModel.newExampleChatPrompt = newExampleChatPrompt
        AppModel.continueNudgePrompt = continueNudgePrompt
        AppModel.replaceEmptyMessagePrompt = replaceEmptyMessagePrompt
        AppModel.worldInfoFormat = worldInfoFormat
        AppModel.scenarioFormat = scenarioFormat
        AppModel.personalityFormat = personalityFormat
        AppModel.userPersonaFormat = userPersonaFormat
        AppModel.groupNudgePrompt = groupNudgePrompt
        AppModel.newGroupChatPrompt = newGroupChatPrompt
        AppModel.groupSummarizePrompt = groupSummarizePrompt
        AppModel.storyMainPrompt = storyMainPrompt
        AppModel.storyMemoryTemplate = storyMemoryTemplate
        AppModel.storySummaryTemplate = storySummaryTemplate
        AppModel.storySummarizePrompt = storySummarizePrompt
        AppModel.storyContinuationGuidancePrompt = storyContinuationGuidancePrompt
        AppModel.storyContinuePrompt = storyContinuePrompt
        AppModel.streamEnabled = streamEnabled
        AppModel.userName = userName
        AppModel.userAvatar = userAvatar
        AppModel.userDescription = userDescription
        // 摘要、上下文和调试行为属于可迁移的用户配置
        AppModel.summaryWordsLimit = summaryWordsLimit
        AppModel.autoSummaryEnabled = autoSummaryEnabled
        AppModel.summaryTriggerMessageCount = summaryTriggerMessageCount
        AppModel.summaryMaxMessagesPerRequest = summaryMaxMessagesPerRequest
        AppModel.summaryResponseTokens = summaryResponseTokens
        AppModel.summaryInjectionTemplate = summaryInjectionTemplate
        AppModel.summaryInjectionPosition = summaryInjectionPosition
        AppModel.summaryInjectionDepth = summaryInjectionDepth
        AppModel.summaryInjectionRole = summaryInjectionRole
        AppModel.worldInfoBudgetPercent = worldInfoBudgetPercent
        AppModel.worldInfoBudgetCap = worldInfoBudgetCap
        AppModel.worldInfoOverflowAlert = worldInfoOverflowAlert
        AppModel.contextTrimmingAlert = contextTrimmingAlert
        AppModel.exampleDialogueBehavior = exampleDialogueBehavior
        AppModel.includeThinkInContext = includeThinkInContext
        AppModel.debugModeEnabled = debugModeEnabled
        AppModel.autoGenerateImageAfterReply = autoGenerateImageAfterReply
        // 即使用户有意保留空模型列表，也不能在恢复后再次生成默认模型
        AppModel.llmDefaultProvidersInitialized = true
    }

    /** 拒绝字段缺失或契约版本不支持的偏好快照。 */
    fun validate() {
        if (version != CURRENT_VERSION) throw BackupException.RestoreValidationFailed()
        val requiredStrings = arrayOf<Any?>(
            ttsProvider,
            ttsSystemLanguageTag,
            ttsSystemVoiceName,
            ttsMimoBaseUrl,
            ttsMimoApiKey,
            ttsMimoModel,
            ttsMimoVoice,
            ttsMimoInstructions,
            ttsAzureApiKey,
            ttsAzureRegion,
            ttsAzureVoice,
            imageGenerationBaseUrl,
            imageGenerationApiKey,
            imageGenerationModel,
            imageGenerationSize,
            imageGenerationStylePrompt,
            mainPrompt,
            summarizePrompt,
            postHistoryInstructions,
            auxiliaryPrompt,
            impersonationPrompt,
            newChatPrompt,
            newExampleChatPrompt,
            continueNudgePrompt,
            replaceEmptyMessagePrompt,
            worldInfoFormat,
            scenarioFormat,
            personalityFormat,
            userPersonaFormat,
            groupNudgePrompt,
            newGroupChatPrompt,
            groupSummarizePrompt,
            storyMainPrompt,
            storyMemoryTemplate,
            storySummaryTemplate,
            storySummarizePrompt,
            storyContinuationGuidancePrompt,
            storyContinuePrompt,
            userName,
            userAvatar,
            userDescription,
            summaryInjectionTemplate
        )
        if (requiredStrings.any { it == null }) throw BackupException.RestoreValidationFailed()
    }

    companion object {
        const val CURRENT_VERSION = 1

        /** 从当前 AppModel 捕获全部属于备份契约的用户配置。 */
        fun capture(): BackupPreferencesSnapshot {
            return BackupPreferencesSnapshot(
                currentLLMProvider = AppModel.currentLLMProvider,
                summaryLLMProvider = AppModel.summaryLLMProvider,
                imagePromptLLMProvider = AppModel.imagePromptLLMProvider,
                ttsProvider = AppModel.ttsProvider,
                ttsSystemLanguageTag = AppModel.ttsSystemLanguageTag,
                ttsSystemVoiceName = AppModel.ttsSystemVoiceName,
                ttsSystemSpeechRate = AppModel.ttsSystemSpeechRate,
                ttsSystemPitch = AppModel.ttsSystemPitch,
                ttsMimoBaseUrl = AppModel.ttsMimoBaseUrl,
                ttsMimoApiKey = AppModel.ttsMimoApiKey,
                ttsMimoModel = AppModel.ttsMimoModel,
                ttsMimoVoice = AppModel.ttsMimoVoice,
                ttsMimoInstructions = AppModel.ttsMimoInstructions,
                ttsMimoTemperature = AppModel.ttsMimoTemperature,
                ttsMimoStreaming = AppModel.ttsMimoStreaming,
                ttsAzureApiKey = AppModel.ttsAzureApiKey,
                ttsAzureRegion = AppModel.ttsAzureRegion,
                ttsAzureVoice = AppModel.ttsAzureVoice,
                ttsAzureSpeechRate = AppModel.ttsAzureSpeechRate,
                imageGenerationBaseUrl = AppModel.imageGenerationBaseUrl,
                imageGenerationApiKey = AppModel.imageGenerationApiKey,
                imageGenerationModel = AppModel.imageGenerationModel,
                imageGenerationSize = AppModel.imageGenerationSize,
                imageGenerationStylePrompt = AppModel.imageGenerationStylePrompt,
                mainPrompt = AppModel.mainPrompt,
                summarizePrompt = AppModel.summarizePrompt,
                postHistoryInstructions = AppModel.postHistoryInstructions,
                auxiliaryPrompt = AppModel.auxiliaryPrompt,
                impersonationPrompt = AppModel.impersonationPrompt,
                newChatPrompt = AppModel.newChatPrompt,
                newExampleChatPrompt = AppModel.newExampleChatPrompt,
                continueNudgePrompt = AppModel.continueNudgePrompt,
                replaceEmptyMessagePrompt = AppModel.replaceEmptyMessagePrompt,
                worldInfoFormat = AppModel.worldInfoFormat,
                scenarioFormat = AppModel.scenarioFormat,
                personalityFormat = AppModel.personalityFormat,
                userPersonaFormat = AppModel.userPersonaFormat,
                groupNudgePrompt = AppModel.groupNudgePrompt,
                newGroupChatPrompt = AppModel.newGroupChatPrompt,
                groupSummarizePrompt = AppModel.groupSummarizePrompt,
                storyMainPrompt = AppModel.storyMainPrompt,
                storyMemoryTemplate = AppModel.storyMemoryTemplate,
                storySummaryTemplate = AppModel.storySummaryTemplate,
                storySummarizePrompt = AppModel.storySummarizePrompt,
                storyContinuationGuidancePrompt = AppModel.storyContinuationGuidancePrompt,
                storyContinuePrompt = AppModel.storyContinuePrompt,
                streamEnabled = AppModel.streamEnabled,
                userName = AppModel.userName,
                userAvatar = AppModel.userAvatar,
                userDescription = AppModel.userDescription,
                summaryWordsLimit = AppModel.summaryWordsLimit,
                autoSummaryEnabled = AppModel.autoSummaryEnabled,
                summaryTriggerMessageCount = AppModel.summaryTriggerMessageCount,
                summaryMaxMessagesPerRequest = AppModel.summaryMaxMessagesPerRequest,
                summaryResponseTokens = AppModel.summaryResponseTokens,
                summaryInjectionTemplate = AppModel.summaryInjectionTemplate,
                summaryInjectionPosition = AppModel.summaryInjectionPosition,
                summaryInjectionDepth = AppModel.summaryInjectionDepth,
                summaryInjectionRole = AppModel.summaryInjectionRole,
                worldInfoBudgetPercent = AppModel.worldInfoBudgetPercent,
                worldInfoBudgetCap = AppModel.worldInfoBudgetCap,
                worldInfoOverflowAlert = AppModel.worldInfoOverflowAlert,
                contextTrimmingAlert = AppModel.contextTrimmingAlert,
                exampleDialogueBehavior = AppModel.exampleDialogueBehavior,
                includeThinkInContext = AppModel.includeThinkInContext,
                debugModeEnabled = AppModel.debugModeEnabled,
                autoGenerateImageAfterReply = AppModel.autoGenerateImageAfterReply
            )
        }
    }
}
