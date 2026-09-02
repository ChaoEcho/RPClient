# RPClient 编译手册

本项目基于 Gradle 9.x / AGP 与 JDK 21。为保证产物完整性与环境兼容，打包请遵循本手册规范。

---

## 1. 核心构建参数

当前 Linux ARM64 构建机必要环境变量与参数：

- **JDK 21**：`JAVA_HOME=/home/ubuntu/.local/jdks/jdk-21.0.12.1+1`
- **AAPT2 覆盖**：`-Pandroid.aapt2FromMavenOverride=/home/ubuntu/android-sdk/build-tools/36.0.0/aapt2` *(解决 ARM64 兼容问题)*

> ⚠️ **强制规范：打包前必须 `clean`**  
> 工程内置 jtokkit 离线分词词表（14MB+）及 Compose 资源。异常增量构建可能跳过 assets/dex 打包，生成约 **4.5 MB 的损坏包**。

---

## 2. 常用构建命令

### A. Debug (dev) 构建
> 追加 `.dev` 包名与版本后缀，用于日常联调开发。

```bash
JAVA_HOME=/home/ubuntu/.local/jdks/jdk-21.0.12.1+1 \
./gradlew \
  -Pandroid.aapt2FromMavenOverride=/home/ubuntu/android-sdk/build-tools/36.0.0/aapt2 \
  clean :app:assembleDebug
```

### B. Release 构建（默认 / Debug 签名）
> 未配置正式签名参数时，默认使用 Debug 签名配置打包，适合 Release 性能与功能测试。

```bash
JAVA_HOME=/home/ubuntu/.local/jdks/jdk-21.0.12.1+1 \
./gradlew \
  -Pandroid.aapt2FromMavenOverride=/home/ubuntu/android-sdk/build-tools/36.0.0/aapt2 \
  clean :app:assembleRelease
```

### C. Release 构建（正式发布 / 生产签名）
> 正式分发/上架需传入签名凭证（支持环境变量或 `-P` 属性）。

```bash
RELEASE_STORE_FILE="/path/to/release.keystore" \
RELEASE_STORE_PASSWORD="your_store_password" \
RELEASE_KEY_ALIAS="your_key_alias" \
RELEASE_KEY_PASSWORD="your_key_password" \
JAVA_HOME=/home/ubuntu/.local/jdks/jdk-21.0.12.1+1 \
./gradlew \
  -Pandroid.aapt2FromMavenOverride=/home/ubuntu/android-sdk/build-tools/36.0.0/aapt2 \
  clean :app:assembleRelease
```

---

## 3. 产物与验证

### 产物命名
构建脚本会自动追加构建时间戳后缀：
- **Debug**：`app/build/outputs/apk/debug/app-debug-<yyyyMMdd.HHmmss>.apk`
- **Release**：`app/build/outputs/apk/release/app-release-<yyyyMMdd.HHmmss>.apk`

### 体积校验基准
构建完成后使用 `ls -lh app/build/outputs/apk/<variant>/` 校验大小：
- **正常包参考**：Debug 约 **29 MB** 左右，Release 约 **23 MB** 左右（包含完整离线分词词表及图标依赖）。
- **异常损坏包**：若体积仅在 **~4.5 MB** 左右，说明资源漏打，严禁分发，必须重新执行全量 `clean` 构建。
