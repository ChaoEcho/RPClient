# 图片发送功能代码审计

> 修复跟进：下文保留修复前的审计结论与反例证据。三项功能问题及相关规范问题已处理，当前结果见 [修复记录](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/audits/2026-09-15-image-send-fixes.md)。

## 结论

发现三项需要修复的 P2 功能问题：图片预览异常会解除仍在运行的选图任务对发送操作的限制；附件查询使用错误的持久化类型编码，导致导出确认和会话预览失效；原图保存没有携带具体 MIME 类型和文件扩展名。另有 KDoc、长方法注释及预览文案不符合项目规范的情况。

现有构建、单元测试、Android 测试和 Lint 均通过，但新增的四个审计反例全部在预期断言处失败。因此，目前不能给出“功能正确且没有回归”的通过结论。此次仅审计，未修改业务代码。

## 范围与验证基线

- 审计提交：`12406c29130e89b6903e7b5873e7268c006cb2ac`、`cf57bb56dc10e3f3469f44c94084c9524368c32e`、`1e6717f41f95f6470c0353a74f2235aa690eb6aa`。
- 检查三次提交的累计变更，并沿调用链检查现有发送、编辑、重试、重新生成、摘要、会话列表、文字导出和备份逻辑。
- 实际构建和测试使用工作区 HEAD `7b2cc73`。该版本包含后续界面提交；本文三项功能问题及查看器规范问题所在文件，相对 `1e6717f4` 均没有后续修改。
- 规范依据：[coding-guidelines.md](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/coding-guidelines.md) 及其关联指南，并结合图片功能方案与实现任务表判断设计约束。

## 功能问题

### P2：预览失败会提前解除图片处理状态

位置：[MessageImageCoordinator.kt:142](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/media/MessageImageCoordinator.kt:142)。

`handle()` 对所有图片操作共用异常处理，并在任意异常发生时设置 `processing = false`。但 `Picked` 在 ViewModel 中通过独立协程执行，预览、保存等操作可以在图片读取尚未结束时进入同一个协调器。

- 触发条件：选图正在等待资源读取，同时打开缺失历史图片的发送版本预览；后者的 `loadSendPreview()` 抛出异常。
- 实际结果：选图任务仍处于活动状态，但协调器已经发布 `processing = false`。
- 影响：单聊和群聊的发送入口，以及协调器的 `draftInputs()`，均使用该状态判断能否发送。此时可以越过“图片尚未准备完毕”的保护，只提交已经准备好的附件；迟到附件还可能在后续草稿中出现。这一发送后果来自调用链分析，审计测试直接复现的是“任务仍活动、保护状态却已解除”。
- 建议：只有对应选图任务的 `finally` 或显式取消路径可以结束处理状态；预览和保存失败只更新各自的错误状态。保留现有任务版本校验，并让发送检查与实际任务生命周期一致。

复现证据：`previewFailureMustNotUnlockAnActiveImagePick` 使用受控管道阻塞图片读取，在确认选图协程仍活动后触发缺图预览；`processing` 应保持为真的断言失败。

### P2：类型编码大小写错误，导致导出确认和纯图预览失效

位置：

- [MessageImageDao.kt:21](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/room/dao/MessageImageDao.kt:21)
- [ChatSessionDao.kt:34](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/room/dao/ChatSessionDao.kt:34)
- [GroupChatSessionDao.kt:43](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/room/dao/GroupChatSessionDao.kt:43)

[MessageType.kt:13](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/room/model/MessageType.kt:13) 定义的持久化编码为 `single` 和 `group`，Room Converter 写入的也是这些小写值。上述 SQL 却直接比较 `'Single'` 和 `'Group'`，无法匹配正常写入的附件。

- 含图单聊调用 `ChatArchiveRepository.hasImages()` 得到 `false`，[ChatViewModel.kt:830](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatViewModel.kt:830) 因而跳过 `ImageExportWarning`，直接进入文字导出。JSONL 仍有图片遗漏占位，但不包含图片字节，也没有执行方案要求的导出前确认。
- 单聊与群聊的最后一条消息为纯图片时，概览查询返回空正文，首页回退到“暂无消息”，无法反映已有图片消息。
- 建议：查询通过 `MessageType` 参数绑定使用既有 Converter，或统一使用稳定编码；增加含图与无图查询的回归断言。会话预览最好返回图片存在标记或数量，再由展示层生成本地化文案。

复现证据：通过真实 Room 与 Repository 创建附件消息后，`imageArchiveMustRequireOmissionWarning`、`singleImageOverviewMustHavePreview`、`groupImageOverviewMustHavePreview` 三个断言均失败。

### P2：原图保存缺少具体文件类型和扩展名

位置：

- [ChatActivity.kt:34](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatActivity.kt:34)、[ChatActivity.kt:85](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/chat/ChatActivity.kt:85)
- [GroupChatActivity.kt:31](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatActivity.kt:31)、[GroupChatActivity.kt:67](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/feature/groupchat/GroupChatActivity.kt:67)

