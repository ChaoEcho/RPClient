# Build Guidelines

本项目使用 Gradle 9.x / Android Gradle Plugin，并要求使用 JDK 21。为避免 JDK 不匹配、AAPT2 二进制兼容问题，以及增量构建产生缺失依赖资源的异常 APK，打包测试 APK 时必须遵循以下规则。

## Debug/dev 版本说明

这里的 **dev 版本** 指 Android `debug` build type，不是 Git 的 `develop` 分支。

当前 Debug 构建会追加：

- `applicationIdSuffix = ".dev"`
- `versionNameSuffix = "-dev"`

因此应使用 `:app:assembleDebug` 构建 dev APK。

## 必要构建参数

### JDK 21

若系统默认 Java 不是 JDK 21，必须显式设置：

```bash
JAVA_HOME=/home/ubuntu/.local/jdks/jdk-21.0.12.1+1
```

### AAPT2 覆盖

当前构建环境需要使用 Android SDK Build Tools 36.0.0 中的 ARM64 兼容 AAPT2：

```bash
-Pandroid.aapt2FromMavenOverride=/home/ubuntu/android-sdk/build-tools/36.0.0/aapt2
```

## 打包前必须 clean

每次打包测试 APK 前必须执行 `clean`，尤其是在切换分支、更新依赖或配置缓存变化之后。

工程包含 jtokkit 离线词表（`.tiktoken`，约 14 MB 以上）及 Compose Material 图标等资源。异常的增量构建可能跳过 `mergeExtDexDebug` 或部分 assets 打包，从而产生约 4.5 MB、缺少依赖资源的损坏 APK。

## 标准 Debug/dev APK 构建

```bash
JAVA_HOME=/home/ubuntu/.local/jdks/jdk-21.0.12.1+1 \
./gradlew \
  -Pandroid.aapt2FromMavenOverride=/home/ubuntu/android-sdk/build-tools/36.0.0/aapt2 \
  clean :app:assembleDebug
```

构建完成后必须检查输出文件和大小：

```bash
ls -lh app/build/outputs/apk/debug/
```

如果 APK 只有约 4.5 MB，应视为损坏包，不要用于安装或分发；重新执行上述 `clean :app:assembleDebug` 全量构建并检查构建日志。

## 标准 Release APK 构建

```bash
JAVA_HOME=/home/ubuntu/.local/jdks/jdk-21.0.12.1+1 \
./gradlew \
  -Pandroid.aapt2FromMavenOverride=/home/ubuntu/android-sdk/build-tools/36.0.0/aapt2 \
  clean :app:assembleRelease
```

Release 构建完成后同样应检查输出文件和大小：

```bash
ls -lh app/build/outputs/apk/release/
```
