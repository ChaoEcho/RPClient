package me.kafuuneko.rpclient.libs.backup

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 使用固定容器头与认证加密保护 RPClient 完整备份。 */
class BackupCrypto {
    /** 将明文流加密为版本化 `.rpbackup` envelope，不关闭调用方的流。 */
    fun encrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val salt = ByteArray(SALT_BYTES)
        val iv = ByteArray(IV_BYTES)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        val keyBytes = try {
            deriveKey(password, salt, BackupContract.KDF_ITERATIONS)
        } catch (_: GeneralSecurityException) {
            throw BackupException.GenericFailure()
        }

        try {
            // 初始化认证加密器并写入固定头。
            val cipher = try {
                createCipher(Cipher.ENCRYPT_MODE, keyBytes, iv)
            } catch (_: GeneralSecurityException) {
                throw BackupException.GenericFailure()
            }
            writeHeader(output, BackupContract.KDF_ITERATIONS, salt, iv)

            // 分块读取明文，避免把完整备份载入内存。
            try {
                transform(input, output, cipher)
            } catch (_: GeneralSecurityException) {
                throw BackupException.GenericFailure()
            }
            output.flush()
        } finally {
            keyBytes.fill(0)
        }
    }

    /** 将 `.rpbackup` envelope 解密为明文流，不关闭调用方的流。 */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val header = readHeader(input)
        val keyBytes = try {
            deriveKey(password, header.salt, header.iterations)
        } catch (_: GeneralSecurityException) {
            throw BackupException.GenericFailure()
        }

        try {
            // 认证头部参数后初始化解密器。
            val cipher = try {
                createCipher(Cipher.DECRYPT_MODE, keyBytes, header.iv)
            } catch (_: GeneralSecurityException) {
                throw BackupException.GenericFailure()
            }

            // 分块处理密文，并在 doFinal 中验证 GCM 标签。
            try {
                transform(input, output, cipher)
            } catch (_: GeneralSecurityException) {
                throw BackupException.WrongPasswordOrCorrupted()
            }
            output.flush()
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun createCipher(mode: Int, keyBytes: ByteArray, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(mode, SecretKeySpec(keyBytes, AES_ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
        return cipher
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val keySpec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(KDF_ALGORITHM)
                .generateSecret(keySpec)
                .encoded
                ?: throw GeneralSecurityException()
        } finally {
            keySpec.clearPassword()
        }
    }

    private fun writeHeader(
        output: OutputStream,
        iterations: Int,
        salt: ByteArray,
        iv: ByteArray
    ) {
        output.write(MAGIC)
        writeInt(output, BackupContract.CONTAINER_VERSION)
        writeInt(output, iterations)
        output.write(salt)
        output.write(iv)
    }

    private fun readHeader(input: InputStream): Header {
        // 先校验 magic，避免对未知格式执行昂贵的 KDF。
        val magic = ByteArray(MAGIC_BYTES)
        readFully(input, magic)
        if (!magic.contentEquals(MAGIC)) {
            throw BackupException.UnsupportedFormat()
        }

        // 版本不兼容时立即拒绝，后续字段不参与解释。
        val containerVersion = readInt(input)
        if (containerVersion != BackupContract.CONTAINER_VERSION) {
            throw BackupException.UnsupportedVersion()
        }
        val iterations = readInt(input)
        // 限制 KDF 成本，拒绝过低或可能耗尽资源的头部参数。
        if (iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
            throw BackupException.UnsupportedFormat()
        }
        val salt = ByteArray(SALT_BYTES)
        val iv = ByteArray(IV_BYTES)
        readFully(input, salt)
        readFully(input, iv)
        return Header(iterations, salt, iv)
    }

    private fun transform(input: InputStream, output: OutputStream, cipher: Cipher) {
        // 使用固定大小缓冲区逐段推进 Cipher。
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            var count = input.read(buffer)
            if (count < 0) {
                break
            }
            if (count == 0) {
                val value = input.read()
                if (value < 0) {
                    break
                }
                buffer[0] = value.toByte()
                count = 1
            }
            val transformed = cipher.update(buffer, 0, count)
            if (transformed != null && transformed.isNotEmpty()) {
                output.write(transformed)
            }
        }

        // doFinal 负责输出尾部并完成 AES-GCM 认证。
        val finalBytes = cipher.doFinal()
        if (finalBytes.isNotEmpty()) {
            output.write(finalBytes)
        }
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        // 按字段长度补齐头部，遇到截断统一归类为格式错误。
        try {
            var offset = 0
            while (offset < target.size) {
                val count = input.read(target, offset, target.size - offset)
                if (count < 0) {
                    throw BackupException.UnsupportedFormat()
                }
                if (count == 0) {
                    val value = input.read()
                    if (value < 0) {
                        throw BackupException.UnsupportedFormat()
                    }
                    target[offset++] = value.toByte()
                } else {
                    offset += count
                }
            }
        } catch (_: EOFException) {
            throw BackupException.UnsupportedFormat()
        }
    }

    private fun readInt(input: InputStream): Int {
        val bytes = ByteArray(INT_BYTES)
        readFully(input, bytes)
        return ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
    }

    private fun writeInt(output: OutputStream, value: Int) {
        output.write(value ushr 24)
        output.write(value ushr 16)
        output.write(value ushr 8)
        output.write(value)
    }

    private data class Header(
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray
    )

    private companion object {
        const val AES_ALGORITHM = "AES"
        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val INT_BYTES = 4
        const val MAGIC_BYTES = 15
        const val BUFFER_SIZE = 16 * 1024
        const val MIN_KDF_ITERATIONS = 10_000
        const val MAX_KDF_ITERATIONS = 1_000_000
        val MAGIC = "RPCLIENT_BACKUP".toByteArray(Charsets.US_ASCII)
        val secureRandom = SecureRandom()
    }
}
