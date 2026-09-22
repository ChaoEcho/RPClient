package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainHomeContentTab
import me.kafuuneko.rpclient.feature.main.model.ImageSendField
import me.kafuuneko.rpclient.libs.media.ImageSendMode
import me.kafuuneko.rpclient.libs.theme.AppThemeMode
import android.net.Uri
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole

/** 首页及全局设置页可接收的全部用户意图。 */
sealed class MainUiIntent {
    data class SelectThemeMode(val themeMode: AppThemeMode) : MainUiIntent()
    data object OpenTokenUsage : MainUiIntent()
    data class SelectImageSendMode(val mode: ImageSendMode) : MainUiIntent()
    data class ChangeImageSendLimit(val field: ImageSendField, val value: String) : MainUiIntent()
    data object SaveImageSendLimits : MainUiIntent()

    data object Init : MainUiIntent()

    data object Resume : MainUiIntent()

    data object Back : MainUiIntent()

    data class SelectPage(val page: MainPage) : MainUiIntent()

    data class SelectHomeContentTab(val tab: MainHomeContentTab) : MainUiIntent()

    data class OpenChat(val sessionId: String) : MainUiIntent()

    data object OpenCreateChat : MainUiIntent()

    data class OpenGroupChat(val sessionId: String) : MainUiIntent()

    data object OpenCreateGroupChat : MainUiIntent()

    data class OpenStory(val storyId: Long) : MainUiIntent()

    data object OpenCreateStory : MainUiIntent()

    data class ShowRenameItemDialog(val item: MainHomeItemSelection) : MainUiIntent()

    data class ChangeItemTitleDraft(val value: String) : MainUiIntent()

    data object ConfirmItemRename : MainUiIntent()

    data object OpenCharacterManager : MainUiIntent()

    data object OpenWorldBookManager : MainUiIntent()

    data object OpenProviderManager : MainUiIntent()





    data object PickUserAvatarClick : MainUiIntent()

    data class UserAvatarCropped(val fileUuid: String) : MainUiIntent()

    data object ClearUserAvatar : MainUiIntent()






    data class ChangeUserName(val value: String) : MainUiIntent()

    data class ChangeUserDescription(val value: String) : MainUiIntent()

    data object ShowUserDescriptionEditor : MainUiIntent()

    data class ChangeUserDescriptionEditorDraft(val value: String) : MainUiIntent()

    data object ConfirmUserDescriptionEditor : MainUiIntent()





















    data object OpenPromptPreset : MainUiIntent()

    data object OpenPromptBehaviorSettings : MainUiIntent()


    data object OpenSummaryMemorySettings : MainUiIntent()

    data object OpenDeveloperSettings : MainUiIntent()

    data object OpenRegexScripts : MainUiIntent()


    data object OpenBackup : MainUiIntent()

    data object OpenImageProviderList : MainUiIntent()

    data object OpenTtsSettings : MainUiIntent()

    data object OpenAbout : MainUiIntent()

    data class EnterMultiSelect(val item: MainHomeItemSelection) : MainUiIntent()

    data class ToggleItemSelection(val item: MainHomeItemSelection) : MainUiIntent()

    data class ToggleSessionGroup(val characterId: String) : MainUiIntent()

    data object ExitMultiSelect : MainUiIntent()

    data object ShowDeleteSelectedDialog : MainUiIntent()

    data object ConfirmDeleteSelected : MainUiIntent()

    data object DismissDialog : MainUiIntent()
}
