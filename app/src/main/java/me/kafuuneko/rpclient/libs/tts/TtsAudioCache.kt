package me.kafuuneko.rpclient.libs.tts

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** 合成缓存的租约覆盖等待、合成和播放，清理不会删除临时文件或任何在用音频。 */
class TtsAudioCache internal constructor(
    private val directory: File,
    private val maxCacheBytes: Long = MAX_CACHE_BYTES
) {
    constructor(context: Context) : this(File(context.applicationContext.cacheDir, "tts"))

    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()

    init {
        require(maxCacheBytes > 0) { "Audio cache limit must be positive" }
    }

    suspend fun <T> withAudio(
        providerType: TtsProviderType,
        request: TtsSynthesisRequest,
        synthesize: suspend (File) -> Unit,
        play: suspend (File) -> T
    ): T {
        val extension = if (providerType == TtsProviderType.Azure) "mp3" else "wav"
        val fileName = "${key(providerType, request)}.$extension"
        val entry = synchronized(lock) {
            entries.getOrPut(fileName) { Entry() }.also { it.users += 1 }
        }
        try {
            // 同键只执行一次合成；在等待锁之前就持有文件租约，避免唤醒时文件被淘汰。
            val file = entry.mutex.withLock {
                withContext(Dispatchers.IO) { loadOrSynthesize(fileName, synthesize) }
            }
            return play(file)
        } finally {
            // 停止播放和排队取消同样释放租约，清理不能被父任务取消打断。
            withContext(NonCancellable + Dispatchers.IO) {
                synchronized(lock) {
                    entry.users -= 1
                    if (entry.users == 0) entries.remove(fileName)
                    cleanupLocked()
                }
            }
        }
    }

    private suspend fun loadOrSynthesize(fileName: String, synthesize: suspend (File) -> Unit): File {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw IOException("Unable to create audio cache")
        val target = File(directory, fileName)
        if (target.isFile && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        val temporary = File(directory, ".$fileName.${UUID.randomUUID()}.partial")
        try {
            // 只在完整合成后发布文件，临时文件不会参与容量清理或被当作命中项。
            synthesize(temporary)
            if (!temporary.isFile || temporary.length() == 0L) {
                throw TtsException("Speech synthesis produced no audio")
            }
            if (target.exists() && !target.delete()) throw IOException("Unable to replace empty audio cache")
            if (!temporary.renameTo(target)) throw IOException("Unable to publish synthesized audio")
            target.setLastModified(System.currentTimeMillis())
            synchronized(lock) { cleanupLocked() }
            return target
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: TtsException) {
            throw error
        } catch (error: Exception) {
            throw TtsException("Unable to cache speech audio", error)
        } finally {
            temporary.delete()
        }
    }

    /** 只淘汰完整且未被租用的文件；在用大文件允许暂时超过软容量上限。 */
    private fun cleanupLocked() {
        val files = directory.listFiles { file ->
            file.isFile && !file.name.startsWith('.') && file.extension in setOf("wav", "mp3")
        }.orEmpty()
        var totalBytes = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (totalBytes <= maxCacheBytes) break
            if (entries[file.name]?.users?.let { it > 0 } == true) continue
            val size = file.length()
            if (file.delete()) totalBytes -= size
        }
    }

    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)

    private fun key(providerType: TtsProviderType, request: TtsSynthesisRequest): String {
        val canonical = buildString {
            field(providerType.persistedValue)
            when (request) {
                is MimoTtsRequest -> {
                    field("mimo")
                    field(request.text)
                    field(request.baseUrl)
                    field(request.apiKey)
                    field(request.model)
                    field(request.voice)
                    field(request.instructions)
                    field(request.temperature.toString())
                }
                is AzureTtsRequest -> {
                    field("azure")
                    field(request.text)
                    field(request.region)
                    field(request.apiKey)
                    field(request.voice)
                    field(request.speechRate.toString())
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun StringBuilder.field(value: String) {
        append(value.length).append(':').append(value)
    }

    private companion object {
        const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
    }
}
