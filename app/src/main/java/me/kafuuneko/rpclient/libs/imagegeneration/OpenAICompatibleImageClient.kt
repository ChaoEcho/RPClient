package me.kafuuneko.rpclient.libs.imagegeneration

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.debug.AppLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

data class ImageGenerationConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val size: String
)

data class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String?
)

/** OpenAI-compatible image generations client. */
class OpenAICompatibleImageClient(
    private val okHttpClient: OkHttpClient
) {
    suspend fun generate(config: ImageGenerationConfig, prompt: String): GeneratedImage =
        withContext(Dispatchers.IO) {
            AppLogger.i("Image", "Image generation started: model=${config.model}, size=${config.size}")
            val startNs = System.nanoTime()
            try {
            val payload = JsonObject().apply {
                addProperty("model", config.model)
                addProperty("prompt", prompt)
                addProperty("size", config.size)
            }.toString()
            val requestBuilder = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/images/generations")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            if (config.apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
            }
            val responseBody = okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Image generation request failed: ${response.code}: ${body.ifBlank { response.message }}")
                }
                body
            }
            val first = try {
                JsonParser.parseString(responseBody)
                    .asJsonObject
                    .getAsJsonArray("data")
                    ?.firstOrNull()
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
            } catch (error: RuntimeException) {
                throw IOException("Image generation response is not valid JSON", error)
            } ?: throw IOException("Image generation response contains no image data")
            val encoded = first.get("b64_json")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.takeIf { it.isNotBlank() }
            if (encoded != null) {
                val decoded = decodeBase64(encoded)
                return@withContext GeneratedImage(
                    decoded.bytes,
                    decoded.mimeType?.takeIf { it.startsWith("image/", ignoreCase = true) }
                        ?: detectMimeType(decoded.bytes)
                )
            }
            val url = first.get("url")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("Image generation response contains neither b64_json nor url")
            val img = download(url)
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            AppLogger.i("Image", "Image generation succeeded: ${img.bytes.size} bytes (${durationMs}ms)")
            img
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            AppLogger.e("Image", "Image generation failed (${durationMs}ms): ${e.message}", e)
            throw e
        }
    }

    private fun download(url: String): GeneratedImage {
        return okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Image download failed: ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw IOException("Image download returned an empty body")
            val contentType = body.contentType()?.toString()?.substringBefore(';')?.trim()
                ?.takeIf { it.isNotBlank() }
            val bytes = body.bytes()
            GeneratedImage(bytes, contentType?.takeIf { it.startsWith("image/", ignoreCase = true) }
                ?: detectMimeType(bytes))
        }
    }

    private fun decodeBase64(value: String): GeneratedImage {
        val commaIndex = value.indexOf(',')
        val declaredMimeType = if (value.startsWith("data:") && commaIndex >= 0) {
            value.substring(5, commaIndex).substringBefore(';').trim()
                .takeIf { it.isNotBlank() }
        } else {
            null
        }
        val encoded = if (value.startsWith("data:") && commaIndex >= 0) {
            value.substring(commaIndex + 1)
        } else {
            value
        }
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw IOException("Image response contains invalid base64", error)
        }
        return GeneratedImage(bytes, declaredMimeType)
    }

    private fun detectMimeType(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> "image/png"
        bytes.size >= 3 && bytes.copyOfRange(0, 3).contentEquals(JPEG_SIGNATURE) -> "image/jpeg"
        bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).contentEquals(RIFF_SIGNATURE) &&
            bytes.copyOfRange(8, 12).contentEquals(WEBP_SIGNATURE) -> "image/webp"
        else -> null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val RIFF_SIGNATURE = byteArrayOf(0x52, 0x49, 0x46, 0x46)
        val WEBP_SIGNATURE = byteArrayOf(0x57, 0x45, 0x42, 0x50)
    }
}
