# you asked

message time: 2026-09-02 11:34:14

定时备份我不太感兴趣，只是提一嘴；给出剩下的详细调研方案，然后制定出可以直接给codex的需求方案和执行方案

---

# chatgpt response

这次我会把范围收窄到 **群聊体验 + 少量高收益聊天能力**，先把现状、差距、可复用点和风险拆清，再给你一份可以直接丢给 Codex 的“需求说明 + 分阶段执行方案”。定时备份我先完全拿掉，不占优先级。

目前我倾向把“下一阶段”压成 4 个互相咬合的小改动，而不是一口气做联合生成：**明确点名/回复 → 限制每轮发言人数 → 把 Auto 从无限续聊改成可控续聊 → 加带指令重生成**。这四个基本都能沿用现有群聊链路，风险远低于重写生成器。

可以。把定时备份彻底移出这轮后，我建议下一阶段不要继续“加很多模块”，而是围绕**群聊可控性、自然度、用户理解成本**做一轮完整优化。

我又顺着 RPClient 当前实现和 `talk` 的群聊链路多看了一层。现在可以比较明确地定方案了。

## 一、调研后的核心判断

RPClient 的群聊底层其实不差，现有东西已经很多：四种发言策略、Swap/Join 角色卡模式、自动续聊、自动摘要、世界书、Regex、Prompt Inspector、重生成、续写、成员静音等。

问题主要集中在三个地方。

### 1. 用户不知道“现在到底是谁会回复”

目前 `MemberRail` 始终有一个 `selectedSpeakerId`，角色卡还会高亮。

但真正发送时：

- `Manual` 才使用 `selectedSpeakerId`
- `Natural` 自己选
- `Pooled` 自己选
- `List` 全员回复

于是 UI 很容易给人一种：

> 我现在选中了这个角色，所以是不是只有他回复？

实际上 Natural 默认就是自然模式，只不过当前 UI 没把这一层表达好。

因此第一件事甚至不是换算法，而是把**“手动选择角色”和“群聊成员”两个概念解耦**。

---

### 2. Natural 有算法，但控制能力太弱

Natural 当前已经支持：

- 文本中出现角色名 → 优先激活
- talkativeness → 概率激活
- 避免连续同一人
- 无人激活 → 随机保底

所以没必要推倒重写。

缺的是：

- 真正的 `@角色`
- 回复某条消息
- 一轮最多几个人
- UI 明确告诉用户 Natural 在做什么

隔壁 Talk 最值得借鉴的并不是“一次生成多人”，而是：

> **用户明确意图优先于自动调度。**

它会看：

- @了谁
- 回复了谁
- 再决定其他人是否参与

这个非常适合迁移进 RPClient。

---

### 3. Auto Mode 是当前最明显的逻辑缺口

RPClient 当前：

```text
这一批角色回复完
↓
autoModeEnabled?
↓
SpeakerSelector 再选
↓
继续生成
↓
再选
...
```

而 Natural/Pooled/List 基本都会返回非空结果。

因此 Auto Mode 实际上没有“自然结束”的机制。

最小改法不是搞一个 AI 判断：

> “大家现在还想不想继续聊天？”

那会多一次请求、成本高、不可预测。

更合理的是：

> **自动续聊轮数**

比如：

```text
自动续聊：开启

续聊轮数：
1轮 / 2轮 / 3轮 / 持续
```

默认 2 轮。

这个比增加一个“结束判断模型”简单很多，而且用户完全能理解。

---

# 二、我建议这一轮正式做 4 个功能

优先顺序是：

| 功能 | 优先级 | 改造强度 | 效果 |
|---|---:|---:|---|
| @角色 + 成员栏语义优化 | P0 | 小~中 | 很高 |
| 回复某条角色消息 | P0 | 中 | 很高 |
| Natural 每轮发言人数限制 | P1 | 小 | 中高 |
| Auto 可控续聊轮数 | P1 | 中 | 很高 |
| 带指令重生成 | P1 | 小~中 | 很高 |

