package me.kafuuneko.rpclient.libs.tts

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MimoTtsProviderTest {
    @Test
    fun configuredModelIsSentInRequestPayload() = runBlocking {
        var capturedRequest: okhttp3.Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body(
                        """{"error":{"message":"test response"}}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            })
            .build()
        val output = File.createTempFile("mimo-tts", ".wav")
        try {
            val result = runCatching {
                MimoTtsProvider(client, Gson()).synthesize(
                    MimoTtsRequest(
                        text = "hello",
                        baseUrl = "https://example.test/v1",
                        apiKey = "secret",
                        model = "custom-tts-model",
                        voice = "mimo_default",
                        instructions = "",
                        temperature = 0.8f
                    ),
                    output
                )
            }

            assertTrue(result.exceptionOrNull() is TtsException)
            val body = capturedRequest!!.body!!.let { requestBody ->
                Buffer().use { buffer ->
                    requestBody.writeTo(buffer)
                    JsonParser.parseString(buffer.readUtf8()).asJsonObject
                }
            }
            assertEquals("custom-tts-model", body.get("model").asString)
        } finally {
            output.delete()
        }
    }
}
