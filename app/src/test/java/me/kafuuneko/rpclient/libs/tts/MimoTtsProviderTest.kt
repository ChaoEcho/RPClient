package me.kafuuneko.rpclient.libs.tts

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class MimoTtsProviderTest {
    @Test
    fun configuredModelIsSentInRequestPayload() = runBlocking {
        var capturedRequest: Request? = null
        val client = clientWithHandler { request ->
            capturedRequest = request
            response(
                request,
                code = 400,
                body = """{"error":{"message":"test response"}}"""
            )
        }
        val output = File.createTempFile("mimo-tts", ".wav")
        try {
            val result = runCatching {
                MimoTtsProvider(client, Gson()).synthesize(
                    request = request(streaming = false),
                    outputFile = output
                )
            }

            assertTrue(result.exceptionOrNull() is TtsException)
            val body = requestBodyJson(capturedRequest!!)
            assertEquals("custom-tts-model", body.get("model").asString)
            assertEquals("wav", body.getAsJsonObject("audio").get("format").asString)
            assertFalse(body.has("stream"))
        } finally {
            output.delete()
        }
    }

    @Test
    fun synthesizeWritesCompleteDecodedWavPayload() = runBlocking {
        val wav = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            1, 2, 3, 4
        )
        val client = clientWithHandler { request ->
            response(
                request,
                body = """{"choices":[{"message":{"audio":{"data":"${encode(wav)}"}}}]}"""
            )
        }
        val output = File.createTempFile("mimo-tts", ".wav")
        try {
            MimoTtsProvider(client, Gson()).synthesize(
                request = request(streaming = false),
                outputFile = output
            )
            assertArrayEquals(wav, output.readBytes())
        } finally {
            output.delete()
        }
    }

    @Test
    fun streamSendsPcm16PayloadAndEmitsAudioChunksInOrder() = runBlocking {
        var capturedRequest: Request? = null
        val firstChunk = byteArrayOf(1, 2)
        val secondChunk = byteArrayOf(3, 4, 5)
        val client = clientWithHandler { request ->
            capturedRequest = request
            response(
                request,
                body = sse(
                    ": keep-alive",
                    "event: message",
                    "data: {\"choices\":[{\"message\":{\"audio\":{\"data\":\"${encode(byteArrayOf(9, 9))}\"}}}]}",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"ignored\"}}]}",
                    "data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"${encode(firstChunk)}\"}}}]}",
                    "retry: 1000",
                    "data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"${encode(secondChunk)}\"}}}]}",
                    "data: [DONE]"
                )
            )
        }
        val received = mutableListOf<ByteArray>()

        MimoTtsProvider(client, Gson()).stream(request(streaming = true)) { chunk ->
            received += chunk
        }

        assertEquals(2, received.size)
        assertArrayEquals(firstChunk, received[0])
        assertArrayEquals(secondChunk, received[1])

        val body = requestBodyJson(capturedRequest!!)
        assertEquals("custom-tts-model", body.get("model").asString)
        assertEquals("pcm16", body.getAsJsonObject("audio").get("format").asString)
        assertEquals("mimo_default", body.getAsJsonObject("audio").get("voice").asString)
        assertEquals(0.8f, body.get("temperature").asFloat)
        assertTrue(body.get("stream").asBoolean)
        val messages = body.getAsJsonArray("messages")
        assertEquals(2, messages.size())
        assertEquals("user", messages[0].asJsonObject.get("role").asString)
        assertEquals("speak carefully", messages[0].asJsonObject.get("content").asString)
        assertEquals("assistant", messages[1].asJsonObject.get("role").asString)
        assertEquals("hello", messages[1].asJsonObject.get("content").asString)
        assertEquals("https://example.test/v1/chat/completions", capturedRequest!!.url.toString())
        assertEquals("secret", capturedRequest!!.header("api-key"))
        assertEquals("application/json", capturedRequest!!.header("Content-Type")?.substringBefore(';'))
    }

    @Test
    fun malformedJsonEventFailsAsInvalidStreamResponse() = runBlocking {
        val client = clientWithHandler { request ->
            response(request, body = sse("data: {not-json", "data: [DONE]"))
        }

        val error = runCatching {
            MimoTtsProvider(client, Gson()).stream(request(streaming = true)) { }
        }.exceptionOrNull()

        assertTrue(error is TtsException)
        assertTrue(error!!.message.orEmpty().contains("invalid stream response"))
        assertNotNull(error.cause)
    }

    @Test
    fun invalidBase64ChunkFailsWithSpecificError() = runBlocking {
        val client = clientWithHandler { request ->
            response(
                request,
                body = sse(
                    "data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"%%%\"}}}]}",
                    "data: [DONE]"
                )
            )
        }

        val error = runCatching {
            MimoTtsProvider(client, Gson()).stream(request(streaming = true)) { }
        }.exceptionOrNull()

        assertTrue(error is TtsException)
        assertTrue(error!!.message.orEmpty().contains("invalid Base64 audio chunk"))
        assertNotNull(error.cause)
    }

    @Test
    fun nonAudioStreamAndDoneWithoutAudioFailsAsInvalidStreamResponse() = runBlocking {
        val client = clientWithHandler { request ->
            response(
                request,
                body = sse(
                    "event: message",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"text only\"}}]}",
                    "data: [DONE]"
                )
            )
        }

        val error = runCatching {
            MimoTtsProvider(client, Gson()).stream(request(streaming = true)) { }
        }.exceptionOrNull()

        assertTrue(error is TtsException)
        assertTrue(error!!.message.orEmpty().contains("invalid stream response"))
    }

    @Test
    fun streamHttpFailureIncludesStatusAndServerDetail() = runBlocking {
        val client = clientWithHandler { request ->
            response(
                request,
                code = 429,
                message = "Too Many Requests",
                body = """{"error":{"message":"quota exhausted"}}"""
            )
        }

        val error = runCatching {
            MimoTtsProvider(client, Gson()).stream(request(streaming = true)) { }
        }.exceptionOrNull()

        assertTrue(error is TtsException)
        assertEquals("MiMo request failed (HTTP 429): quota exhausted", error!!.message)
    }

    private fun request(streaming: Boolean): MimoTtsRequest = MimoTtsRequest(
        text = "hello",
        baseUrl = "https://example.test/v1/",
        apiKey = "secret",
        model = "custom-tts-model",
        voice = "mimo_default",
        instructions = "speak carefully",
        temperature = 0.8f,
        streaming = streaming
    )

    private fun clientWithHandler(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun response(
        request: Request,
        code: Int = 200,
        message: String = if (code == 200) "OK" else "Error",
        body: String
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun requestBodyJson(request: Request) = JsonParser.parseString(
        request.body!!.let { requestBody ->
            Buffer().use { buffer ->
                requestBody.writeTo(buffer)
                buffer.readUtf8()
            }
        }
    ).asJsonObject

    private fun sse(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
