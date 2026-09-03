package me.kafuuneko.rpclient.feature.main.presentation

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole

/** 全局设置页状态树，各子状态与设置页的可渲染面板一一对应。 */
data class MainSettingsState(
    val identityState: MainUserIdentityState,
    val providerState: MainProviderSettingsState,
    // 当前图片服务的一行摘要；图片配置在数据库里，Composable 读不到，必须由 VM 预先算好。
    val imageProviderSummary: String,
    val promptBehaviorState: MainPromptBehaviorState,
    val worldInfoBudgetState: MainWorldInfoBudgetState,
    val summaryState: MainSummarySettingsState
)

/** 用户名称、描述和头像面板状态。 */
data class MainUserIdentityState(
    val userName: String,
    val userDescription: String,
    val userDescriptionPreview: String,
    val avatarState: MainUserAvatarState
)

/**
 * 用户头像的配置状态。
 *
 * [Configured.image] 允许为空，以保留“头像已配置但文件暂时无法解码”时的清除入口。
 */
sealed class MainUserAvatarState {
    data object None : MainUserAvatarState()
    data class Configured(val image: ImageBitmap?) : MainUserAvatarState()
}

/** 模型配置面板状态；生成参数只在至少存在一个可用模型配置时进入状态树。 */
sealed class MainProviderSettingsState {
    data object Empty : MainProviderSettingsState()

    data class Available(
        val selectedProviderId: Long,
        val providers: List<MainProviderItem>
    ) : MainProviderSettingsState()
}

/** Prompt 行为面板状态。 */
data class MainPromptBehaviorState(
    val exampleDialogueBehavior: ExampleDialogueBehavior,
    val includeThinkInContext: Boolean,
    val contextTrimmingAlert: Boolean,
    val streamEnabled: Boolean
)

/** 世界书 Prompt 预算面板状态。 */
data class MainWorldInfoBudgetState(
    val budgetPercent: Int,
    val budgetCap: Int,
    val overflowAlert: Boolean
)

/** 通用摘要参数与对话摘要行为面板状态。 */
data class MainSummarySettingsState(
    val selectedProviderId: Long = 0L,
    val providers: List<MainProviderItem> = emptyList(),
    val autoSummaryEnabled: Boolean,
    val triggerMessageCount: Int,
    val wordsLimit: Int,
    val maxMessagesPerRequest: Int,
    val responseTokens: Int,
    val injectionState: MainSummaryInjectionState
)

/** 摘要注入位置；只有聊天历史内注入需要额外的深度和角色。 */
sealed class MainSummaryInjectionState(
    val position: SummaryInjectionPosition
) {
    data object None : MainSummaryInjectionState(SummaryInjectionPosition.None)
    data object BeforeMain : MainSummaryInjectionState(SummaryInjectionPosition.BeforeMain)
    data object AfterMain : MainSummaryInjectionState(SummaryInjectionPosition.AfterMain)

    data class InChat(
        val depth: Int,
        val role: SummaryInjectionRole
    ) : MainSummaryInjectionState(SummaryInjectionPosition.InChat)
}

internal fun SummaryInjectionPosition.toMainSummaryInjectionState(
    depth: Int,
    role: SummaryInjectionRole
): MainSummaryInjectionState {
    return when (this) {
        SummaryInjectionPosition.None -> MainSummaryInjectionState.None
        SummaryInjectionPosition.BeforeMain -> MainSummaryInjectionState.BeforeMain
        SummaryInjectionPosition.AfterMain -> MainSummaryInjectionState.AfterMain
        SummaryInjectionPosition.InChat -> MainSummaryInjectionState.InChat(
            depth = depth,
            role = role
        )
    }
}

