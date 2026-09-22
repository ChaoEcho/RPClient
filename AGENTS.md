# RPClient — AI 入口

## 项目边界

RPClient 是 Android 客户端。本机只维护源码，不部署应用、不监听端口，不在仓库保存用户数据、备份、签名密钥或凭据。

## 开始前

- 阅读 `README_ZH.md`、`doc/coding-guidelines.md`，按任务读取其中的专题规范。
- 构建与验证见 `doc/build-and-verification.md`；上游集成约束与进度见 `doc/upstream-integration.md`。
- 检查 Git 工作区，保留不属于当前任务的修改。

## 分支与兼容

- `master` 保留上游历史，`main` 承载 fork 功能；集成工作从 `main` 建立独立分支。
- 上游同步保留双方历史，不重写已发布的 fork 提交。
- 已发布的 Room schema 不得覆盖。数据库迁移、完整备份和聊天归档分别维护兼容边界。
- 生成配图默认仅用于展示，不自动作为视觉输入回传模型。
- 不使用破坏性数据库回退，不把正式发布静默切换为另一把签名密钥。

## 验证

- 执行 `python3 scripts/verify_repository.py` 和 `git diff --check`。
- Android 构建、单元测试和设备测试按构建文档执行；没有相应环境时明确记录未执行项。
- 发布前必须验证旧数据升级、备份恢复和 R8 构建，不用 Debug 成功替代 Release 验收。
