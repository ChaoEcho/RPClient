package me.kafuuneko.rpclient.feature.characteredit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditViewEvent
import me.kafuuneko.rpclient.feature.characteredit.ui.CharacterEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 角色创建与编辑页面宿主，处理头像文件选择等系统事件。 */
class CharacterEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<CharacterEditViewModel>()

    /** 头像 URI 只作为一次性选择结果交回 ViewModel，文件复制由数据层完成。 */
    private val mAvatarPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { mViewModel.emit(CharacterEditUiIntent.AvatarSelected(it)) }
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is CharacterEditUiState.Finished) finish()
        }

        CharacterEditLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val characterId = intent.getLongExtra(EXTRA_CHARACTER_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(CharacterEditUiIntent.Init(characterId))
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(CharacterEditUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        when (viewEvent) {
            CharacterEditViewEvent.OpenAvatarPicker -> mAvatarPickerLauncher.launch("image/*")
            is CharacterEditViewEvent.CopyText -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.character), viewEvent.text)
                )
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_CHARACTER_ID = "extra_character_id"
    }
}
