package me.kafuuneko.rpclient.feature.main.presentation

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.feature.main.model.MainSessionSelection
import me.kafuuneko.rpclient.libs.prompt.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionRole

/** 应用首页状态树，组合最近会话、全局设置和批量操作对话框。 */
sealed class MainUiState {
    data object None : MainUiState()

    data class Normal(
        val selectedPage: MainPage = MainPage.Home,
        val homeState: MainHomeState,
        val settingsState: MainSettingsState,
        val dialogState: MainDialogState = MainDialogState.None
    ) : MainUiState()

    data class Finished(val previous: MainUiState) : MainUiState()

    companion object {
        fun finished(previous: MainUiState): MainUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 首页互斥显示的确认对话框。 */
sealed class MainDialogState {
    data object None : MainDialogState()
    data class DeleteSelectedSessions(
        val count: Int
    ) : MainDialogState()

    data class EditGenerationParameter(
        val parameter: MainGenerationParameter,
        val draftValue: String
    ) : MainDialogState()

    data class ImportChatCharacterSelection(
        val title: String,
        val sourceCharacterName: String,
        val messageCount: Int,
        val query: String,
        val characters: List<MainImportCharacterItem>,
        val visibleCharacters: List<MainImportCharacterItem>,
        val selectedCharacterId: Long?,
        val isImporting: Boolean = false
    ) : MainDialogState()
}

/** 首页底部导航对应的一级页面。 */
enum class MainPage {
    Home,
    Settings
}

/** 首页会话列表、资源统计和多选状态。 */
data class MainHomeState(
    val sessionGroups: List<MainChatSessionGroup>,
    val groupChatSessions: List<MainGroupChatSessionItem> = emptyList(),
    val totalCharacters: Int,
    val totalWorldBooks: Int,
    val multiSelectMode: Boolean = false,
    val selectedSessions: Set<MainSessionSelection> = emptySet()
)

/** 全局设置页的可渲染快照，由 ViewModel 从 Kotpref 与 Provider 数据共同构建。 */
data class MainSettingsState(
    val userName: String,
    val hasUserAvatar: Boolean,
    val userAvatarImage: ImageBitmap?,
    val userDescription: String,
    val selectedProviderId: String,
    val providers: List<MainProviderItem>,
    val temperature: Float,
    val topP: Float,
    val maxTokens: Int,
    val contextTokens: Int,
    val streamEnabled: Boolean,
    val promptPostProcessingMode: PromptPostProcessingMode,
    val exampleDialogueBehavior: ExampleDialogueBehavior,
    val includeThinkInContext: Boolean,
    val worldInfoBudgetPercent: Int,
    val worldInfoBudgetCap: Int,
    val worldInfoOverflowAlert: Boolean,
    val contextTrimmingAlert: Boolean,
    val debugModeEnabled: Boolean,
    val autoSummaryEnabled: Boolean,
    val summaryTriggerMessageCount: Int,
    val summaryWordsLimit: Int,
    val summaryMaxMessagesPerRequest: Int,
    val summaryResponseTokens: Int,
    val summaryInjectionPosition: SummaryInjectionPosition,
    val summaryInjectionDepth: Int,
    val summaryInjectionRole: SummaryInjectionRole,
    val isChatImportReading: Boolean = false
)
