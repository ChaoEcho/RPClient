package me.kafuuneko.rpclient.libs.upgrade

/**
 * 一个以应用 versionCode 为目标的业务数据升级步骤。
 *
 * 与只处理数据库结构的 Room Migration 不同，App Upgrade 可以协调偏好、文件和 Room
 * 等多个存储。步骤必须可以安全重试；只有 [migrate] 正常返回后，调度器才会记录检查点。
 */
interface AppUpgrade {
    /** 该步骤成功后业务数据达到的目标应用版本。 */
    val targetVersionCode: Int

    /** 将任意更早的已支持状态升级到 [targetVersionCode]。 */
    suspend fun migrate()

    /**
     * 清理升级成功后不再需要的旧数据。
     *
     * 清理在检查点写入后执行，并可能在后续启动中重复调用，因此必须保持幂等。
     */
    suspend fun cleanup() = Unit
}
