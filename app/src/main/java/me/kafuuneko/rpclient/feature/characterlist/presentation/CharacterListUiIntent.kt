package me.kafuuneko.rpclient.feature.characterlist.presentation

import android.net.Uri

/** 角色列表页可接收的用户意图与文件选择结果。 */
sealed class CharacterListUiIntent {
    data object Init : CharacterListUiIntent()

    data object Resume : CharacterListUiIntent()

    data object Back : CharacterListUiIntent()

    data class ChangeSearchText(val value: String) : CharacterListUiIntent()

    data class SelectCharacter(val characterId: Long) : CharacterListUiIntent()

    data class VisibleCharactersChanged(
        val characterIds: Set<Long>,
        val targetSizePx: Int
    ) : CharacterListUiIntent()

    data object CreateCharacter : CharacterListUiIntent()

    data object ImportCharacterClick : CharacterListUiIntent()

    data class ImportCharacterCards(val uris: List<Uri>) : CharacterListUiIntent()

    data object ImportCharacterWithGlobalLorebookBudget : CharacterListUiIntent()

    data object ImportCharacterWithOriginalLorebookBudget : CharacterListUiIntent()

    data object DismissDialog : CharacterListUiIntent()

    data class ExportCharacterJsonClick(val characterId: Long) : CharacterListUiIntent()

    data class CopyCharacterJson(val characterId: Long) : CharacterListUiIntent()

    data class SaveCharacterJsonFile(val characterId: Long) : CharacterListUiIntent()

    data class ExportCharacterJson(val characterId: Long, val uri: Uri) : CharacterListUiIntent()
}
