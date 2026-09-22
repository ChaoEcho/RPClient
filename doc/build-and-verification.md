# 构建与验证

## 环境

- 使用仓库 Gradle Wrapper、JDK 21，以及构建文件指定的 Android SDK。
- 推荐在 GitHub Actions 的 Linux x86_64 runner 上构建。ARM64 主机缺少对应 Android 原生工具时，不替换为来源不明的二进制。
- 本仓库不包含应用运行数据、签名文件或生产凭据。源码服务器不部署 Android 应用。

## 本地静态检查

```bash
python3 scripts/verify_repository.py
git diff --check
```

## Android 验证

在已配置 SDK 的构建机执行：

```bash
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

- CI 的验证构建不创建 Release、不上传商店、不使用生产签名。
- 设备测试使用隔离的测试数据库和文件目录，不使用真实聊天或角色数据。
- Release 必须单独验证反射 Intent 分发、JSON 兼容和分词资源，不能仅根据 APK 体积判断打包成功。

## 发布与回滚

- 正式发布仅允许 `main` 历史中的提交，必须提供与既有安装兼容的签名。
- 签名通过 CI Secrets 或构建机外部凭据注入，不写入 Gradle 文件。
- 数据库升级前保留完整备份。旧 APK 不保证可读取升级后的数据库，回滚需恢复升级前备份。

## 已知 Lint 基线

- 首次 CI 对未改业务源码的 fork 运行了 343 项 JVM 测试，全部通过。
- 同次 Lint 报告有 255 项既有 `MissingTranslation` 错误，已精确登记在 `app/lint-baseline.xml`，不是全局禁用翻译检查；新增文案缺失翻译仍会失败。
- 翻译债务尚未清零；不能把启用基线后的 Lint 成功表述为所有语言已经补齐。
- AGP 标准 APK 名称保留，带时间戳的副本位于 `app/build/distributions/`，避免破坏设备测试使用的产物元数据。
- 未提供正式签名时 Release 保持 unsigned，验证构建不自动换用 Debug 密钥冒充正式升级包。

## 完整恢复的故障边界

- 恢复先关闭业务任务准入、取消并等待 partial 持久化，再替换数据库和偏好。旧页面的延迟回调不能写入新的数据代次。
- 回滚快照只放在 Android 应用私有 `noBackupFilesDir/restore`，不导出、不上传、不进入系统备份。快照在恢复期间包含本机敏感数据；成功后清理，进程中断时保留给下次启动回滚。
- 数据库和偏好都持久化后才写完成标记；已完成但清理中断的恢复不会被错误撤销。
- 回滚失败时阻止普通业务入口，专用恢复页仅允许重试或退出，不自动清库、不丢弃回滚源。
- 准备完成但因协程取消未能交付的加密临时文件和明文校验 staging 也必须清理。
