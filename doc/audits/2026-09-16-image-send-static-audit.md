# 图片发送全流程静态代码审计

当前状态：下文记录 `4b2bc3b` 修复前的审计与复核结论，六项问题的工作区修改见 [修复记录](./2026-09-16-image-send-fixes.md)。修复后未执行测试或构建。

## 结论

在 `develop` 的 `4b2bc3b` 上，经第二轮逐项可达性复核，保留 **4 项 P2 问题、2 项 P3 问题**。Gemini 临时文件清理遗漏由 P2 降为 P3；其他五项保留，但补充了实际操作顺序和不触发的边界。其中正文与附件快照不一致、选择器结果归属、群聊历史图片保护范围应优先处理。

六项均有源码支持的应用内触发路径，未发现因必要操作被界面或业务层完全禁止而应整项撤回的问题。此前“生成过程中编辑图片”的笼统描述不足以证明第一个问题：生成期间图片修改确实会被拦截，成立的路径是**生成前已修改附件，生成时保存既有编辑**。本修订结论取代初稿的优先级和触发范围描述。

审计与复核阶段仅做源码、提交差异和调用链审查，没有运行单元测试、编译、Lint、模拟器或实机，没有调用模型服务，也没有修改业务代码。下文触发步骤是由代码推导的反例，不是本轮实测结果。

## 审计范围

- 以图片功能之前的 `93c3d52` 为比较基线，检查初始实现及截至 `4b2bc3b` 的相关修复和界面改动。
- 对照图片功能方案、实现任务表与上一轮审计及修复记录，复核当前实现。
- 覆盖系统选图、草稿归属、取消与重建、格式与尺寸处理、暂存提交、单聊／群聊发送、历史与摘要、预算与协议编码、错误与重试、图片展示与保存、编辑／删除／分支、文字导出与备份规则。
- Android 结果回调的进程重建语义另对照官方文档；未进行设备操作。

## 问题汇总

| 编号 | 复核优先级 | 问题 | 可达性及修复价值 |
| --- | --- | --- | --- |
| A | P2，保留 | 普通生成混用正文与附件快照 | 先编辑附件、再启动生成、在两次读取之间保存；可能向模型发送从未保存过的图文组合 |
| B | P2，保留 | 失去归属的选图结果进入新消息草稿 | 历史编辑选图期间进程被回收，系统仍可交付结果；需要拒绝失去目标的回调 |
| C | P2，保留 | 群聊旧用户图片仍不可裁剪 | 批次完成后旧图仍在历史窗口、预算不足；原本可裁剪的旧图阻断后续生成 |
| D | P2，保留 | 空草稿选图失败提示被隐藏 | 首张图被本地格式／大小检查拒绝即可触发；用户无法知道失败原因 |
| E | P3，降级 | Gemini 非法地址遗留请求文件 | UI 允许保存缺少 scheme 的非空地址；含图编码后解析失败，遗留本地缓存 |
| F | P3，保留 | 新消息草稿失去图片排序入口 | 两张以上草稿图无法原地调整顺序；属于已有能力回退，可通过重新选择绕过 |

复核方法：分别追踪布局控件、Intent 收集、ViewModel 门禁、Repository 事务和后续消费；检查已有保护是否先于问题发生。没有通过直接调用内部方法或修改数据库来充当真实 UI 触发步骤，也没有执行测试。并发问题仅证明交错路径可达，不声称测得发生频率。

## P2：正文与附件来自不同的数据库快照

位置：[ChatViewModel.kt:2116](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt:2116)、[GroupChatViewModel.kt:1672](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatViewModel.kt:1672)。

普通生成先取得 `generationHistory.messages`／`data.messages`，执行其他查询后，再通过 `getMessagesWithImages()` 读取附件。后一次聚合虽然同时含有正文和附件，调用方却只取图片引用，仍向 Builder 传入第一次读取的正文和摘要。