我把前两个视作一个“定向交互”功能，所以实际上还是四块。

---

# 三、功能 1：真正的群聊定向交互

## 3.1 `@角色`

用户输入：

```text
@爱丽丝 你觉得呢？
```

Natural 模式下：

> 爱丽丝必须进入本轮发言候选。

然后系统仍然可以根据活跃度，再让其他角色参与。

例如：

```text
你：@爱丽丝 你觉得呢？

爱丽丝：我觉得可以。
米娅：你居然会同意？
```

这是我们想要的。

而不是 @Alice 后：

> 随机抽到了 Bob。

---

## 3.2 不需要为 @ 增加数据库字段

这是一个重要的 complexity-review 结论。

Talk 保存了：

```text
mentions: contactId[]
```

但 RPClient 第一版没必要照搬。

因为：

```text
@角色名
```

本身就在消息正文里，历史、备份、Prompt 都天然保留。

只需要 SpeakerSelector 对显式的：

```text
@完整角色名
```

赋予最高优先级即可。

原来普通写：

```text
爱丽丝，你怎么看？
```

也继续兼容现在的名字检测。

所以：

> `@` 是“强定向”，普通名字是“弱定向”。

这个设计挺合适。

---

## 3.3 输入框增加 @ 自动完成

输入：

```text
@
```

弹：

```text
爱丽丝
米娅
莉莉
```

继续：

```text
@爱
```

只剩：

```text
爱丽丝
```

选中后：

```text
@爱丽丝 
```

不需要复杂富文本。

**TextField 继续是纯字符串。**

这是我比较坚持的一点，不要为了 @ 搞 AnnotatedString 编辑器或者富文本 Composer。

---

## 3.4 成员栏顺便改掉现在的歧义

现在 MemberRail 点击角色就是：

```text
SelectSpeaker
```

但其实只有 Manual 有意义。

建议改成：

### Manual

点击角色：

> 选择下一位发言人。

保留现在的高亮状态。

### Natural / Pooled / List

不再显示“选中角色”的视觉状态。

点击角色：

> 向输入框插入 `@角色名 `。

这样 MemberRail 一下就有意义了。

特别是手机上，比自己输入 @ 更舒服。

---

# 四、功能 2：回复某条角色消息

这个我认为甚至比 @ 更重要。

长按：

```text
Alice：
我觉得还是别去了。
```

消息菜单：

```text
复制
回复
编辑
重新生成
带指令重新生成
删除
```

点击回复后，输入框上面出现：

```text
回复 Alice
我觉得还是别去了。
×
```

然后用户输入：

```text
为什么？
```

发出去。

---

## 数据上只增加一个字段

`GroupChatMessage`：

```kotlin
val replyToMessageId: Long? = null
```

就够了。

不要增加：

- ReplyEntity
- ReplyRepository
- GroupTargetEntity
- MentionRelation
- MessageRelation 表

全部没必要。

而且不要给它做 self foreign key。

简单存 ID。

原消息不存在时：

> 回复预览显示“原消息已删除”

即可。

---

## SpeakerSelector 怎么处理回复？

如果用户回复的是 Alice 的角色消息：

```text
preferredCharacterIds = { Alice }
```

那么：

### Natural

Alice 必须参与。

其他角色仍可能加入。

### Pooled

本轮直接优先 Alice。

### Manual

依然尊重 Manual 当前明确选择的角色。

否则 Manual 就失去了含义。

### List

还是所有角色。

因为 List 的定义本来就是全员顺序发言。

这样四种模式仍然有清晰边界。

---

# 五、回复信息也必须进入 Prompt

这点不能只做 UI。

否则模型只能看到：

```text
Alice: 我觉得还是别去了。
Bob: 随便吧。
User: 为什么？
```

它不知道这个“为什么”是在问 Alice。

因此历史 Prompt 应当把回复关系轻量表示出来。

例如：

```text
User [replying to Alice: "我觉得还是别去了。"]: 为什么？
```

或者类似格式。

不需要把整条消息无限复制。

