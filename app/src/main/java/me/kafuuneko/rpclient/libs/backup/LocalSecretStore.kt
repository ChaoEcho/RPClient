package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 使用 AndroidKeyStore 保存 WebDAV 安全凭据。
 *
 * SharedPreferences 只保存 AES/GCM 密文，密钥本身始终由 AndroidKeyStore 管理。
 */
class LocalSecretStore(context: Context) {
    private val preferences = (context.applicationContext ?: context)
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** 仅在迁移时清理旧版本遗留的本地备份密码。 */
    fun clearLegacyBackupPassword() {
        preferences.edit().remove(LEGACY_BACKUP_PASSWORD_KEY).apply()
    }

    /** 是否已保存 WebDAV 密码。 */
    fun hasSavedWebDavPassword(): Boolean = preferences.getString(WEBDAV_PASSWORD_KEY, null) != null

    /** 读取 WebDAV 密码。 */
    fun getWebDavPassword(): String? = readSecret(WEBDAV_PASSWORD_KEY)

    /** 保存或删除 WebDAV 密码，首次保存失败时尝试自恢复重试一次。 */
    fun setWebDavPassword(value: String?) = writeSecretWithRecovery(WEBDAV_PASSWORD_KEY, value)

    private fun readSecret(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return try {
            val stored = Base64.decode(encoded, Base64.DEFAULT)
            if (stored.size <= IV_SIZE) {
                throw IllegalArgumentException("invalid_secret")
            }

            val iv = stored.copyOfRange(0, IV_SIZE)
            val ciphertext = stored.copyOfRange(IV_SIZE, stored.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            // 密钥失效或密文损坏时只清理当前秘密，不影响其他配置。
            preferences.edit().remove(key).commit()
            null
        }
    }

    private fun writeSecretWithRecovery(key: String, value: String?) {
        if (value == null) {
            if (!preferences.edit().remove(key).commit()) {
                throw IllegalStateException("secure_secret_store_unavailable")
            }
            return
        }

        try {
            writeSecretInternal(key, value)
        } catch (_: Exception) {
            // 第一次失败尝试自恢复：清理 alias 与旧密文后重新生成密钥并重试一次
            try {
                resetKeyAndCiphertext(key)
                writeSecretInternal(key, value)
            } catch (_: Exception) {
                // 不能退回明文保存；调用方只得到不包含秘密的稳定错误。
                throw IllegalStateException("secure_secret_store_unavailable")
            }
        }
    }

    private fun writeSecretInternal(key: String, value: String) {
        val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stored = ByteArray(iv.size + ciphertext.size).apply {
            iv.copyInto(this)
            ciphertext.copyInto(this, destinationOffset = iv.size)
        }
        val committed = preferences.edit()
            .putString(key, Base64.encodeToString(stored, Base64.NO_WRAP))
            .commit()
        if (!committed) throw IllegalStateException("secure_secret_store_unavailable")
    }

    private fun resetKeyAndCiphertext(key: String) {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
        preferences.edit().remove(key).commit()
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rpclient_local_secrets_v1"
        const val PREFERENCES_NAME = "rpclient_secure_secrets"
        const val LEGACY_BACKUP_PASSWORD_KEY = "backup_password"
        const val WEBDAV_PASSWORD_KEY = "webdav_password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val GCM_TAG_BITS = 128
    }
}
