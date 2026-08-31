package me.kafuuneko.rpclient.libs.tts

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class TtsAudioCache(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "tts")

    suspend fun getOrCreate(
        providerType: TtsProviderType,
        request: TtsSynthesisRequest,
        synthesize: suspend (File) -> Unit
    ): File {
        directory.mkdirs()
        val extension = if (providerType == TtsProviderType.Azure) "mp3" else "wav"
        val target = File(directory, "${key(providerType, request)}.$extension")
        if (target.isFile && target.length() > 0L) return target
        if (target.exists()) target.delete()

        val temporary = File(
            directory,
            ".${target.nameWithoutExtension}.${System.nanoTime()}.$extension"
        )
        try {
            synthesize(temporary)
            if (!temporary.isFile || temporary.length() == 0L) {
                throw TtsException("Speech synthesis produced no audio")
            }
            if (target.isFile && target.length() > 0L) {
                temporary.delete()
                return target
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Unable to move synthesized audio into the cache")
            }
            cleanup()
            return target
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: TtsException) {
            throw error
        } catch (error: Throwable) {
            throw TtsException("Unable to cache speech audio", error)
        } finally {
            temporary.delete()
        }
    }

    private fun cleanup() {
        val files = directory.listFiles { file ->
            file.isFile && (file.extension == "wav" || file.extension == "mp3")
        }.orEmpty()
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalBytes <= MAX_CACHE_BYTES) return@forEach
            val size = file.length()
            if (file.delete()) totalBytes -= size
        }
    }

    private fun key(providerType: TtsProviderType, request: TtsSynthesisRequest): String {
        val canonical = buildString {
            field(providerType.persistedValue)
            when (request) {
                is MimoTtsRequest -> {
                    field("mimo")
                    field(request.text)
                    field(request.baseUrl)
                    field(request.apiKey)
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