建议最多截取一个很短的 preview，比如约 120 个字符。

这样：

- 模型知道定向关系；
- Token 成本非常低；
- Prompt Inspector 也能看见。

---

# 六、功能 3：Natural 每轮最多发言人数

Talk 有：

```text
speakerLimit
```

这个思想值得借。

但不需要完整复制。

RPClient 可以加：

> 自然模式每轮最多发言

```text
1人
2人
3人
全部
```

默认：

> **2 人**

而且这个设置**只影响 Natural**。

不要影响：

- Manual：永远 1 人
- Pooled：永远 1 人
- List：按定义全员

否则四种策略之间开始互相覆盖，会越来越难解释。

---

## Natural 算法不用重写

当前：

```text
显式点名
+
talkativeness 掷骰
+
保底角色
```

保留。

只在最后增加：

```text
显式点名角色
永远保留

其余概率激活角色
最多补足到 maxSpeakers
```

例如设置最多 2 人：

```text
@Alice
```

Alice 一定在。

然后随机/活跃度再最多选 1 人。

如果用户明确：

```text
@Alice @Bob @Carol
```

那么三个都允许进入。

也就是说：

> **用户明确选择 > 发言人数上限。**

这个原则很好理解。

---

# 七、功能 4：Auto Mode 改成“可控自动续聊”

保留：

```text
autoModeEnabled
```

不要直接推翻已有字段。

增加：

```kotlin
autoModeMaxRounds
```

建议语义：

```text
1 = 自动再聊 1 轮
2 = 自动再聊 2 轮
3 = 自动再聊 3 轮
-1 = 持续
```

默认：

```text
2
```

`0` 不作为用户设置值，可以作为旧备份/异常值的 fallback：

```text
<= 0 且 != -1
→ 按默认 2 处理
```

这样旧备份缺字段时也安全。

---

## UI

```text
自动模式                      [开]

自动续聊轮数
[1轮] [2轮] [3轮] [持续]
```

关闭 Auto 时下面的选项可以隐藏或 disable。

“持续”就是保留现在 Auto Mode 的能力。

区别是：

> 以前不知情地无限聊。

变成：

> 用户明确选择持续。

---

## launchGeneration 改造

当前：

```text
while pendingSpeakers.isNotEmpty()
```

加入：

```text
autoRoundsCompleted
```

概念上：

```text
用户触发的第一批
不算自动轮

第一批完成
↓
Auto 开启
↓
自动第 1 轮
↓
自动第 2 轮
↓
达到上限
↓
停止
```

这里完全不需要增加 LLM 判断请求。

---

# 八、功能 5：带指令重新生成

这个现在实现成本其实很低。

因为 RPClient 已经有：

```text
RegenerateMessage
↓
删除该消息及之后历史
↓
找到原 speaker
↓
GroupChatGenerationMode.Regenerate
↓
重新生成
```

所以只需要增加：

```text
regenerationInstruction
```

例如用户输入：

```text
不要同意，语气强硬一些，但不要直接吵起来
```

请求末尾加入类似：

```text
【本次重生成要求】
不要同意，语气强硬一些，但不要直接吵起来。

该要求仅约束本次重新生成。
自然执行要求，不要解释、复述或提及这条指令。
```

然后仍然：

> 原角色重生成。

---

## 不要把这个指令存进聊天记录

这是一次性控制信息。

应该是：

```text
UI Dialog
↓
ViewModel
↓
GroupChatPromptContext
↓
PromptBuilder
↓
LLM
```

生成完成即消失。

不用新增数据库字段。

---

## 但 Prompt Inspector 应该看得到

这是 RPClient 相比 Talk 的优势，不能因为新功能破坏。

建议新增：

```kotlin
PromptSourceKind.RegenerationInstruction
```

这样 Inspector 能展示：

> 重生成指令

以后出问题时很好查。

---

# 九、暂时不要做联合群聊生成

这次调研后我反而更确定这一点。

Talk 是：

```text
选 A/B/C
↓
一次模型请求
↓
模型同时输出 A/B/C 多条消息
```

