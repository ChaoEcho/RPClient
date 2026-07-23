package me.kafuuneko.rpclient.libs.upgrade

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/** 提供当前安装包 versionCode，便于调度器在测试中使用固定版本。 */
fun interface AppVersionCodeProvider {
    fun currentVersionCode(): Int
}

/** 从 Android PackageManager 读取当前安装包 versionCode。 */
class AndroidAppVersionCodeProvider(
    private val mContext: Context
) : AppVersionCodeProvider {
    override fun currentVersionCode(): Int {
        val packageInfo = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
    }
}
