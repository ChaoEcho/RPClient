package me.kafuuneko.rpclient.libs.core

import android.content.ContentResolver
import android.content.Intent

/**
 * 释放旧版一次性导入流程遗留的持久 URI 权限。
 *
 * 当前所有导入都会立即复制内容且不保存 URI；失败的释放会在下次启动时自然重试。
 */
internal fun ContentResolver.releaseObsoletePersistedUriPermissions() {
    val permissions = runCatching { persistedUriPermissions }
        .getOrDefault(emptyList())
    permissions.forEach { permission ->
        val modeFlags =
            (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (modeFlags == 0) return@forEach
        runCatching {
            releasePersistableUriPermission(permission.uri, modeFlags)
        }
    }
}