RPClient 是：

```text
选 A/B/C
↓
A 单独生成
↓
B 单独生成
↓
C 单独生成
```

Talk 的效果可能更自然，但 RPClient 当前很多能力都是建立在“当前 Speaker 唯一明确”上的：

- Swap Character Card
- Group Nudge
- Regex
- Trim Other Speakers
- Continue
- Regenerate
- Prompt Inspector
- 每角色 Provider
- 世界书状态
- 流式消息

现在增加联合生成，就意味着同时维护两套生成语义。

**收益暂时不足以覆盖复杂度。**

我的建议是：

> 先把现有独立生成架构做到可控、好用。

等这轮实际体验以后，如果仍然觉得：

> “几个角色之间不像真的互相聊天。”

那时候再专门做一个 `Joint Generation Experimental`。

而不是现在一起塞进去。

---

# 十、数据库改动控制在很小范围

我建议最终只新增 3 个字段：

`GroupChatSession`：

```kotlin
naturalMaxSpeakers: Int = 2
autoModeMaxRounds: Int = 2
```

`GroupChatMessage`：

```kotlin
replyToMessageId: Long? = null
```

然后：

```text
AppDatabase 6 → 7
AutoMigration 6 → 7
```

其中 Session 两个非空字段要有明确 SQL default，例如 `2`。

旧备份如果新字段缺失，要对 `0` 做运行时 default normalization，不要为了这几个字段升级完整 `.rpbackup` 容器格式。

---

# 十一、这一轮最终产品形态

做完后用户打开群聊，大概是这样：

```text
发言策略：自然
自然模式会根据 @、回复对象和角色活跃度选择最多 2 名角色参与。
```

成员栏：

```text
[Alice] [Bob] [Carol]
```

点 Alice：

```text
@Alice |
```

用户：

```text
@Alice 昨天那件事你还记得吗？
```

可能：

```text
Alice：当然记得。

Bob：你们俩又在说什么秘密？
```

长按 Alice：

```text
回复
```

用户：

```text
回复 Alice · 当然记得。
具体是什么？
```

Alice 必定参与。

然后开启：

```text
自动模式
自动续聊：2轮
```

Alice/Bob 回完以后他们最多自己继续两轮，随后自然回到等待用户输入。

这个体验比现在会清晰很多。

---

# 十二、开发顺序

我不建议 Codex 一口气乱改。

应该分 4 个 commit-sized 阶段：

1. **Speaker targeting + @ UX**
2. **Reply relation**
3. **Natural limit + bounded Auto**
4. **Guided regenerate**

每阶段都能单独构建和测试。

第一、二阶段先让群聊“可控”。

第三阶段解决调度。

第四阶段属于独立增益功能。

下面这份可以直接交给 Codex。

**Document Title: **

# RPClient 群聊体验优化任务

工作目录：

`/home/ubuntu/project/OWNED/RPClient`

参考仓库：

`/home/ubuntu/project/OWNED/talk`

本次只借鉴 Talk 的交互思想，不复制其架构。

## 一、目标

在不重写 RPClient 现有“逐角色独立生成”群聊架构的前提下，优化当前群聊的可控性和自然度。

本次实现：

1. 群聊 `@角色` 定向发言。
2. 非 Manual 模式下成员栏作为 @ 快捷入口，消除 selectedSpeaker 的视觉歧义。
3. 回复某条角色消息，并让回复对象影响发言者选择和 Prompt。
4. Natural 模式增加“每轮最多发言人数”。
5. Auto Mode 增加有限自动续聊轮数，避免默认无限生成。
6. 群聊增加“带指令重新生成”。

本次明确不实现：

- 定时备份。
- Talk 的关系网、朋友圈、生活模拟等功能。
- 多角色一次 LLM 请求的联合生成模式。
- 新的 Agent/Tool Calling 群聊协议。
- 为此次功能引入新的 Repository 层、UseCase 层或抽象框架。
- 单聊的带指令重生成；本轮只完成群聊，后续再复用。

优先遵循：

