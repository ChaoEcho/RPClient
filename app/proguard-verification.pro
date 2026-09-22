# AndroidJUnitRunner 与应用共享 tracing 依赖；库去重后运行器不能调用被目标 APK 优化移除的入口。
# 仅 verification 加载本文件；正式 Release 的业务 R8 配置不变。
-keep class androidx.tracing.** { *; }
# androidx.test.platform.io 在运行器启动阶段直接引用 Kotlin 顶层 Lazy API；
# verification 目标 APK 必须保留原始类名，不能只保留被 R8 重命名后的实现。
-keep class kotlin.LazyKt { *; }
-keep class kotlin.LazyKt__LazyJVMKt { *; }
-keep class kotlin.LazyKt__LazyKt { *; }
