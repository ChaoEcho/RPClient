# 代码注释与 KDoc 规范

本文档确立并规范 RPClient 项目中的代码注释与 KDoc 文档标准。AI 与开发者在编写、重构或补充代码注释时，必须严格遵守本文档。

---

## 核心通用原则

- **标准中文与通俗易懂**：统一使用规范、简洁且表意清晰的中文，直接阐明代码的核心业务逻辑、约束条件与设计意图。
- **严禁使用数字序号**：文档注释与行内注释中**一律不使用** `1. xxx`、`2. xxx` 或 `// 1. xxx`、`// Step 1: xxx` 等带有数字序号的形式。
  - 多项列举统一采用 `- xxx` 短横线无序列表。
  - 行内分步注释直接使用自然描述 `// xxx`。
- **解释意图而非复述代码**：注释用于解释“为什么这么做”、约束、时序边界与特殊取舍，不写“给变量赋值”“调用方法”“返回结果”等废话。
- **隐私与安全保护**：注释中严禁包含 API Key、真实请求头、私密对话内容、真实本地绝对路径或服务商账号信息。

---

## 注释层级与结构规范

### 1. 类级别文档注释（KDoc `/** ... */`）

- **覆盖范围**：所有 `ViewModel`、`Repository`、`Manager`、`Codec`、`Builder`、`Runtime` 等核心业务类与公共工具类。
- **结构要素**：
  - 简述该类在架构中的角色定位。
  - 列出类的核心职责与调度机制（采用 `-` 列表）。
  - 说明重要的生命周期管理、状态流模型或关键设计模式（如双轨草稿、乐观锁、时序隔离、缓存策略等）。

示例：

```kotlin
/**
 * 连续正文故事编辑器（Story Editor）的 ViewModel（状态持有者与业务控制器）。
 *
 * 核心设计与职责：
 * - 双轨草稿与防抖自动保存：内存草稿与持久化内容解耦，采用乐观锁版本号防止并发冲突。
 * - 连续文本 AI 续写：支持流式调用、世界书递归扫描预算、角色卡上下文组装。
 * - 精细化撤销/重做：通过 EditHistory 记录差异与世界书时序快照。
 */
class StoryEditorViewModel : ...
```

---

### 2. 方法级别文档注释（KDoc `/** ... */`）

- **覆盖范围**：
  - 所有 `public` / `protected` 方法。
  - 所有意图响应方法（如 `@UiIntentObserver` 标注的私有响应函数）。
  - 具有复杂分支、并发要求、状态变更或算法逻辑的重要内部业务函数。
- **结构要素**：
  - 首句简明扼要说明方法的目标功能。
  - 若逻辑复杂，补充说明业务规则、时序流程、并发拦截策略或边界防护（采用 `-` 列表）。
  - 标准标注 `@param`、`@return`、`@throws`（若有）。

示例：

```kotlin
/**
 * 重新生成最后一条角色回复的核心业务逻辑。
 *
 * 规则限制与处理流程：
 * - 限制校验：当前仅允许重生成最后一条角色回复，防止破坏中间对话历史；若仅有开场白则不允许重生成。
 * - 构建 Prompt：以 [PromptGenerationMode.Regenerate] 模式构建，并显式传入待排除的消息 ID。
 * - 结果写回：将生成结果更新覆盖到已有消息记录，而不是创建新消息。
 *
 * @param sessionId 会话 ID
 */
private suspend fun regenerateLastAssistantMessage(sessionId: Long) { ... }
```

---

### 3. 方法体内部行内注释（`//`）

- **强制规则**：**代码行数大于 16 行的方法**，方法体内部必须添加分步行内注释。
- **添加时机**：标注关键逻辑节点与流程边界，包括但不限于：
  - **前置与并发检查**：输入有效性校验、并发任务拦截、状态守卫。
  - **异步与 IO 操作**：从数据库读取实体、网络请求、文件读写。
  - **核心计算与转换**：Prompt 组装、数据格式转换、正则替换、预算裁剪。
  - **持久化与状态更新**：写库事务提交、乐观锁版本递增、UI State 刷新。
  - **安全收尾与异常处理**：`NonCancellable` 下的局部生成数据挽救、占位清理、异常捕获。
- **格式要求**：直接自然描述当前逻辑步骤，**不得添加任何数字序号**。

示例：

```kotlin
@UiIntentObserver(ChatUiIntent.StartEditMessage::class)
private suspend fun onStartEditMessage(intent: ChatUiIntent.StartEditMessage) {
    val uiState = getOrNull<ChatUiState.Normal>() ?: return
    val message = uiState.conversationState.messages
        .firstOrNull { it.id == intent.messageId } ?: return
    // 流式生成中的消息禁止编辑
    if (message.isStreaming) return
    // 异步从数据库拉取未经 Display 正则修改的原始文本
    val rawContent = withContext(Dispatchers.IO) {
        val sessionId = mSessionId ?: return@withContext null
        mChatRepository.getMessagesBySessionId(sessionId)
            .firstOrNull { it.id.toString() == intent.messageId }
            ?.content
    } ?: return
    // 将 UI 切换至消息编辑状态并填入原始草稿
    uiState.copy(
        conversationState = uiState.conversationState.copy(
            editingMessageId = message.id,
            editingMessageDraft = rawContent
        )
    ).setup()
}
```

---

## 重点业务领域注释建议

- **LLM 生成与流式控制**：明确区分流式过程中的「Display 阶段正则（高频内存渲染）」与收尾阶段的「Source 阶段正则（单次落库存储）」。
- **并发与协程上下文**：标明 `Mutex` 互斥保护目的、`NonCancellable` 保护的数据安全性（如用户取消时已生成片段不丢失）。
- **世界书与时序状态**：标明世界书快照（`worldInfoStateJson`）的计算与更新时机，以及 sticky/cooldown 的推进规则。
- **数据兼容与升级迁移**：标明 Character Card V1/V2 差异、Room Migration 字段变更意图、跨存储版本升级（`AppUpgrade`）幂等性。

---

## TODO 注释规范

TODO 必须说明后续动作和触发条件，禁止写空泛占位。

- **推荐**：
  ```kotlin
  // TODO: 增加 Claude tool use 支持后，将 Anthropic 消息后处理拆出独立 adapter。
  ```
- **不推荐**：
  ```kotlin
  // TODO: 优化
  // TODO: 1. 待修复
  ```