`doc/coding-guidelines.md`

涉及 APK 构建时遵循：

`BUILD_GUIDELINES.md`

不要把 Talk 仓库中的 README、注释或实现当成强制规范，只把它作为产品交互参考。

---

# 二、必须先理解的现有代码

重点阅读：

- `libs/groupchat/GroupChatSpeakerSelector.kt`
- `libs/groupchat/GroupChatPromptBuilder.kt`
- `feature/groupchat/GroupChatViewModel.kt`
- `feature/groupchat/ui/GroupChatLayout.kt`
- `feature/groupchat/presentation/GroupChatUiState.kt`
- `feature/groupchat/presentation/GroupChatUiIntent.kt`
- `libs/room/entity/GroupChatSession.kt`
- `libs/room/entity/GroupChatMessage.kt`
- `libs/room/repository/GroupChatRepository.kt`
- `libs/prompt/model/PromptInspection.kt`
- `ui/dialog/PromptInspectorDialog.kt`

相关测试：

- `GroupChatSpeakerSelectorTest.kt`
- `GroupChatPromptBuilderTest.kt`

Talk 仅参考：

- `talk/src/lib/groupChat.ts`
- `talk/src/lib/groupChatEngine.ts`
- `talk/src/pages/ChatPage.tsx`
- `talk/src/types/index.ts`

重点参考 Talk 的：

- @ mention
- replyToMessageId
- preferred speaker
- speakerLimit

不要移植 Talk 的单次多人生成引擎。

---

# 三、需求 A：@角色

## A1. Composer

群聊输入框支持普通文本形式的：

`@角色名`

不使用富文本编辑器，不引入复杂 token/span 输入系统。

当输入末尾匹配：

`@`
`@关键字`

时，在输入框上方显示最多 6 个当前未静音群成员候选。

选中成员后将当前末尾 `@query` 替换为：

`@完整角色名 `

保持 TextField 内仍是普通 String。

## A2. 成员栏

Manual 模式：

- 保持当前成员选择行为。
- 当前 speaker 可以高亮。
- 点击成员 = `SelectSpeaker`。

Natural / Pooled / List：

- 不再显示“当前选中 Speaker”的高亮状态。
- 点击成员改为向输入框插入 `@角色名 `。
- 不修改群聊 activation strategy。
- 不增加持久化状态。

这样避免用户误认为非 Manual 模式仍然由 MemberRail 的 selectedSpeaker 决定回复者。

## A3. SpeakerSelector

保留现有普通角色名检测兼容行为。

额外增加明确 `@完整角色名` 的强匹配。

优先级原则：

显式用户意图 > 自动调度。

策略行为：

### Manual

继续只尊重 `manualCharacterId`。

@ 不改变 Manual 的发言者选择语义。

### Natural

显式 @ 的未静音角色必须进入本轮 speaker 列表。

之后才依据现有 talkativeness 逻辑选择其他角色。

### Pooled

如果用户显式 @ 了有效角色，本轮优先选择被 @ 的角色。

如果没有显式目标，维持原 pooled 算法。

### List

保持所有未静音成员依序回复，不因 @ 缩减成员。

不要删除现有普通名字匹配逻辑。

---

# 四、需求 B：回复消息

## B1. 数据结构

`GroupChatMessage` 增加：

`replyToMessageId: Long? = null`

不要：

- 建 ReplyEntity。
- 建新的关系表。
- 建 self foreign key。
- 为该字段增加不必要索引。

数据库版本从 6 升到 7，优先使用 Room AutoMigration。

## B2. UI

角色消息长按菜单增加：

`回复`

点击后进入 composer reply 状态。

输入框上方显示紧凑 reply preview：

- 原发言角色名
- 原消息正文短预览
- 取消按钮

预览不需要复杂卡片。

用户发送后：

- 当前 User GroupChatMessage 保存 `replyToMessageId`。
- 清除 composer reply 状态。

若被回复消息已经不存在：

- UI 不崩溃。
- 可以显示“原消息已删除”或忽略 preview。

