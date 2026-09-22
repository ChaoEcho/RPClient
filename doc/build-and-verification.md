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
