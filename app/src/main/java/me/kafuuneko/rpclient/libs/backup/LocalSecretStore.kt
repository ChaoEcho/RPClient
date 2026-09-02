package me.kafuuneko.rpclient.libs.backup

import android.content.Context

/** 保存 WebDAV 密码的应用私有设置。 */
class LocalSecretStore(context: Context) {
    private val preferences = (context.applicationContext ?: context)
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getWebDavPassword(): String {
        preferences.edit().remove(LEGACY_WEBDAV_PASSWORD_KEY).apply()
        return preferences.getString(WEBDAV_PASSWORD_KEY, "").orEmpty()
    }

    fun setWebDavPassword(value: String) {
        preferences.edit()
            .remove(LEGACY_WEBDAV_PASSWORD_KEY)
            .apply {
                if (value.isEmpty()) {
                    remove(WEBDAV_PASSWORD_KEY)
                } else {
                    putString(WEBDAV_PASSWORD_KEY, value)
                }
            }
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "rpclient_secure_secrets"
        const val LEGACY_WEBDAV_PASSWORD_KEY = "webdav_password"
        const val WEBDAV_PASSWORD_KEY = "webdav_password_v2"
    }
}
