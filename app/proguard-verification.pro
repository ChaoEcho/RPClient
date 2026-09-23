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
# 设备测试是独立 APK，直接调用应用的 DAO 和仓库；保持这些测试可见的 ABI。
# 只影响 verification，主页面的 ViewModel/Intent 仍由业务 R8 规则决定。
-keep class androidx.room.util.** { *; }
-keep interface me.kafuuneko.rpclient.libs.room.dao.** { *; }
-keep class me.kafuuneko.rpclient.libs.room.dao.** { *; }
-keep class me.kafuuneko.rpclient.libs.room.repository.FileRepository { *; }
-keep class me.kafuuneko.rpclient.libs.room.repository.ChatRepository { *; }
-keep class me.kafuuneko.rpclient.libs.room.repository.MessageImageRepository { *; }
-keep class me.kafuuneko.rpclient.libs.media.MessageImageRuntime { *; }
-keep class me.kafuuneko.rpclient.libs.chat.ChatArchive** { *; }
-keep class me.kafuuneko.rpclient.libs.backup.BackupRepository { *; }
-keep class me.kafuuneko.rpclient.libs.backup.BackupCodec { *; }
-keep class me.kafuuneko.rpclient.libs.backup.BackupCrypto { *; }
-keep class me.kafuuneko.rpclient.libs.backup.RestoreJournal { *; }
-keep class me.kafuuneko.rpclient.libs.generation.DataMaintenanceKt { *; }
-keep class me.kafuuneko.rpclient.libs.AppModel { *; }
-keep class me.kafuuneko.rpclient.libs.theme.AppThemeMode** { *; }
-keep class me.kafuuneko.rpclient.libs.theme.AppThemeManager { *; }
# 测试进程还会直接触达应用 APK 内的 Kotlin 状态盒、调度器、Koin 和迁移夹具。
# 这些保留仅服务于独立测试 APK；正式 Release 仍使用原有精确业务规则。
-keep class kotlin.Result** { *; }
-keep class kotlin.coroutines.jvm.internal.Boxing { *; }
-keep class kotlinx.coroutines.Dispatchers { *; }
-keep class org.koin.core.context.GlobalContext { *; }
-keep class org.koin.core.Koin { *; }
-keep class com.google.gson.JsonParser { *; }
-keep class androidx.room.migration.bundle.** { *; }
-keep class kotlinx.serialization.** { *; }
# 独立测试用到的个别依赖 API 不在主应用的可达调用图内；仍仅在 verification 保留。
-keep class org.koin.core.registry.ScopeRegistry { *; }
-keep class org.koin.core.scope.Scope { *; }
-keep class me.kafuuneko.rpclient.libs.backup.BackupContract { *; }
-keep class com.google.gson.JsonObject { *; }
-keep class kotlin.Pair { *; }
-keep class kotlin.jvm.internal.FunctionReferenceImpl { *; }
-keep interface kotlinx.coroutines.CompletableDeferred { *; }
# 测试侧直接调用父 DAO 的批量插入，以及 Lifecycle/Room 测试夹具的运行时入口。
-keep interface me.kafuuneko.rpclient.libs.room.MutableDao { *; }
-keep class androidx.lifecycle.ViewModelProvider** { *; }
-keep class androidx.arch.core.executor.ArchTaskExecutor { *; }
# 真实 ViewModel 继续混淆；只保持设备冒烟测试读取的 UiState 和 Room 配置构造器。
-keep class me.kafuuneko.rpclient.feature.main.presentation.MainUiState** { *; }
-keep class me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState { *; }
-keep class me.kafuuneko.rpclient.feature.main.presentation.MainAppearanceSettingsState { *; }
-keep class androidx.room.DatabaseConfiguration { *; }
# MigrationTestHelper 会从独立测试 APK 调用 RoomOpenDelegate 的历史构造器。
-keep class androidx.room.RoomOpenDelegate** { *; }
