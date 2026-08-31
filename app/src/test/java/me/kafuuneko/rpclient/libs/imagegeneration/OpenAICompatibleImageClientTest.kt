package me.kafuuneko.rpclient.libs.imagegeneration

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class OpenAICompatibleImageClientTest {
    @Test
    fun b64JsonWinsOverUrlAndTrimsBaseUrl() = runBlocking {
        val bytes = pngBytes()
        var generationRequest: okhttp3.Request? = null
        val client = clientWithInterceptor { request ->
            generationRequest = request
            response(
                request,
                """{"data":[{"b64_json":"${Base64.getEncoder().encodeToString(bytes)}","url":"http://invalid.test/image"}]}"""
            )
        }

        val result = OpenAICompatibleImageClient(client).generate(
            ImageGenerationConfig("http://example.test/v1/", "secret", "model", "512x512"),
            "prompt"
        )

        assertArrayEquals(bytes, result.bytes)
        assertEquals("image/png", result.mimeType)
        assertEquals("http://example.test/v1/images/generations", generationRequest?.url.toString())
        assertEquals("Bearer secret", generationRequest?.header("Authorization"))
        val body = JsonParser.parseString(generationRequest!!.body!!.let { requestBody ->
            val buffer = okio.Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        }).asJsonObject
        assertEquals(setOf("model", "prompt", "size"), body.keySet())
        assertEquals("model", body.get("model").asString)
        assertEquals("prompt", body.get("prompt").asString)
        assertEquals("512x512", body.get("size").asString)
    }

    @Test
    fun urlFallbackDownloadsBytesAndOmitsAuthorizationForBlankKey() = runBlocking {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)
        val requests = mutableListOf<okhttp3.Request>()
        val client = clientWithInterceptor { request ->
            requests += request
            if (request.url.encodedPath.endsWith("/images/generations")) {
                response(request, """{"data":[{"url":"http://example.test/image.jpg"}]}""")
            } else {
                response(request, bytes, "application/octet-stream")
            }
        }

        val result = OpenAICompatibleImageClient(client).generate(
            ImageGenerationConfig("http://example.test/v1", "   ", "model", "1024x1024"),
            "prompt"
        )

        assertArrayEquals(bytes, result.bytes)
        assertEquals("image/jpeg", result.mimeType)
        assertFalse(requests.first().headers.names().contains("Authorization"))
        assertEquals("GET", requests[1].method)
    }

    @Test
    fun dataUrlPrefixIsAccepted() = runBlocking {
        val bytes = pngBytes()
        val dataUrl = "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"
        val client = clientWithInterceptor { request ->
            response(request, """{"data":[{"b64_json":"$dataUrl"}]}""")
        }

        val result = OpenAICompatibleImageClient(client).generate(
            ImageGenerationConfig("http://example.test", "key", "model", "size"),
            "prompt"
        )

        assertArrayEquals(bytes, result.bytes)
        assertEquals("image/png", result.mimeType)
    }

    private fun clientWithInterceptor(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun response(request: okhttp3.Request, body: String): Response =
        response(request, body.toByteArray(), "application/json")

    private fun response(request: okhttp3.Request, bytes: ByteArray, mimeType: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody(mimeType.toMediaType()))
            .build()

    private fun pngBytes(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3
    )
}
