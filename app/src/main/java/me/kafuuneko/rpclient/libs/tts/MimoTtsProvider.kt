package me.kafuuneko.rpclient.libs.tts

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MimoTtsProvider(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : TtsProvider {
    private val lock = Any()
    private var activeCall: Call? = null

    override suspend fun synthesize(request: TtsSynthesisRequest, outputFile: File) {
        val mimoRequest = request as? MimoTtsRequest
            ?: throw TtsException("MiMo TTS received an invalid request")
        val baseUrl = mimoRequest.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) throw TtsException("MiMo base URL is required")
        if (mimoRequest.apiKey.isBlank()) throw TtsException("MiMo API key is required")
        if (mimoRequest.voice.isBlank()) throw TtsException("MiMo voice is required")
        if (mimoRequest.text.isBlank()) throw TtsException("Speech text is blank")
        if (!mimoRequest.temperature.isFinite() || mimoRequest.temperature !in 0f..1.5f) {
            throw TtsException("MiMo temperature must be between 0 and 1.5")
        }

        val messages = mutableListOf<Map<String, String>>()
        if (mimoRequest.instructions.isNotBlank()) {
            messages += mapOf("role" to "user", "content" to mimoRequest.instructions)
        }
        messages += mapOf("role" to "assistant", "content" to mimoRequest.text)
        val payload = mapOf(
            "model" to "mimo-v2.5-tts",
            "messages" to messages,
            "audio" to mapOf("format" to "wav", "voice" to mimoRequest.voice),
            "temperature" to mimoRequest.temperature
        )
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = try {
            Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("api-key", mimoRequest.apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build()
        } catch (error: IllegalArgumentException) {
            throw TtsException("MiMo base URL is invalid", error)
        }

        val responseBytes = await(httpRequest)
        val root = try {
            JsonParser.parseString(responseBytes.toString(Charsets.UTF_8)).asJsonObject
        } catch (error: Throwable) {
            throw TtsException("MiMo returned an invalid response", error)
        }
        val audioData = runCatching {
            root.path("choices").asJsonArrayOrNull()
                ?.firstOrNull()
                ?.asJsonObjectOrNull()
                ?.path("message")
                ?.asJsonObjectOrNull()
                ?.path("audio")
                ?.asJsonObjectOrNull()
                ?.path("data")
                ?.takeUnless { it.isJsonNull }
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: throw TtsException("MiMo returned no audio")
        val audio = try {
            Base64.decode(audioData, Base64.DEFAULT)
        } catch (error: IllegalArgumentException) {
            throw TtsException("MiMo returned invalid audio data", error)
        }
        if (audio.isEmpty()) throw TtsException("MiMo returned empty audio")
        try {
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { it.write(audio) }
        } catch (error: Throwable) {
            throw TtsException("Unable to save MiMo audio", error)
        }
    }

    override fun stop() {
        synchronized(lock) {
            activeCall?.cancel()
            activeCall = null
        }
    }

    private suspend fun await(request: Request): ByteArray {
        val call = okHttpClient.newCall(request)
        synchronized(lock) { activeCall = call }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                synchronized(lock) {
                    if (activeCall === call) activeCall = null
                }
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    synchronized(lock) {
                        if (activeCall === call) activeCall = null
                    }
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(TtsException("MiMo request failed", e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        synchronized(lock) {
                            if (activeCall === call) activeCall = null
                        }
                        if (!it.isSuccessful) {
                            val responseBody = try {
                                it.body?.string().orEmpty()
                            } catch (_: Throwable) {
                                ""
                            }
                            val detail = errorDetail(responseBody)
                            val message = if (detail == null) {
                                "MiMo request failed (HTTP ${it.code})"
                            } else {
                                "MiMo request failed (HTTP ${it.code}): $detail"
                            }
                            if (!continuation.isCancelled) {
                                continuation.resumeWithException(TtsException(message))
                            }
                            return
                        }
                        val bytes = try {
                            it.body?.bytes()
                        } catch (error: Throwable) {
                            if (!continuation.isCancelled) {
                                continuation.resumeWithException(TtsException("MiMo response could not be read", error))
                            }
                            return
                        }
                        if (bytes == null) {
                            if (!continuation.isCancelled) {
                                continuation.resumeWithException(TtsException("MiMo returned an empty response"))
                            }
                            return
                        }
                        if (!continuation.isCancelled) continuation.resume(bytes)
                    }
                }
            })
        }
    }

    private fun errorDetail(responseBody: String): String? {
        val body = responseBody.trim()
        if (body.isBlank()) return null
        val serverMessage = runCatching {
            JsonParser.parseString(body)
                .asJsonObjectOrNull()
                ?.path("error")
                ?.asJsonObjectOrNull()
                ?.path("message")
                ?.takeUnless { it.isJsonNull }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return (serverMessage ?: body).take(MAX_ERROR_DETAIL_LENGTH)
    }

    private fun JsonObject.path(name: String): com.google.gson.JsonElement {
        return get(name) ?: com.google.gson.JsonNull.INSTANCE
    }

    private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun com.google.gson.JsonElement.asJsonArrayOrNull(): List<com.google.gson.JsonElement>? {
        return if (isJsonArray) asJsonArray.toList() else null
    }

    private companion object {
        const val MAX_ERROR_DETAIL_LENGTH = 256
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
