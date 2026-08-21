package me.kafuuneko.rpclient.feature.jsonviewer

import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerEntry
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerNodeType
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerErrorReason
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiIntent
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiState
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.utils.toPreview
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * JSON 树查看器状态持有者。
 *
 * 核心职责：
 * - 解析并维护内存中的 JSON 对象/数组语法树；
 * - 跟踪由根至当前层级的导航路径栈（Path Stack）；
 * - 仅将当前视口可见的层级节点映射为列表 UI 数据，避免大 JSON 树在重组时产生大量深拷贝开销；
 * - 支持父子层级间逐级下钻浏览与逐级返回。
 */
class JsonViewerViewModel : CoreViewModel<JsonViewerUiIntent, JsonViewerUiState>(
    JsonViewerUiState.None
) {
    /** 当前载荷标题、根节点与从根到当前节点的导航路径。 */
    private var mTitle: String = ""
    private var mRoot: Any? = null
    private var mPath: List<JsonPathStep> = emptyList()

    /** 从载荷存储区拉取 JSON 原文并进行语法解析与初始展示。 */
    @UiIntentObserver(JsonViewerUiIntent.Init::class)
    private fun onInit(intent: JsonViewerUiIntent.Init) {
        if (!isStateOf<JsonViewerUiState.None>()) return

        // 从内存暂存区检索 Payload
        val payload = JsonViewerPayloadStore.get(intent.payloadKey)
        if (payload == null) {
            JsonViewerUiState.Error(
                title = "",
                reason = JsonViewerErrorReason.PayloadUnavailable,
                rawPreview = ""
            ).setup()
            return
        }

        mTitle = payload.title
        // 解析 JSON 字符串
        val parsed = parseJson(payload.json)
        if (parsed.isFailure) {
            JsonViewerUiState.Error(
                title = mTitle,
                reason = JsonViewerErrorReason.InvalidJson,
                rawPreview = payload.json.toPreview()
            ).setup()
            return
        }

        // 保存根节点对象并构建顶层视图
        mRoot = parsed.getOrNull()
        mPath = emptyList()
        buildNormalState().setup()
    }

    /** 处理返回操作，若处于子层级则返回上一层，处于根层级则退出查看器。 */
    @UiIntentObserver(JsonViewerUiIntent.Back::class)
    private fun onBack() {
        if (getOrNull<JsonViewerUiState.Normal>()?.canNavigateUp == true) {
            mPath = mPath.dropLast(1)
            buildNormalState().setup()
            return
        }
        JsonViewerUiState.finished(uiStateFlow.value).setup()
    }

    /** 选中包含子节点的条目，下钻进入其下属层级。 */
    @UiIntentObserver(JsonViewerUiIntent.EntrySelected::class)
    private fun onEntrySelected(intent: JsonViewerUiIntent.EntrySelected) {
        val uiState = getOrNull<JsonViewerUiState.Normal>() ?: return
        val entry = uiState.entries.firstOrNull { it.id == intent.entryId } ?: return
        if (!entry.hasChildren) return

        mPath = mPath + JsonPathStep(
            label = entry.name,
            objectKey = entry.sourceKey,
            arrayIndex = entry.sourceIndex
        )
        buildNormalState().setup()
    }

    /** 使用 JSONTokener 安全解析 JSON 字符串为 JSONObject、JSONArray 或原始基本类型。 */
    private fun parseJson(json: String): Result<Any?> {
        return runCatching {
            val tokener = JSONTokener(json)
            val value = tokener.nextValue()
            val next = tokener.nextClean()
            if (next.code != 0) error("Unexpected data after JSON value.")
            value
        }
    }

    /** 根据当前导航路径构建当前层级的 Normal 状态。 */
    private fun buildNormalState(): JsonViewerUiState.Normal {
        val current = currentNode()
        return JsonViewerUiState.Normal(
            title = mTitle,
            path = listOf("Root") + mPath.map { it.label },
            currentType = current.nodeType(),
            childCount = current.childCount(),
            entries = current.toEntries(),
            canNavigateUp = mPath.isNotEmpty()
        )
    }

    /** 沿路径栈下钻定位当前聚焦的 JSON 节点。 */
    private fun currentNode(): Any? {
        var current = mRoot
        for (step in mPath) {
            current = when {
                step.objectKey != null && current is JSONObject -> current.opt(step.objectKey)
                step.arrayIndex != null && current is JSONArray -> current.opt(step.arrayIndex)
                else -> null
            }
        }
        return current
    }

    /** 将当前聚焦的 JSONObject 或 JSONArray 映射为子条目列表。 */
    private fun Any?.toEntries(): List<JsonViewerEntry> {
        return when (this) {
            is JSONObject -> keys().asSequence().toList().mapIndexed { index, key ->
                val value = opt(key)
                value.toEntry(
                    id = index,
                    name = key,
                    sourceKey = key,
                    sourceIndex = null
                )
            }

            is JSONArray -> (0 until length()).map { index ->
                val value = opt(index)
                value.toEntry(
                    id = index,
                    name = "[$index]",
                    sourceKey = null,
                    sourceIndex = index
                )
            }

            else -> emptyList()
        }
    }

    /** 将单个子值封装为列表项视图模型。 */
    private fun Any?.toEntry(
        id: Int,
        name: String,
        sourceKey: String?,
        sourceIndex: Int?
    ): JsonViewerEntry {
        return JsonViewerEntry(
            id = id,
            name = name,
            type = nodeType(),
            preview = valuePreview(),
            childCount = childCount(),
            sourceKey = sourceKey,
            sourceIndex = sourceIndex
        )
    }

    /** 推断 JSON 节点类型枚举。 */
    private fun Any?.nodeType(): JsonViewerNodeType {
        return when (this) {
            is JSONObject -> JsonViewerNodeType.Object
            is JSONArray -> JsonViewerNodeType.Array
            is Boolean -> JsonViewerNodeType.Boolean
            is Number -> JsonViewerNodeType.Number
            JSONObject.NULL, null -> JsonViewerNodeType.Null
            else -> JsonViewerNodeType.String
        }
    }

    /** 计算节点的直接子元素数量。 */
    private fun Any?.childCount(): Int {
        return when (this) {
            is JSONObject -> length()
            is JSONArray -> length()
            else -> 0
        }
    }

    /** 生成节点值的短文本摘要预览。 */
    private fun Any?.valuePreview(): String {
        return when (this) {
            is JSONObject -> toString().toPreview(180)
            is JSONArray -> toString().toPreview(180)
            is String -> toPreview()
            is Boolean, is Number -> toString()
            JSONObject.NULL, null -> "null"
            else -> toString().toPreview()
        }
    }

    /** JSON 导航路径栈单步描述（包含标签、对象 Key 或数组索引）。 */
    private data class JsonPathStep(
        val label: String,
        val objectKey: String?,
        val arrayIndex: Int?
    )
}