- 可达操作：在空闲状态进入一条历史用户消息的内联编辑，修改正文并增删或重排附件，等待图片处理完成，暂不保存；保持编辑框打开，从底部输入栏发送另一条消息；随后保存先前的历史编辑。群聊还可以在后续角色开始构建请求时保存。
- 必要时序：生成流程已取得旧正文，编辑事务提交新正文和新附件，然后生成流程才执行附件读取。只有保存落在这个窗口内才发生错配；单纯“编辑时发送”并不必然触发。
- 实际结果：原本数据库中先后是“正文 A＋图片 A”和“正文 B＋图片 B”，请求可能包含“正文 A＋图片 B”。若原消息是纯图、编辑后改为纯文字，第一次正文为空、第二次附件为空，Builder 还可能把这条消息作为空消息丢弃。
- 影响：发送到模型的内容不是任何一次真实保存的完整消息。文件租约及发送缓存版本校验只能保证图片资源本身，无法发现正文与附件错配。
- 建议：Repository 在同一个读取事务中返回摘要、候选正文及附件快照，生成链路全程消费该快照；若需要检测后续修改，增加版本校验并明确失败或重新构建。也应统一开始／保存编辑与生成之间的操作约束。

相关入口：[单聊开始编辑](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt:1221)、[单聊保存编辑](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt:1269)、[群聊保存编辑](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatViewModel.kt:1291)。

**排除门禁后的证据：**

1. 编辑是消息气泡中的内联控件，不是遮挡发送栏的模态弹窗。[单聊保存按钮](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ui/ChatLayout.kt:1006) 和 [群聊保存按钮](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/ui/GroupChatLayout.kt:1692) 均未绑定生成中的禁用条件。两类发送入口也未检查 `editingMessageId`。
2. 发送后的状态刷新默认保留 `editingMessageId`／`editingMessageDraft`：[单聊](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt:2347)、[群聊](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatViewModel.kt:2098)。`committed()` 只清空新消息草稿，未撤销历史编辑。
3. [CoreViewModel](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/core/CoreViewModel.kt:81) 串行处理 Intent，但生成使用独立的 `viewModelScope.launch`，Intent 收集不会等待整个生成结束。两次读取分别结束各自事务，没有覆盖整个 Prompt 构建过程的共同事务或锁。
4. 两类 `onImageAction()` 会阻止生成期间增删／重排图片；群聊也禁止此时新建编辑。因此不能用“先发送，再进群聊编辑并换图”证明本问题。上述路径在发送前完成附件修改，之后的 `SaveEditingMessage` 只检查处理状态，未检查生成任务，故仍然可达。

## P2：进程重建后，旧编辑选择器的结果会落入新消息草稿

位置：[MessageImageCoordinator.kt:189](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/media/MessageImageCoordinator.kt:189)。

`mPickEditing` 初始化为 `false`，没有表达“本协调器没有发起过选择”的状态。`pick()` 只在该值为 `true` 时检查编辑身份；否则直接把回调加入 `state.draft`。[Activity 回调](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatActivity.kt:29) 只转发 URI，不携带操作归属。

- 触发条件：编辑历史消息并打开系统选图器，应用进程在后台被回收；返回时 Activity 和 ViewModel 重建，系统向注册的回调交付先前选择结果。
- 实际结果：新协调器没有原编辑目标，却以默认 `false` 把结果加入新消息草稿。初始化 Intent 与结果 Intent 经串行收集执行时，初始化完成后即会走到该分支；该路径没有“无待处理选择”的拒绝条件。
- 影响：用户为历史消息选择的图片被改派给下一条待发送消息，违背已经采用的“不跨进程恢复草稿”策略及选择结果隔离要求。
- 建议：用可空的待处理选择记录区分“没有选择”和“选择新消息图片”，仅 `choose()` 可以创建该记录，结果到达后消费。新 ViewModel 没有记录时丢弃旧结果并给出提示即可，无须恢复完整草稿或重新引入 `SavedStateHandle`。

