package me.kafuuneko.rpclient.libs.upgrade

import me.kafuuneko.rpclient.libs.AppModel

/** 保存业务数据迁移和旧存储清理各自的升级检查点。 */
interface AppUpgradeVersionStore {
    var lastCompletedVersionCode: Int
    var lastCleanedVersionCode: Int
}

/**
 * 使用应用偏好保存升级检查点。
 *
 * 继续复用既有 `lastMigratedVersionCode` 键，避免框架重构导致已完成用户重新迁移。
 */
class AppModelUpgradeVersionStore : AppUpgradeVersionStore {
    override var lastCompletedVersionCode: Int
        get() = AppModel.lastMigratedVersionCode
        set(value) {
            AppModel.lastMigratedVersionCode = value
        }

    override var lastCleanedVersionCode: Int
        get() = AppModel.lastCleanedUpgradeVersionCode
        set(value) {
            AppModel.lastCleanedUpgradeVersionCode = value
        }
}
