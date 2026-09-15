# 图片发送审计问题修复记录

## 修复结果

针对 [原审计报告](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/audits/2026-09-15-image-send-audit.md) 的三项 P2 问题完成修复，并补齐相关规范问题。

| 问题 | 修改后的行为 |
| --- | --- |
| 预览错误提前结束选图状态 | 通用异常处理仅更新错误提示；选图任务通过自身结束或取消路径管理 `processing`，预览通过身份校验结束自己的加载状态 |
| 附件类型查询大小写错误 | DAO 通过 `MessageType` 参数和既有 Converter 绑定稳定编码，含图文字导出能正确触发遗漏确认 |
| 纯图会话预览错误及硬编码文案 | 会话概览单独返回最新消息的图片存在标记，首页优先显示正文，无正文且有图片时使用已有的本地化 `message_image` 资源 |
| 保存缺少具体 MIME 与扩展名 | Runtime 检查原始字节，生成具体 MIME 与对应文件名，经 ViewEvent 传给共用文档创建合约；实际保存继续复制原图 |

原图元数据支持现有校验器接受的 JPEG、PNG、WebP、HEIC 和 HEIF。保存目标 UUID 在调起系统选择器前冻结，切换预览不会改变该次保存对象。数据库结构与持久化编码未改变，本次无需数据库迁移。

## 规范与可维护性

- 补齐协调器、图片 Runtime 和相关事件处理方法的参数、返回值及关键异常契约。
- 将图片查看器拆为对话框、预览画布和导航组件，补充分步说明；切换消息、图片或预览版本时重置局部手势状态。
- 图片会话预览复用所有语言已有的字符串资源，不在 DAO 中格式化界面文案。
- 新增保存元数据模型和文档创建合约共用于单聊、群聊，避免两个页面分别推断文件格式。

## 回归覆盖

回归用例加入原有测试文件，没有将历史审计中的临时失败测试直接加入常规测试集。

[MessageImageLifecycleTest.kt](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/androidTest/java/me/kafuuneko/rpclient/libs/room/repository/MessageImageLifecycleTest.kt) 新增三个用例：

- 含图导出检测、空会话、跨聊天类型同 ID 隔离、移除最后附件后恢复纯文字行为。
- 单聊最新图片消息、摘要排除、后续文字回复与图片标记更新。
- 群聊最新图片消息、跨类型同 ID 隔离与后续文字消息预览。

[MultimodalImageIntegrationTest.kt](/Users/kafuuneko/Documents/Git/Github/RPClient/app/src/androidTest/java/me/kafuuneko/rpclient/libs/llm/adapter/MultimodalImageIntegrationTest.kt) 新增两个用例，并扩展一个已有用例：

- 通过受控管道阻塞选图，验证预览和保存元数据失败均不解除发送保护，实际选图结束后才解除。测试依赖 API 29 的 `ContentResolver.wrap`，因此仅在 API 29 及以上运行。
- 故意使用错误的来源 MIME，验证 PNG、JPEG 的原图元数据、系统创建文档请求、草稿保存和持久化后保存；逐字节比较输出与原图。
- 调起保存后切换到另一张预览，验证保存结果仍为原先冻结的图片。

## 验证

- Debug 构建通过。
- 单元测试：268 个通过，无失败、错误或跳过。
- Android 完整测试：81 个通过，无失败、错误或跳过，包含新增的五个回归用例。
- Lint 通过，无 Error。
- 最后补充保存期间切换预览的断言后，对三个图片集成用例再次定向验证，全部通过。
- `git diff --check` 通过。

构建使用离线 Gradle 依赖，Android 测试仅在本次只读启动的 Pixel_10a Android 17 模拟器执行。[验证摘要](/Users/kafuuneko/Documents/Git/Github/RPClient/doc/audits/2026-09-15-image-send/fix-validation.txt)

本次没有访问真实模型服务或使用用户图片。不同厂商文档提供器以及 HEIC/HEIF 实际保存的端到端兼容性尚未逐项实测；已验证 PNG/JPEG 的实际格式识别、系统请求参数及原始字节保存。
