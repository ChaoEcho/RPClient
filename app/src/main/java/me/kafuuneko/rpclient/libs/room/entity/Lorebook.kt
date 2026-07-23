package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 世界书级配置。
 *
 * [scanDepth]、[tokenBudget] 和 [recursiveScanning] 为条目默认行为；[tokenBudget] 是固定
 * Token 上限，为 0 时跟随全局世界书预算，不对本书增加额外限制。
 * [extensionsJson] 保留当前应用尚未识别的导入字段。
 */
@Entity(
    tableName = "lorebooks"
)
data class Lorebook(
    // 世界书 ID。
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 世界书显示名称。
    val name: String,
    // 世界书描述，仅用于管理和导入导出。
    val description: String = "",
    // 默认扫描的最近消息条数，条目未单独配置时使用该值。
    val scanDepth: Int = 2,
    // 本世界书的固定 Token 上限；0 表示跟随全局世界书预算。
    val tokenBudget: Int = 0,
    // 是否允许已激活条目的内容继续参与后续递归扫描。
    val recursiveScanning: Boolean = false,
    // 世界书 extensions 原始兼容数据，用于保留当前 App 未识别的第三方字段。
    val extensionsJson: String = "{}"
)
