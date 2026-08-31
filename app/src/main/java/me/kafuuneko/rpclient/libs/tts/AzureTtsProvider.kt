package me.kafuuneko.rpclient.libs.tts

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AzureTtsProvider(
    private val okHttpClient: OkHttpClient
) : TtsProvider {
    private val lock = Any()
    private var activeCall: Call? = null

    override suspend fun synthesize(request: TtsSynthesisRequest, outputFile: File) {
        val azureRequest = request as? AzureTtsRequest
            ?: throw TtsException("Azure TTS received an invalid request")
        val region = azureRequest.region.trim()
        if (region.isBlank()) throw TtsException("Azure region is required")
        if (!AZURE_REGION_PATTERN.matches(region)) throw TtsException("Azure region is invalid")
        if (azureRequest.apiKey.isBlank()) throw TtsException("Azure API key is required")
        if (azureRequest.voice.isBlank()) throw TtsException("Azure voice is required")
        if (azureRequest.text.isBlank()) throw TtsException("Speech text is blank")

        val ssml = buildSsml(azureRequest)
        val httpRequest = try {
            Request.Builder()
                .url("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
                .header("Ocp-Apim-Subscription-Key", azureRequest.apiKey)
                .header("Content-Type", "application/ssml+xml")
                .header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                .post(ssml.toRequestBody(SSML_MEDIA_TYPE))
                .build()
        } catch (error: IllegalArgumentException) {
            throw TtsException("Azure region is invalid", error)
        }
        val audio = await(httpRequest)
        if (audio.isEmpty()) throw TtsException("Azure returned empty audio")
        try {
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { it.write(audio) }
        } catch (error: Throwable) {
            throw TtsException("Unable to save Azure audio", error)
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
                        continuation.resumeWithException(TtsException("Azure request failed", e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        synchronized(lock) {
                            if (activeCall === call) activeCall = null
                        }
                        if (!it.isSuccessful) {
                            if (!continuation.isCancelled) {
                                continuation.resumeWithException(TtsException("Azure request failed (HTTP ${it.code})"))
                            }
                            return
                        }
                        val bytes = try {
                            it.body?.bytes()
                        } catch (error: Throwable) {
                            if (!continuation.isCancelled) {
                                continuation.resumeWithException(TtsException("Azure response could not be read", error))
                            }
                            return
                        }
                        if (bytes == null) {
                            if (!continuation.isCancelled) {
                                continuation.resumeWithException(TtsException("Azure returned an empty response"))
                            }
                            return
                        }
                        if (!continuation.isCancelled) continuation.resume(bytes)
                    }
                }
            })
        }
    }

    private fun buildSsml(request: AzureTtsRequest): String {
        val language = request.voice.split('-').take(2).joinToString("-").ifBlank { "en-US" }
        val rate = if (request.speechRate.isFinite()) {
            request.speechRate.coerceIn(0.5f, 2f)
        } else {
            1f
        }
        val percentage = (rate - 1f) * 100f
        val rateValue = String.format(Locale.US, "%+.0f%%", percentage)
        return "<speak version=\"1.0\" xml:lang=\"${escapeXml(language)}\" " +
            "xmlns=\"http://www.w3.org/2001/10/synthesis\">" +
            "<voice name=\"${escapeXml(request.voice)}\"><prosody rate=\"$rateValue\">" +
            "${escapeXml(request.text)}" +
            "</prosody></voice></speak>"
    }

    private fun escapeXml(value: String): String {
        return buildString(value.length) {
            value.forEach { character ->
                when (character) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '\'' -> append("&apos;")
                    '"' -> append("&quot;")
                    else -> append(character)
                }
            }
        }
    }

    private companion object {
        val AZURE_REGION_PATTERN = Regex("[a-z0-9-]+", RegexOption.IGNORE_CASE)
        val SSML_MEDIA_TYPE = "application/ssml+xml; charset=utf-8".toMediaType()
    }
}
