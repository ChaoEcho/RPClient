package me.kafuuneko.rpclient.libs.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val crypto = BackupCrypto()

    @Test
    fun roundTripStreamsLargeUnicodePayloadAndKeepsStreamsOpen() {
        val payload = ByteArray(128 * 1024) { index -> (index * 31).toByte() } +
            "换行\nemoji 🌟 and binary\u0000\n".toByteArray(UTF_8)
        val input = ChunkedInputStream(payload, maxChunkSize = 7)
        val encryptedOutput = TrackingOutputStream()

        crypto.encrypt(input, encryptedOutput, "正确 密码".toCharArray())

        assertFalse(input.closed)
        assertFalse(encryptedOutput.closed)
        assertTrue(encryptedOutput.flushed)
        val encrypted = encryptedOutput.toByteArray()
        val decryptInput = ChunkedInputStream(encrypted, maxChunkSize = 5)
        val decryptedOutput = TrackingOutputStream()

        crypto.decrypt(decryptInput, decryptedOutput, "正确 密码".toCharArray())

        assertArrayEquals(payload, decryptedOutput.toByteArray())
        assertFalse(decryptInput.closed)
        assertFalse(decryptedOutput.closed)
        assertTrue(decryptedOutput.flushed)
    }

    @Test
    fun emptyPasswordRoundTripsEmptyPayload() {
        assertArrayEquals(ByteArray(0), roundTrip(ByteArray(0), charArrayOf()))
    }

    @Test
    fun unicodePasswordRoundTrips() {
        val password = "密码🙂é".toCharArray()
        val payload = "unicode password payload".toByteArray(UTF_8)

        assertArrayEquals(payload, roundTrip(payload, password))
    }

    @Test
    fun whitespaceOnlyPasswordRoundTripsWithoutTrimming() {
        val password = "   ".toCharArray()
        val payload = "whitespace password payload".toByteArray(UTF_8)
        val encrypted = encrypt(payload, password)

        assertArrayEquals(payload, decrypt(encrypted, password))
        assertThrows(BackupException.WrongPasswordOrCorrupted::class.java) {
            decrypt(encrypted, charArrayOf())
        }
    }

    @Test
    fun passwordIsNotTrimmed() {
        val encrypted = encrypt("sensitive payload".toByteArray(UTF_8), "  secret  ".toCharArray())

        assertThrows(BackupException.WrongPasswordOrCorrupted::class.java) {
            decrypt(encrypted, "secret".toCharArray())
        }
        assertArrayEquals(
            "sensitive payload".toByteArray(UTF_8),
            decrypt(encrypted, "  secret  ".toCharArray())
        )
    }

    @Test
    fun wrongPasswordIsMappedToStableBackupException() {
        val encrypted = encrypt("payload".toByteArray(UTF_8), "correct".toCharArray())

        val error = assertThrows(BackupException.WrongPasswordOrCorrupted::class.java) {
            decrypt(encrypted, "incorrect".toCharArray())
        }

        assertEquals("wrong_password_or_corrupted", error.message)
    }

    @Test
    fun ciphertextTamperingIsMappedToStableBackupException() {
        val encrypted = encrypt("payload".toByteArray(UTF_8), "correct".toCharArray())
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0x01).toByte()

        assertThrows(BackupException.WrongPasswordOrCorrupted::class.java) {
            decrypt(tampered, "correct".toCharArray())
        }
    }

    @Test
    fun envelopeUsesFixedHeaderAndAuthenticationTag() {
        val payload = "payload".toByteArray(UTF_8)
        val encrypted = encrypt(payload, "correct".toCharArray())

        assertArrayEquals(MAGIC, encrypted.copyOfRange(0, MAGIC_BYTES))
        assertEquals(BackupContract.CONTAINER_VERSION, readInt(encrypted, MAGIC_BYTES))
        assertEquals(BackupContract.KDF_ITERATIONS, readInt(encrypted, MAGIC_BYTES + INT_BYTES))
        assertEquals(HEADER_BYTES + payload.size + TAG_BYTES, encrypted.size)
    }

    @Test
    fun invalidMagicIsRejectedAsUnsupportedFormat() {
        val invalid = "WRONG_MAGIC_____".toByteArray(UTF_8)

        assertThrows(BackupException.UnsupportedFormat::class.java) {
            decrypt(invalid, "correct".toCharArray())
        }
    }

    @Test
    fun unsupportedVersionIsRejectedBeforeRemainingHeader() {
        val invalid = ByteArray(MAGIC_BYTES + INT_BYTES)
        MAGIC.copyInto(invalid)
        writeInt(invalid, MAGIC_BYTES, BackupContract.CONTAINER_VERSION + 1)

        assertThrows(BackupException.UnsupportedVersion::class.java) {
            decrypt(invalid, "correct".toCharArray())
        }
    }

    @Test
    fun truncatedHeaderIsRejectedAsUnsupportedFormat() {
        val encrypted = encrypt(ByteArray(0), "correct".toCharArray())

        listOf(
            0,
            MAGIC_BYTES - 1,
            MAGIC_BYTES + INT_BYTES - 1,
            MAGIC_BYTES + INT_BYTES + INT_BYTES + SALT_BYTES - 1,
            HEADER_BYTES - 1
        ).forEach { size ->
            assertThrows(BackupException.UnsupportedFormat::class.java) {
                decrypt(encrypted.copyOf(size), "correct".toCharArray())
            }
        }
    }

    @Test
    fun unreasonableIterationsAreRejectedBeforeKeyDerivation() {
        val encrypted = encrypt(ByteArray(0), "correct".toCharArray())
        val invalid = encrypted.copyOf()
        val iterationsOffset = MAGIC_BYTES + INT_BYTES

        listOf(1, 0, -1, 1_000_001).forEach { iterations ->
            writeInt(invalid, iterationsOffset, iterations)
            assertThrows(BackupException.UnsupportedFormat::class.java) {
                decrypt(invalid, "correct".toCharArray())
            }
        }
    }

    private fun roundTrip(payload: ByteArray, password: CharArray): ByteArray {
        return decrypt(encrypt(payload, password), password)
    }

    private fun encrypt(payload: ByteArray, password: CharArray): ByteArray {
        val output = ByteArrayOutputStream()
        crypto.encrypt(ChunkedInputStream(payload, maxChunkSize = 11), output, password)
        return output.toByteArray()
    }

    private fun decrypt(encrypted: ByteArray, password: CharArray): ByteArray {
        val output = ByteArrayOutputStream()
        crypto.decrypt(ChunkedInputStream(encrypted, maxChunkSize = 13), output, password)
        return output.toByteArray()
    }

    private class ChunkedInputStream(
        private val source: ByteArray,
        private val maxChunkSize: Int
    ) : InputStream() {
        private var position = 0
        var closed = false
            private set

        override fun read(): Int {
            if (position >= source.size) {
                return -1
            }
            return source[position++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) {
                return 0
            }
            if (position >= source.size) {
                return -1
            }
            val count = minOf(length, maxChunkSize, source.size - position)
            source.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun close() {
            closed = true
        }
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set
        var flushed = false
            private set

        override fun close() {
            closed = true
        }

        override fun flush() {
            flushed = true
            super.flush()
        }
    }

    private companion object {
        const val MAGIC_BYTES = 15
        const val INT_BYTES = 4
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val HEADER_BYTES = MAGIC_BYTES + INT_BYTES + INT_BYTES + SALT_BYTES + IV_BYTES
        const val TAG_BYTES = 16
        val MAGIC = "RPCLIENT_BACKUP".toByteArray(UTF_8)

        fun readInt(bytes: ByteArray, offset: Int): Int {
            return ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
        }

        fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value ushr 24).toByte()
            bytes[offset + 1] = (value ushr 16).toByte()
            bytes[offset + 2] = (value ushr 8).toByte()
            bytes[offset + 3] = value.toByte()
        }
    }
}