Android 明确说明结果回调需要在进程／Activity 重建后重新注册，处理结果所需的额外状态不会由结果 API 自动恢复。[Android Activity Result 文档](https://developer.android.com/training/basics/intents/result)

**排除门禁后的证据与边界：**

- 历史编辑区 [MessageImageEditButton](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/ui/message/MessageImageGallery.kt:219) 确实能发出 `Choose(editing = true)`，Activity 无条件注册选择器。两个 Activity 均没有 `noHistory` 等禁止正常恢复的配置；`CoreActivity` 正常将 `savedInstanceState` 传给 `ComponentActivity`。
- Activity 在 `onCreate()` 发出 `Init`。AndroidX 的生命周期注册在 `ON_START` 接通回调并交付积存结果，恢复过程也保留在途启动标识和 request code 映射，见 [ActivityResultRegistry 官方源码](https://raw.githubusercontent.com/androidx/androidx/androidx-main/activity/activity/src/main/java/androidx/activity/result/ActivityResultRegistry.kt)。若初始化还在 IO 中，串行 Intent 收集会让结果等待初始化完成；`getOrNull<Normal>()` 不是拒绝这类回调的可靠保护。初始化成功且仍是同一有效会话时，新协调器即可接受结果。
- 普通旋转等配置重建复用原 ViewModel，不触发此问题；同一 ViewModel 内取消／切换编辑已有版本检查，也不在本条范围。必要条件是原 ViewModel 丢失，但系统保留在途结果，例如后台进程被回收，而非用户强制停止或移除任务。
- 结果只是加入**可见的未发送草稿**，不会自动发给模型；原历史消息也不会因此被修改。修复目标是避免错误归属，无须违反现有“不跨进程恢复草稿”的产品约定。

## P2：群聊旧图片的不可裁剪保护超出了触发批次

位置：[GroupChatPromptBuilder.kt:232](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/groupchat/GroupChatPromptBuilder.kt:232)。

[批次循环](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatViewModel.kt:1535) 在用户触发批次完成后清空 `batchTriggerMessageId`，Repository 也因此停止额外补入触发消息。但 Builder 不接收这个身份，而是无条件把候选历史中最后一条用户消息的图片标为 `canDrop = false`。

- 触发条件：用户图片所在批次已完成，后续自动轮次没有新用户输入，旧图片仍位于配置允许的历史窗口中；随着角色回复增长，预算需要淘汰这条旧图文。
- 实际结果：Builder 继续保护旧图片，Finalizer 只能淘汰其他历史；若剩余必需内容加旧图片仍超预算，就抛出预算异常。删除这条旧图文原本即可构成合法请求。
- 影响：自动对话会被已经结束的旧图片阻断。现有将历史窗口设为一条的场景只能证明旧图片退出查询窗口后不再发送，不能覆盖图片仍在候选窗口、需要由 Token 预算裁剪的情况。
- 建议：把明确的本批次受保护消息 ID 传入 Prompt Context，用该 ID 决定额外的不可裁剪保护；批次结束后的旧图文恢复普通历史保留优先级，并继续整条消息裁剪。

**排除前置约束后的证据与边界：**

- 该问题不必依赖 AutoMode。非手动策略下，[点击成员头像](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatViewModel.kt:548) 就会调用 `launchGeneration(sessionId, listOf(forcedSpeaker))`，未传入触发用户 ID，形成无新用户输入的新批次。
- 可在已有“用户图片＋角色文字回复”、图片尚未被摘要覆盖的群聊中，先在模型配置页面调小上下文预算，再返回点击成员头像。设剩余必需文字开销为 T、旧图文增加的开销为 I，只要输入预算 B 满足 `T ≤ B < T + I`，去掉旧图文就能发送，现有代码却禁止去掉它。预算字段可以由 UI 编辑，保存检查只要求正数及回复预留小于上下文；不存在强制让 B 足够容纳旧图的前置检查。
- 默认历史窗口为 500 条，并非初稿讨论中可能让人误解的“默认无限”；设置为较大的有限值或 0 同样可使旧图留下。`getGroupChatPromptData()` 不会按 Token 预算预裁剪图片；[Finalizer](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/prompt/PromptRequestFinalizer.kt:153) 只删除 `canDrop = true` 的整条消息，没有能解除该保护的兜底分支。
- AutoMode 的 `maybeAutoSummarize()` 在生成循环结束后执行，不会保证下一轮之前图片已被摘要替代。旧图被摘要覆盖、退出历史窗口、之后已有更新的用户消息，或剩余预算足够时，不触发本条预算阻断。

## P2：第一次选图失败时没有可见错误提示

位置：[MessageImages.kt:62](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/ui/message/MessageImages.kt:62)。

`DraftAttachmentTray()` 在 `draft` 为空且不再处理中时直接返回，但错误文本在该条件之后渲染。

- 触发条件：空草稿首次选择损坏图片、不支持的动画、超过限制的图片，或来源 URI 读取失败，且没有任何图片准备成功。
- 实际结果：协调器的 `finally` 结束处理，异常分支设置 `errorResId`；此时 `draft` 仍为空，托盘直接退出，错误文本不会显示。
- 影响：用户只看到选图器关闭／加载结束，无法知道添加失败。协调器已消费异常，两类 ViewModel 没有另外发出 Toast。
- 建议：将错误显示独立于图片列表是否为空，或让提前返回条件同时要求没有错误。

该回归由 `4b2bc3b` 的托盘替换引入。

**可达性与边界：**输入栏的首次添加按钮独立于托盘显示，空草稿可以打开系统选择器。`ImageOnly` 未携带本应用的 32 MiB／4800 万像素上限；选中超过本地上限的有效照片即可走到 [本地校验失败](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/media/MessageImageRuntime.kt:282)。无需伪造 URI 或绕过系统控件。此结论限定于没有成功附件、没有其他正在显示错误的历史编辑框；已有附件或部分选择成功时，托盘仍会显示错误。

## P3：Gemini 地址解析失败会遗留完整含图请求文件

位置：[GeminiLLMClient.kt:170](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/llm/adapter/GeminiLLMClient.kt:170)。

`codec.prepare()` 已经写出含 Base64 的临时 JSON；随后执行的 `toHttpUrl()` 位于 `prepared.toHttpRequest { ... }` 保护范围之外。

- 触发条件：Gemini 的 Base URL 是非空但非法的地址，例如缺少 scheme。模型配置保存入口只验证非空，没有拦截这种值。
- 实际结果：`toHttpUrl()` 抛异常，既未进入 `PreparedWireBody.toHttpRequest()` 的删除分支，也未返回到 `generate()`／流式收集中的 `try/finally`。
- 影响：每次重试都留下一个完整含图请求文件。当前兜底清理只在之后创建请求文件时删除超过一天的旧文件，不能代替当次失败释放，也没有限制一天内失败文件的累计大小。
- 建议：在生成含图文件前解析 URL，或把 URL 构建一并移入 `prepared.toHttpRequest` 的保护范围，保证准备成功后的所有退出路径都释放文件。

相关清理契约：[PreparedWireBody.toHttpRequest](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/llm/adapter/MultimodalWireCodec.kt:35)、[请求暂存创建](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/media/MessageImageRuntime.kt:48)、[地址保存校验](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/llmprovideredit/LLMProviderEditViewModel.kt:684)。

**可达性及降级理由：**

- 在模型编辑页面选择 Gemini 协议，将 Base URL 填为 `generativelanguage.googleapis.com`（缺少 `https://`），其余字段合法即可保存。保存按钮没有“连通性测试成功”前提，保存链路和 `LLMClientFactory` 也不解析 URL；`normalizedBaseUrl()` 只去首尾空白和末尾斜杠，不会补 scheme。
- 图片能力可设为“支持”，正常提交一条预算内的含图消息后，才会在 Gemini 地址解析处失败。因此不是只能由测试直接构造非法 Provider 才触发的问题。
- 正确地址、纯文字请求，以及在 `codec.prepare()` 内就失败的请求不产生本条残留。文件位于应用私有缓存，未发现对外泄露路径；之后创建请求文件时还有超过 24 小时的清理。故将其从 P2 **降为 P3**，作为低成本的资源释放修复，不再与正常流程的图文错配同等排序。

## P3：新消息图片草稿的排序能力失去 UI 入口

位置：[MessageImages.kt:95](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/ui/message/MessageImages.kt:95)。

`4b2bc3b` 用 `DraftAttachmentTray` 替换原图片条后，删除了草稿的 `Move(uuid, editing = false)` 操作。新托盘只提供预览、删除和追加；当前 UI 中唯一的 `Move` 调用位于历史消息编辑网格，并固定使用 `editing = true`。

- 触发条件：新消息已选择多张图片，需要在发送前交换顺序。
- 实际结果：协调器仍支持移动，但页面没有任何控件可以调用草稿移动操作。
- 影响：用户只能删除后重新选择，不能继续使用原有发送前排序能力；涉及“第一张／第二张”的正文时，调整顺序直接影响输入含义。
- 建议：为草稿恢复前移菜单或重排手势，发出 `editing = false` 的移动行为，并沿用处理／生成中的编辑门禁。

**回退依据与影响边界：**[功能实现任务表](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/用户图片发送功能实现任务表.md:18) 列明“多图选择、移除与排序”，且上一个版本确有草稿前移按钮；当前托盘没有长按菜单或拖动手势，大图查看器也不发出 `Move`。历史编辑网格的长按前移固定为 `editing = true`，不能替代草稿入口。这是低优先级的既有交互能力回退，**不是图片顺序随机或协议发送乱序**；用户仍可移除图片后按所需顺序逐张重新添加，因此保留 P3。

## 已复核的关键逻辑

以下为源码检查结论，不等同于运行验证通过。

| 环节 | 审查结果 |
| --- | --- |
| 选择与处理 | 有流式字节限制、尺寸与格式校验、EXIF 方向处理、发送版本压缩、取消版本隔离；上轮“预览错误解除选图保护”的问题已修复 |
| 本地提交 | 正文、附件顺序、文件索引在同一事务提交；原图发布先于事务，失败暂存可保留；图片编辑使相关摘要失效 |
| 文件生命周期 | 消息类型隔离、附件独立 UUID、hash 去重、读取租约以及删除／分支调用链已核对，未发现新的可确认误删问题 |
| 普通发送与重试 | 用户消息先提交，生成失败保留消息；单聊复用已有用户消息，群聊保存未完成角色列表；普通 Prompt 的读取一致性问题见上文 |
| 历史与预算 | 图文块经后处理保留顺序，按原消息裁剪；统计图片数量、发送字节与视觉 Token；群聊额外保护范围问题见上文 |
| 协议 | 三种适配器共用内联编码器，消息结构受 Patch 保护，实际 JSON 有字节上限；没有发现新的普通发送路径丢图问题，Gemini 异常释放有遗漏 |
| 摘要 | 候选保留纯图消息，选择连续前缀，提交校验正文／附件及摘要快照；视觉错误可暂停自动摘要，摘要后使用文字覆盖历史 |
| 展示与导出 | 上轮附件类型查询、首页纯图预览、原图 MIME／扩展名与冻结保存目标修复仍在；本轮发现的错误可见性和排序回归见上文 |
| 日志与备份 | 请求图片用元数据占位记录，响应／错误日志有脱敏和长度限制；备份规则排除 staging，缓存沿用缓存目录 |

Room 保持 version 4 属于方案明确记录的未发布版本约束，本轮不将其再次报告为缺陷。

## 后续修复的行为验收要点

以下仅列出需要满足的行为，没有在本轮新增或执行测试。

- 编辑与生成交错时，请求中的正文和附件必须属于同一快照，或明确拒绝过时输入。
- 新协调器没有待处理选择记录时，不接纳旧编辑选择器的回调；配置重建复用原 ViewModel 时保留有效目标。
- 群聊批次结束后，仍处在候选窗口内的旧图文可以按预算正常裁剪；当前批次触发图文继续受保护。
- 空草稿首次选图失败后能够看到明确错误。
- Gemini 非法地址失败后，没有遗留本次请求的 JSON 文件。
- 多图草稿可以在发送前调整顺序，最终附件位置与请求中的图片顺序一致。