## B3. Speaker 调度

发送消息前读取 reply target。

若回复目标是 Character 消息，则得到该消息的 `speakerCharacterId`。

策略：

- Natural：回复目标必须参与。
- Pooled：回复目标优先作为本轮 speaker。
- Manual：仍尊重手动选择 speaker。
- List：仍全员参与。

@ 与 reply 同时存在时：

- 两者都是用户明确意图。
- Natural 中合并目标集合并去重。
- 不要因为自动发言人数上限丢弃显式目标。

## B4. Prompt

回复关系不能只存在 UI。

构建群聊历史 Prompt 时，对于带 `replyToMessageId` 的 User 消息，加入轻量 reply context。

示意：

`User [replying to Alice: "原消息短预览"]: 为什么？`

具体格式可按现有 Prompt 风格调整，但必须满足：

- 模型能明确知道回复的是谁。
- 包含一小段原消息预览。
- 对原文做长度限制，避免重复整条超长消息。
- 原消息不存在时正常退化成普通 User 消息。
- Regex 对用户实际正文的处理行为不要被破坏。

不要创建独立 LLM 请求来解析回复关系。

---

# 五、需求 C：Natural 每轮发言人数

`GroupChatSession` 增加：

`naturalMaxSpeakers: Int = 2`

数据库字段 SQL 默认值为 2。

推荐语义：

- `1` = 最多 1 人
- `2` = 最多 2 人
- `3` = 最多 3 人
- `-1` = 全部
- `0` 或其他非法值 = 运行时按默认 2 处理，用于旧备份兼容

设置页：

`自然模式每轮最多发言`

选项：

`1人 / 2人 / 3人 / 全部`

仅在 Natural 模式下展示或 enable。

该设置只作用于 Natural。

Manual / Pooled / List 的原始语义保持不变。

Natural 的处理顺序：

1. 过滤 muted。
2. 识别 reply / @ 等显式目标。
3. 显式目标全部保留。
4. 对剩余成员执行当前 talkativeness 激活算法。
5. 仅对非显式目标应用 `naturalMaxSpeakers` 上限。
6. 若无任何成员，维持现有 fallback 行为。

如果显式 @ 了 3 人，而上限为 2：

3 个显式目标全部保留。

用户明确意图优先。

不要在这一任务中重新设计 talkativeness 算法。

---

# 六、需求 D：Auto Mode 有限续聊

保留现有：

`autoModeEnabled`

新增：

`autoModeMaxRounds: Int = 2`

SQL 默认值为 2。

语义：

- `1` = 第一批回复结束后自动追加最多 1 轮
- `2` = 自动追加最多 2 轮
- `3` = 自动追加最多 3 轮
- `-1` = 持续模式
- `0` 或非法值 = 默认按 2 处理

设置 UI：

`自动模式 [开关]`

开启后：

`自动续聊轮数`

`1轮 / 2轮 / 3轮 / 持续`

默认 2 轮。

关闭时无需运行任何自动续聊。

改造 `launchGeneration()`：

- 用户最初触发的 speaker batch 不算 auto round。
- 第一批结束后才开始计 auto round。
- 每次调用 SpeakerSelector 得到下一批即计一轮。
- 达到 `autoModeMaxRounds` 后停止。
- `-1` 保留当前持续自动模式行为。
- Manual 仍不自动续聊。
- 用户 StopGeneration 必须继续立即停止。
- 不增加额外 LLM 请求来判断“是否该结束”。

特别注意不要产生 off-by-one。

例如配置 2：

`用户触发批次 -> 自动第1轮 -> 自动第2轮 -> 停止`

---

# 七、需求 E：带指令重新生成

保留现有：

`RegenerateMessage`

行为。

角色消息菜单增加：

`带指令重新生成`

点击后弹出简单多行文本 Dialog：

标题：

`带指令重新生成`

输入提示：

`例如：语气强硬一些，但不要直接吵起来`

确认后：

