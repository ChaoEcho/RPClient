# AndroidJUnitRunner 与应用共享 tracing 依赖；库去重后运行器不能调用被目标 APK 优化移除的入口。
# 仅 verification 加载本文件；正式 Release 的业务 R8 配置不变。
-keep class androidx.tracing.** { *; }