两个 Activity 均使用 `CreateDocument("image/*")`，并以无扩展名的 `image` 启动保存。[MessageImageRuntime.save()](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/media/MessageImageRuntime.kt:135) 则直接复制原图字节，可能是 JPEG、PNG、WebP 等不同格式。

接受默认文件名时，应用没有提供足够信息来正确创建带扩展名的图片文件。Android 官方明确说明通配 MIME 会破坏自动扩展名处理，要求指定具体 MIME。因此存在保存结果缺少扩展名或类型不明确、影响其他应用识别和分享的兼容性问题。[Android CreateDocument 文档](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.CreateDocument)

- 建议：在请求系统保存前，取得已校验原图的 MIME 与对应扩展名，通过保存事件传给 Activity；文档创建请求使用具体类型及带扩展名的默认名称。由于保存原始字节，不能统一标记为 PNG。
- 证据边界：已核对代码和官方 API 合约，未对不同系统文档提供器逐一进行保存、打开和分享实测。

## Coding Guidelines、可读性与可维护性

### 需要补齐

- **方法注释**：[MessageImageCoordinator.draftInputs()](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/media/MessageImageCoordinator.kt:66)、[MessageImageRuntime.references()](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/libs/media/MessageImageRuntime.kt:103)、`save()` 等新增公共方法仅有简短说明，未完整标明参数、返回值及关键约束。应按 [规范第 103 行](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/coding-guidelines.md:103) 补充适用的 KDoc 标签。
- **长方法内部说明**：[MessageImageViewer()](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/main/java/me/kafuuneko/rpclient/ui/message/MessageImages.kt:120) 跨越约 90 行，负责变换状态、图片渲染、导航和操作按钮，内部没有分步注释，不符合 [超过 16 行方法必须添加行内注释](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/coding-guidelines.md:96) 的要求。可按内容显示、图片切换、保存操作拆分私有 Composable，同时补充必要的状态重置说明。
- **界面文案和分层**：两个会话 DAO 中的 `'[Image]'` 是直接用于界面展示的硬编码英文。修复类型编码后，该文案会进入所有语言的会话列表。应返回结构化信息，由展示层使用字符串资源，符合 [文案规范](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/coding-guidelines.md:127)。

### 已检查的正向结果

- 图片文件处理主要集中在 Repository 与 Runtime，选择器放在 Activity，通过 Intent 和 ViewEvent 连接；附件 UI 共用协调器与状态模型，整体符合现有分层方向。
- 原图、发送版本、临时持有和持久化附件采用显式模型；现有测试覆盖资源释放、事务、Prompt 预算及多模态协议等重要边界。
- 对审计提交范围内 36 个新增或修改的可翻译字符串进行检查，支持的语言资源均存在，格式占位符一致。上述 DAO 预览文案是资源检查之外的遗漏。
- 本次累计变更的 `git diff --check` 通过。

## 验证结果

| 验证 | 结果 |
| --- | --- |
| `:app:assembleDebug` | 通过 |
| `:app:testDebugUnitTest` | 268 个测试通过，失败、错误、跳过均为 0 |
| 原有 `:app:connectedDebugAndroidTest` | 76 个测试通过，失败、错误、跳过均为 0 |
| `:app:lintDebug` | 任务通过，无 Error；仍有 Warning，不能据此视为完全符合人工编码规范 |
| 四个新增审计反例 | 全部在上述预期断言处失败，确认两个根因和四种表现 |

环境为 Java 17、离线 Gradle 依赖，以及以只读方式启动的 Pixel_10a Android 17 模拟器。未使用真实模型账户发起付费网络请求；未完成物理设备、低内存、各厂商文档提供器或系统整机备份恢复的端到端验证。

Room 保持 version 4 是 [设计文档明确要求的未发布版本例外](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/用户图片发送功能调研与方案.md:525)，此次不将其误报为新增缺陷。旧开发版 version 4 的直接覆盖安装限制仍按该文档处理。

## 复现材料

- [审计反例源码](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/audits/2026-09-15-image-send/ImageSendAuditTest.kt.txt)
- [反例测试执行日志](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/audits/2026-09-15-image-send/test-run.txt)

反例源码以 `.kt.txt` 保存，不加入常规测试集。复现时将其复制为 `app/src/androidTest/java/me/kafuuneko/rpclient/audit/ImageSendAuditTest.kt`，连接测试设备后执行：

```sh
./gradlew --offline --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=me.kafuuneko.rpclient.audit.ImageSendAuditTest
```

在当前实现上应观察到四个失败断言。修复后应让这些行为断言通过，并补充保存时 MIME、文件名和原始字节一致性的验证。审计结束时已移除临时测试入口，仅保留本报告和复现材料。
