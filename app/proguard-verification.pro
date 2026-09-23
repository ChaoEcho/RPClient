# AndroidJUnitRunner 与应用共享 tracing 依赖；库去重后运行器不能调用被目标 APK 优化移除的入口。
# 仅 verification 加载本文件；正式 Release 的业务 R8 配置不变。
-keep class androidx.tracing.** { *; }
# androidx.test.platform.io 在运行器启动阶段直接引用 Kotlin 顶层 Lazy API；
# verification 目标 APK 必须保留原始类名，不能只保留被 R8 重命名后的实现。
-keep class kotlin.LazyKt { *; }
-keep class kotlin.LazyKt__LazyJVMKt { *; }
-keep class kotlin.LazyKt__LazyKt { *; }
# 独立测试 APK 在应用 R8 完成后才编译；保留其直接调用的 Kotlin/协程顶层入口。
# 仅 verification 使用，不改变正式 Release，也不保留整个业务包。
-keep class kotlin.collections.CollectionsKt** { *; }
-keep class kotlinx.coroutines.BuildersKt** { *; }
# 完整恢复设备测试直接访问应用级准入对象；保持其方法的调用类型和签名。
-keep class me.kafuuneko.rpclient.libs.generation.DataMaintenance { *; }
-keep class me.kafuuneko.rpclient.libs.generation.DataOperationBarrier { *; }
