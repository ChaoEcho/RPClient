package me.kafuuneko.rpclient.libs.tts

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class MimoTtsProvider(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : TtsProvider {
    private class ActiveCall(val call: Call) {
        @Volatile
        var stopped: Boolean = false
    }

    private val lock = Any()
    private var activeCall: ActiveCall? = null

    override suspend fun synthesize(request: TtsSynthesisRequest, outputFile: File) {
        val mimoRequest = request as? MimoTtsRequest
            ?: throw TtsException("MiMo TTS received an invalid request")
        val httpRequest = buildHttpRequest(mimoRequest, streaming = false)
        val audio = execute(httpRequest) { response, active ->
            checkNotStopped(active)
            readWavAudio(response).also { checkNotStopped(active) }
        }
        try {
            withContext(Dispatchers.IO) {
                outputFile.parentFile?.mkdirs()
                outputFile.outputStream().use { it.write(audio) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw TtsException("Unable to save MiMo audio", error)
        }
    }

    suspend fun stream(
        request: MimoTtsRequest,
        onAudioChunk: suspend (ByteArray) -> Unit
    ) {
        val httpRequest = buildHttpRequest(request, streaming = true)
        execute(httpRequest) { response, active ->
            readStream(response, active, onAudioChunk)
        }
    }

    override fun stop() {
        val call = synchronized(lock) {
            val current = activeCall
            current?.stopped = true
            activeCall = null
            current?.call
        }
        call?.cancel()
    }

    private fun buildHttpRequest(request: MimoTtsRequest, streaming: Boolean): Request {
        val baseUrl = request.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) throw TtsException("MiMo base URL is required")
        if (request.apiKey.isBlank()) throw TtsException("MiMo API key is required")
        val model = request.model.trim()
        if (model.isBlank()) throw TtsException("MiMo model is required")
        if (request.voice.isBlank()) throw TtsException("MiMo voice is required")
        if (request.text.isBlank()) throw TtsException("Speech text is blank")
        if (!request.temperature.isFinite() || request.temperature !in 0f..1.5f) {
            throw TtsException("MiMo temperature must be between 0 and 1.5")
        }

        val messages = mutableListOf<Map<String, String>>()
        if (request.instructions.isNotBlank()) {
            messages += mapOf("role" to "user", "content" to request.instructions)
        }
        messages += mapOf("role" to "assistant", "content" to request.text)
        val payload = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages,
            "audio" to mapOf(
                "format" to if (streaming) "pcm16" else "wav",
                "voice" to request.voice
            ),
            "temperature" to request.temperature
        )
        if (streaming) payload["stream"] = true

        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        return try {
            Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("api-key", request.apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build()
        } catch (error: IllegalArgumentException) {
            throw TtsException("MiMo base URL is invalid", error)
        }
    }

    private fun readWavAudio(response: Response): ByteArray {
        val responseBytes = try {
            response.body?.bytes()
        } catch (error: IOException) {
            throw TtsException("MiMo response could not be read", error)
        } ?: throw TtsException("MiMo returned an empty response")
        val root = try {
            JsonParser.parseString(responseBytes.toString(Charsets.UTF_8)).asJsonObject
        } catch (error: Exception) {
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
            decodeBase64(audioData)
        } catch (error: RuntimeException) {
            throw TtsException("MiMo returned invalid audio data", error)
        }
        if (audio.isEmpty()) throw TtsException("MiMo returned empty audio")
        return audio
    }

    private suspend fun readStream(
        response: Response,
        active: ActiveCall,
        onAudioChunk: suspend (ByteArray) -> Unit
    ) {
        val body = response.body ?: throw TtsException("MiMo invalid stream response: empty response body")
        val source = body.source()
        var receivedAudio = false
        while (true) {
            checkNotStopped(active)
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val eventData = line.substring(5).trim()
            if (eventData == "[DONE]") break
            if (eventData.isBlank()) continue

            val event = try {
                JsonParser.parseString(eventData).asJsonObject
            } catch (error: Exception) {
                throw TtsException("MiMo invalid stream response", error)
            }
            val encodedAudio = streamAudioData(event) ?: continue
            if (encodedAudio.isBlank()) continue
            val audio = try {
                decodeBase64(encodedAudio)
            } catch (error: RuntimeException) {
                throw TtsException("MiMo invalid Base64 audio chunk", error)
            }
            if (audio.isEmpty()) {
                throw TtsException("MiMo invalid stream response: empty audio chunk")
            }
            checkNotStopped(active)
            onAudioChunk(audio)
            receivedAudio = true
        }
        checkNotStopped(active)
        if (!receivedAudio) {
            throw TtsException("MiMo invalid stream response: no audio")
        }
    }

    private fun streamAudioData(root: JsonObject): String? {
        val firstChoice = root.path("choices").asJsonArrayOrNull()?.firstOrNull()?.asJsonObjectOrNull()
            ?: return null
        val delta = firstChoice.path("delta").asJsonObjectOrNull() ?: return null
        val audio = delta.path("audio").asJsonObjectOrNull() ?: return null
        val data = audio.path("data")
        if (data.isJsonNull) return null
        if (!data.isJsonPrimitive || !data.asJsonPrimitive.isString) {
            throw TtsException("MiMo invalid stream response: audio data is not a string")
        }
        return data.asString
    }

    private suspend fun <T> execute(
        request: Request,
        block: suspend (Response, ActiveCall) -> T
    ): T {
        val call = okHttpClient.newCall(request)
        val active = activate(call)
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            return withContext(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw requestFailure(response)
                        block(response, active)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    currentCoroutineContext().ensureActive()
                    if (active.stopped || call.isCanceled()) {
                        throw TtsException("MiMo request stopped/cancelled", error)
                    }
                    throw TtsException("MiMo request failed", error)
                }
            }
        } finally {
            cancellationHandle?.dispose()
            clearActive(active)
        }
    }

    private fun activate(call: Call): ActiveCall {
        val active = ActiveCall(call)
        val previous = synchronized(lock) {
            val old = activeCall
            old?.stopped = true
            activeCall = active
            old
        }
        previous?.call?.cancel()
        return active
    }

    private fun clearActive(active: ActiveCall) {
        synchronized(lock) {
            if (activeCall === active) activeCall = null
        }
    }

    private fun checkNotStopped(active: ActiveCall) {
        if (active.stopped) {
            throw TtsException("MiMo request stopped/cancelled")
        }
    }

    private fun requestFailure(response: Response): TtsException {
        val responseBody = try {
            response.body?.string().orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val detail = errorDetail(responseBody)
        val message = if (detail == null) {
            "MiMo request failed (HTTP ${response.code})"
        } else {
            "MiMo request failed (HTTP ${response.code}): $detail"
        }
        return TtsException(message)
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

    private fun decodeBase64(value: String): ByteArray {
        val paddingStart = value.indexOf('=')
        val hasInvalidPadding = paddingStart >= 0 && value.length % 4 != 0
        if (value.length % 4 == 1 || hasInvalidPadding || !BASE64_PATTERN.matches(value)) {
            throw IllegalArgumentException("Invalid Base64 value")
        }
        return try {
            Base64.decode(value, Base64.DEFAULT)
        } catch (error: RuntimeException) {
            if (!error.isAndroidBase64Stub()) throw error
            java.util.Base64.getDecoder().decode(value)
        }
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

    private fun RuntimeException.isAndroidBase64Stub(): Boolean {
        val message = message.orEmpty()
        return message == "Stub!" || message.contains("not mocked", ignoreCase = true)
    }

    private companion object {
        const val MAX_ERROR_DETAIL_LENGTH = 256
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]*={0,2}$")
    }
}
