package me.kafuuneko.rpclient.feature.worldbooklist

import android.os.Bundle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.worldbookedit.WorldBookEditActivity
import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListDialogState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListLoadState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.character.CharacterBookImport
import me.kafuuneko.rpclient.libs.character.LorebookImportPolicy
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 世界书列表页状态持有者，协调条目数聚合、编辑导航及文件导入导出。 */
class WorldBookListViewModel : CoreViewModelWithEvent<WorldBookListUiIntent, WorldBookListUiState>(
    WorldBookListUiState.None
), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()
    private var mTransferJob: Job? = null
    private var mTransferToken: Any? = null
    private var mRefreshGeneration: Long = 0L
    private var mPendingImport: CharacterBookImport? = null

    @UiIntentObserver(WorldBookListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<WorldBookListUiState.None>()) return
        WorldBookListUiState.Normal(loadState = WorldBookListLoadState.Loading).setup()
        refreshLorebooks()
    }

    @UiIntentObserver(WorldBookListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<WorldBookListUiState.Normal>()) return
        // Activity Result 先于 onResume 交付；导入或导出任务负责结束 Loading。
        if (mTransferJob?.isActive == true) return
        refreshLorebooks()
    }

    @UiIntentObserver(WorldBookListUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<WorldBookListUiState.Finished>()) return
        mRefreshGeneration++
        mTransferJob?.cancel()
        mPendingImport = null
        WorldBookListUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(WorldBookListUiIntent.CreateWorldBook::class)
    private fun onCreateWorldBook() {
        if (!isStateOf<WorldBookListUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookEditActivity::class.java).tryEmit()
    }

    @UiIntentObserver(WorldBookListUiIntent.EditWorldBook::class)
    private fun onEditWorldBook(intent: WorldBookListUiIntent.EditWorldBook) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.lorebooks.none { it.id == intent.lorebookId }) return
        AppViewEvent.StartActivity(
            activity = WorldBookEditActivity::class.java,
            extras = Bundle().apply {
                putLong(WorldBookEditActivity.EXTRA_LOREBOOK_ID, intent.lorebookId)
            }
        ).tryEmit()
    }

    private suspend fun refreshLorebooks() {
        if (!isStateOf<WorldBookListUiState.Normal>()) return
        val generation = ++mRefreshGeneration
        val items = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks().map { lorebook ->
                WorldBookListItem.from(
                    lorebook = lorebook,
                    entryCount = mLorebookRepository.getEntriesByLorebookId(lorebook.id).size
                )
            }
        }
        if (generation != mRefreshGeneration) return
        val current = getOrNull<WorldBookListUiState.Normal>() ?: return
        current.copy(
            loadState = WorldBookListLoadState.None,
            lorebooks = items
        ).setup()
    }

    @UiIntentObserver(WorldBookListUiIntent.ImportWorldBookClick::class)
    private fun onImportWorldBookClick() {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.dialogState != WorldBookListDialogState.None) return
        WorldBookListViewEvent.OpenWorldBookImporter.tryEmit()
    }

    /**
     * 解析独立世界书，并在低固定预算需要确认时暂存导入草稿。
     *
     * 确认前不写数据库；任务 token 只允许当前导入或导出任务结束 Loading，
     * 避免 Activity Result 与 onResume 的刷新顺序造成状态闪回。
     */
    @UiIntentObserver(WorldBookListUiIntent.ImportWorldBook::class)
    private fun onImportWorldBook(intent: WorldBookListUiIntent.ImportWorldBook) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.loadState != WorldBookListLoadState.None || mTransferJob?.isActive == true) return
        val token = Any()
        mTransferToken = token
        uiState.copy(loadState = WorldBookListLoadState.Loading).setup()
        mTransferJob = viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    mLorebookRepository.readImportFromUri(intent.uri)
                }
                if (LorebookImportPolicy.requiresLowBudgetConfirmation(parsed)) {
                    mPendingImport = parsed
                    getOrNull<WorldBookListUiState.Normal>()?.copy(
                        loadState = WorldBookListLoadState.None,
                        dialogState = WorldBookListDialogState.LowTokenBudgetConfirm(
                            importedTokenBudget = parsed.lorebook.tokenBudget
                        )
                    )?.setup()
                } else {
                    saveImport(parsed)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.import_world_book_failed).tryEmit()
                refreshLorebooks()
            } finally {
                finishTransfer(token)
            }
        }
    }

    @UiIntentObserver(WorldBookListUiIntent.ImportWithGlobalBudget::class)
    private fun onImportWithGlobalBudget() {
        continuePendingImport(followGlobal = true)
    }

    @UiIntentObserver(WorldBookListUiIntent.ImportWithOriginalBudget::class)
    private fun onImportWithOriginalBudget() {
        continuePendingImport(followGlobal = false)
    }

    /** 消费一次待确认世界书，并按用户选择保留固定预算或改为跟随全局预算。 */
    private fun continuePendingImport(followGlobal: Boolean) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.dialogState !is WorldBookListDialogState.LowTokenBudgetConfirm) return
        val parsed = mPendingImport ?: return
        mPendingImport = null
        val token = Any()
        mTransferToken = token
        uiState.copy(
            loadState = WorldBookListLoadState.Loading,
            dialogState = WorldBookListDialogState.None
        ).setup()
        mTransferJob = viewModelScope.launch {
            try {
                saveImport(LorebookImportPolicy.resolveBudget(parsed, followGlobal))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.import_world_book_failed).tryEmit()
                refreshLorebooks()
            } finally {
                finishTransfer(token)
            }
        }
    }

    private suspend fun saveImport(parsed: CharacterBookImport) {
        withContext(Dispatchers.IO) { mLorebookRepository.saveImport(parsed) }
        AppViewEvent.PopupToastMessageByResId(R.string.import_world_book_success).tryEmit()
        refreshLorebooks()
    }

    @UiIntentObserver(WorldBookListUiIntent.ExportWorldBookClick::class)
    private fun onExportWorldBookClick(intent: WorldBookListUiIntent.ExportWorldBookClick) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        val lorebook = uiState.lorebooks.firstOrNull { it.id == intent.lorebookId } ?: return
        WorldBookListViewEvent.OpenWorldBookExporter(
            lorebookId = intent.lorebookId,
            fileName = "${lorebook.name.ifBlank { "worldbook" }}.json"
        ).tryEmit()
    }

    @UiIntentObserver(WorldBookListUiIntent.ExportWorldBook::class)
    private fun onExportWorldBook(intent: WorldBookListUiIntent.ExportWorldBook) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.loadState != WorldBookListLoadState.None || mTransferJob?.isActive == true) return
        val token = Any()
        mTransferToken = token
        uiState.copy(loadState = WorldBookListLoadState.Loading).setup()
        mTransferJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    mLorebookRepository.exportToUri(intent.lorebookId, intent.uri)
                }
                AppViewEvent.PopupToastMessageByResId(R.string.export_world_book_success).tryEmit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.export_world_book_failed).tryEmit()
            } finally {
                finishTransfer(token)
            }
        }
    }

    /** 仅由当前传输任务清理 Loading；页面结束后不再发布普通状态。 */
    private fun finishTransfer(token: Any) {
        if (mTransferToken !== token) return
        mTransferToken = null
        mTransferJob = null
        val current = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (current.loadState == WorldBookListLoadState.Loading) {
            current.copy(loadState = WorldBookListLoadState.None).setup()
        }
    }
}
