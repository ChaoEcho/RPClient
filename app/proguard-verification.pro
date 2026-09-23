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
# 测试协程和 Room 历史迁移直接调用的入口不会被主 APK 的 R8 可达性分析看到。
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt** { *; }
-keep class androidx.room.Room { *; }
-keep class androidx.room.RoomDatabase$Builder { *; }
-keep class androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory { *; }
# 测试 APK 的协程与迁移夹具会调用更多 Kotlin 顶层函数，主 APK 单独优化时需保留文件门面。
# 仅匹配 Kotlin/协程库的 *Kt 文件门面，不保留 RPClient 业务类或正式 Release。
-keep class kotlin.**Kt* { *; }
-keep class kotlinx.coroutines.**Kt* { *; }
# 测试直接关闭 Room 数据库；主 APK 自身没有该调用时 R8 不能删除这一 ABI。
-keep class androidx.room.RoomDatabase { *; }
-keep class me.kafuuneko.rpclient.libs.room.AppDatabase { *; }