- 使用原消息 speaker。
- 仍然从目标消息开始删除后续历史。
- 使用 `GroupChatGenerationMode.Regenerate`。
- 将本次 instruction 作为一次性参数传入生成管线。
- instruction 不写进 GroupChatMessage。
- instruction 不保存到 Session。
- 生成完成即释放。

建议：

`GroupChatPromptContext`

新增：

`regenerationInstruction: String = ""`

在 Regenerate 且 instruction 非空时，在请求末尾附近加入不可轻易被预算裁剪的 user 控制消息。

语义类似：

`【本次重生成要求】`
`<用户输入>`
`仅自然执行该要求，不要解释、复述或提及这条指令。`

不要复用永久的 groupNudgeOverride。

新增：

`PromptSourceKind.RegenerationInstruction`

并在 Prompt Inspector 中提供可识别名称，使开发者能够确认本次指令实际进入了最终请求。

普通“重新生成”继续保持当前行为，不注入该 instruction。

---

# 八、数据库与兼容

数据库：

`AppDatabase version 6 -> 7`

增加：

- `GroupChatSession.naturalMaxSpeakers`
- `GroupChatSession.autoModeMaxRounds`
- `GroupChatMessage.replyToMessageId`

两个非空 Int 字段必须有数据库默认值 2，确保 AutoMigration 可行。

旧数据必须正常加载。

完整备份不要因为此次字段增加就重做格式。

特别考虑旧 `.rpbackup` 中缺少新字段时 Gson 反序列化可能得到 `0`：

业务读取时将：

`naturalMaxSpeakers == 0`
`autoModeMaxRounds == 0`

归一化为默认 2。

不要为了这个需求增加 Backup V2。

确保新增字段可以随现有 JSONL 实体导出自然进入新备份。

---

# 九、UI 小优化

群聊设置页当前只有 activation strategy chips。

在 chips 下增加当前策略简介，例如：

- Manual：每轮由你明确选择发言角色。
- Natural：根据 @、回复对象、角色名和活跃度选择参与回复的成员。
- List：所有活跃成员依次回复。
- Pooled：每轮选择一名角色，并优先选择尚未发言或被明确指向的成员。

尽量复用已有 `descriptionRes()`。

不要进行群聊设置页整体视觉重构。

---

# 十、测试要求

只测试高逻辑密度部分，不写仪式性 ViewModel/UI 单测。

重点扩展：

`GroupChatSpeakerSelectorTest`

至少验证：

1. Natural 显式 @ 的成员始终进入结果。
2. CJK @ 名称可识别。
3. 多词角色名 @ 可识别。
4. Natural maxSpeakers 能限制普通概率成员。
5. 显式目标不会被 maxSpeakers 丢弃。
6. Pooled 有回复/@目标时优先目标。
7. Manual 不被 @ 覆盖。
8. List 保持全员未静音成员。

扩展 `GroupChatPromptBuilderTest`：

至少验证：

1. replyToMessageId 能在 Prompt 历史中产生明确 reply context。
2. 被回复消息不存在时不会失败。
3. Regenerate instruction 只在有 instruction 时注入。
4. 普通 Regenerate 不出现该 instruction。
5. Prompt source 能识别为 `RegenerationInstruction`。

Auto round 如果能提取出非平凡的纯调度函数则测试边界：

- 1 轮
- 2 轮
- continuous
- disabled

如果只能测试简单 `counter < limit`，不要为了 checklist 人为创建无价值单测。

---

# 十一、验收场景

## Scenario 1：Natural + @

群成员 Alice/Bob/Carol。

Natural，最多 2 人。

输入：

`@Alice 你怎么看？`

必须：

- Alice 一定回复。
- 最多再有 1 个自动成员。
- 不允许 Alice 因随机概率落选。

## Scenario 2：成员栏

Natural 模式点击 Alice：

输入框插入：

`@Alice `

Alice 卡片不得以“当前手动 Speaker”的形式永久高亮。

Manual 模式点击 Alice：

继续使用原有 speaker selection。

## Scenario 3：回复

Bob 说：

`我觉得不应该去。`

用户长按 Bob -> 回复 -> 输入：

