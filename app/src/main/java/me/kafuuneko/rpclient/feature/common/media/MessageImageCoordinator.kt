package me.kafuuneko.rpclient.feature.common.media

import androidx.compose.ui.graphics.asImageBitmap
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.room.model.MessageImageInput
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.FileRepository

/**
 * 每个聊天 ViewModel 独占的图片交互协调器。
 * - 草稿和选择器状态只驻留内存，跟随所属 ViewModel，不做跨进程恢复。
 * - IO 委托图片服务，状态发布回调只由 ViewModel 更新状态树。
 */
class MessageImageCoordinator(
    private val mRuntime: MessageImageRuntime,
    private val mFiles: FileRepository,
    private val mChanged: (MessageImageState) -> Unit
) {
    private val mOwner = UUID.randomUUID().toString()
    private val mPrepared = mutableMapOf<String, PreparedFile>()
    private val mDisplayReferences = mutableMapOf<String, Int>()
    private val mLoading = mutableSetOf<String>()
    private var mPickEditing = false
    private var mSaveUuid: String? = null
    private var mEditingActive = false
    var state = MessageImageState()
        private set

    /** 页面销毁时释放仍未提交的暂存；已提交的 UUID 已从所有权集合移除。 */
    suspend fun releaseDrafts() {
        mPrepared.values.toList().forEach { mFiles.releasePrepared(it) }
        mPrepared.clear()
        mDisplayReferences.clear()
        mLoading.clear()
    }

    /** 根据用户选择的输入位置记录本次系统选择器目标。 */
    fun choose(editing: Boolean) {
        mPickEditing = editing
    }

    /** 返回草稿凭据；只允许全部图片准备完成后提交。 */
    fun draftInputs(): List<MessageImageInput.Prepared> {
        check(!state.processing) { "Images are still being processed" }
        return state.draft.map { MessageImageInput.Prepared(requireNotNull(mPrepared[it])) }
    }

    /** 返回编辑后的顺序，保留项交给消息仓库验证所有权。 */
    fun editingInputs(): List<MessageImageInput> = state.editing.map {
        mPrepared[it]?.let(MessageImageInput::Prepared) ?: MessageImageInput.Existing(it)
    }

    /** 消息已原子提交后才清空草稿，不释放已经成为消息附件的文件。 */
    fun committed() {
        state.draft.forEach { mPrepared.remove(it) }
        publish(state.copy(draft = emptyList(), errorResId = null))
    }

    /** 进入历史图文编辑；原附件不能因为取消编辑而被删除。 */
    suspend fun startEditing(ids: List<String>) {
        cancelEditing()
        mEditingActive = true
        publish(state.copy(editing = ids))
    }

    /** 编辑成功后移交新附件所有权。 */
    fun editingCommitted() {
        mEditingActive = false
        state.editing.forEach { mPrepared.remove(it) }
        publish(state.copy(editing = emptyList(), errorResId = null))
    }

    /** 取消编辑只释放本次编辑新增的暂存。 */
    suspend fun cancelEditing() {
        mEditingActive = false
        state.editing.filter { it !in state.draft }.forEach { uuid ->
            mPrepared.remove(uuid)?.let { mFiles.releasePrepared(it) }
        }
        publish(state.copy(editing = emptyList()))
    }

    /** 保存选择器启动前冻结当前原图 ID，避免返回时误存另一张。 */
    fun beginSave(): Boolean {
        val preview = state.preview ?: return false
        mSaveUuid = preview.ids.getOrNull(preview.index)
        return mSaveUuid != null
    }

    /** 处理图片行为；失败只展示受控提示，取消继续向上传播。 */
    suspend fun handle(action: MessageImageAction) {
        try {
            // 暂存、历史加载与原图保存走同一个受控资源入口，页面不直接读取文件。
            when (action) {
                is MessageImageAction.Picked -> pick(action)
                is MessageImageAction.Load -> load(action.uuid)
                is MessageImageAction.RegisterDisplay -> {
                    mDisplayReferences[action.uuid] = (mDisplayReferences[action.uuid] ?: 0) + 1
                    load(action.uuid)
                }
                is MessageImageAction.ReleaseDisplay -> {
                    val remaining = (mDisplayReferences[action.uuid] ?: 0) - 1
                    if (remaining > 0) mDisplayReferences[action.uuid] = remaining
                    else mDisplayReferences.remove(action.uuid)
                    publish(state)
                }
                is MessageImageAction.Remove -> remove(action)
                is MessageImageAction.Move -> move(action)
                is MessageImageAction.Preview -> preview(action)
                MessageImageAction.ClosePreview -> publish(state.copy(preview = null))
                is MessageImageAction.SaveResult -> mSaveUuid?.let {
                    mRuntime.save(it, action.uri, mPrepared[it])
                    mSaveUuid = null
                }
                else -> Unit
            }
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            publish(state.copy(processing = false, preview = state.preview?.copy(loading = false),
                errorResId = R.string.image_prepare_failed))
        }
    }

    /** 逐图复制并立即取得私有暂存；部分选择失败时保留之前成功的草稿。 */
    private suspend fun pick(action: MessageImageAction.Picked) {
        if (state.processing) return
        val editing = mPickEditing
        // 编辑已经结束时，迟到的选择器结果不能误加到新消息草稿。
        if (editing && !mEditingActive) {
            publish(state.copy(errorResId = R.string.image_edit_ended))
            return
        }
        val initial = if (editing) state.editing else state.draft
        require(initial.size + action.uris.size <= MessageImagePolicy.MAX_IMAGES_PER_MESSAGE) { "A message can contain at most four images" }
        publish(state.copy(processing = true, errorResId = null))
        try {
            for (uri in action.uris) {
                val prepared = mRuntime.prepare(mOwner, uri)
                val uuid = prepared.file.uuid
                mPrepared[uuid] = prepared
                publish(if (editing) state.copy(editing = state.editing + uuid) else state.copy(draft = state.draft + uuid))
                load(uuid)
            }
        } finally { publish(state.copy(processing = false)) }
    }

    /** 同一 UUID 的解码去重；null 是已完成的缺图结果，不能自动循环重试。 */
    private suspend fun load(uuid: String) {
        if (state.thumbnails.containsKey(uuid) || !mLoading.add(uuid)) return
        try {
            val bitmap = mRuntime.load(uuid, mPrepared[uuid])?.asImageBitmap()
            publish(state.copy(thumbnails = state.thumbnails + (uuid to bitmap)))
        } finally {
            mLoading.remove(uuid)
        }
    }

    /** 调整草稿中的顺序，不改变任何文件所有权。 */
    private fun move(action: MessageImageAction.Move) {
        val ids = (if (action.editing) state.editing else state.draft).toMutableList()
        val index = ids.indexOf(action.uuid)
        if (index <= 0) return
        ids.removeAt(index)
        ids.add(index - 1, action.uuid)
        publish(if (action.editing) state.copy(editing = ids) else state.copy(draft = ids))
    }

    /** 大图仅在当前查看器仍对应同一次请求时发布，避免迟到结果覆盖新图。 */
    private suspend fun preview(action: MessageImageAction.Preview) {
        val uuid = action.ids.getOrNull(action.index) ?: return
        val preview = ImagePreviewState(action.ids, action.index, sendVersion = action.sendVersion)
        publish(state.copy(preview = preview))
        val bitmap = if (action.sendVersion) mRuntime.loadSendPreview(uuid, mPrepared[uuid])
            else mRuntime.load(uuid, mPrepared[uuid], 3072)
        if (state.preview === preview) {
            publish(state.copy(preview = preview.copy(bitmap = bitmap?.asImageBitmap(), loading = false)))
        }
    }

    /** 移除草稿释放暂存；移除历史附件仅更改编辑顺序，保存时才删除。 */
    private suspend fun remove(action: MessageImageAction.Remove) {
        mPrepared.remove(action.uuid)?.let { mFiles.releasePrepared(it) }
        publish(if (action.editing) state.copy(editing = state.editing - action.uuid)
            else state.copy(draft = state.draft - action.uuid))
    }

    /**
     * 草稿、编辑和组合中的图片受到保护；64 是历史缓存回收目标。
     * 保护项超过目标时允许短时超出，解除保护后立即回收，避免可见图退回加载状态。
     */
    private fun publish(value: MessageImageState) {
        val protected = value.draft.toSet() + value.editing + mDisplayReferences.keys
        val removable = value.thumbnails.keys.filter { it !in protected }
        val excess = (value.thumbnails.size - HISTORY_CACHE_TARGET).coerceAtLeast(0)
        state = value.copy(
            thumbnails = value.thumbnails - removable.take(excess).toSet(),
            canAddDraft = value.draft.size < MessageImagePolicy.MAX_IMAGES_PER_MESSAGE,
            canAddEditing = value.editing.size < MessageImagePolicy.MAX_IMAGES_PER_MESSAGE
        )
        mChanged(state)
    }

    private companion object {
        const val HISTORY_CACHE_TARGET = 64
    }
}