`为什么？`

必须：

- User 消息保存 replyToMessageId。
- Composer 发出后清除 reply 状态。
- Natural/Pooled 下 Bob 被优先激活。
- Prompt Inspector 能看到回复关系。

## Scenario 4：Natural 上限

5 个群成员，最多 2 人。

无明确 @ 时：

单轮最多生成 2 个角色。

明确 @ 三个成员时：

三个明确成员都允许生成。

## Scenario 5：Auto 2轮

开启 Auto，续聊轮数 2。

必须严格：

`初始批次 + 最多两个自动批次`

然后回到 Idle。

不能继续无限请求。

## Scenario 6：持续 Auto

选择“持续”后保留原持续行为。

Stop 按钮必须仍可立即终止。

## Scenario 7：带指令重生成

Alice 原消息：

`好吧，我答应你。`

执行带指令重生成：

`不要答应，坚持拒绝，但语气不要过于激烈。`

必须：

- 仍由 Alice 生成。
- instruction 出现在 Prompt Inspector。
- 最终用户可见聊天中不保存该 instruction。
- 普通 Regenerate 不受影响。

---

# 十二、实施顺序

严格分阶段完成，不要一次大面积修改。

## Phase 1：@ 与 MemberRail

修改：

- GroupChatSpeakerSelector
- GroupChatViewModel
- GroupChatUiState / Intent
- GroupChatLayout
- strings
- SpeakerSelector tests

完成后先运行相关 unit tests。

## Phase 2：Reply

修改：

- GroupChatMessage
- AppDatabase migration
- GroupChat UI state / intent
- ViewModel
- Message actions / Composer
- GroupChatPromptBuilder
- PromptBuilder tests

确认旧数据库 migration 可以成功。

## Phase 3：Natural limit + Auto rounds

修改：

- GroupChatSession
- settings state / intents / UI
- SpeakerSelector
- launchGeneration
- migration schema
- tests

重点检查 auto round off-by-one。

## Phase 4：Guided Regenerate

修改：

- message actions
- dialog state
- GroupChatUiIntent
- ViewModel
- GroupChatPromptContext
- GroupChatPromptBuilder
- PromptSourceKind
- PromptInspector label
- PromptBuilder tests

不要顺手扩展到单聊。

---

# 十三、实现约束

遵循 minimal engineering：

- 优先修改现有类。
- 不新增无必要 manager/service/usecase。
- 不引入新第三方依赖。
- 不重写群聊生成架构。
- 不修改单聊生成链。
- 不修改备份功能设计。
- 不顺手重构无关代码。
- 不因为新增 3 个字段创建新的 domain abstraction。
- 不把 Talk 的 relation/social graph 搬进 RPClient。
- 不实现 AI 自动判断群聊是否结束。
- 不实现一次请求多人输出。

所有新增 public/protected 方法和复杂业务函数遵循项目 KDoc 规范。

字符串至少完整提供：

- 默认 `values/strings.xml`
- `values-b+zh+Hans/strings.xml`

其他 locale 若本任务没有可靠翻译，不要生成质量可疑的机器翻译；允许暂时 fallback 到默认英文。

---

# 十四、验证

先运行针对性单测，例如相关 GroupChat tests。

然后至少运行：

`:app:testDebugUnitTest`

最终需要 APK 验证时按照 `BUILD_GUIDELINES.md`：

使用 JDK 21、指定 AAPT2 override，并执行 clean debug build。

不要省略 clean 后直接把异常小体积 APK 当成成功产物。

完成后输出：

1. 修改文件列表。
2. 每阶段具体做了什么。
3. 数据库 migration 说明。
4. 实际运行的测试及结果。
5. 是否存在未解决风险。
6. 明确说明没有实现联合多人生成和其他超范围功能。

我会建议实际交给 Codex 时就按这份做，**不要再额外附一句“顺便把群聊整体重构一下”**。这一轮的价值就在于：在现有架构上把最影响体验的几个控制点补齐，同时为以后是否做联合生成留出观察期。