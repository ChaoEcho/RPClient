package me.kafuuneko.rpclient.libs.tts

import java.io.File

enum class TtsProviderType(val persistedValue: String) {
    System("system"),
    Mimo("mimo"),
    Azure("azure");

    companion object {
        fun fromPersistedValue(value: String): TtsProviderType {
            return entries.firstOrNull { it.persistedValue == value } ?: System
        }
    }
}

data class TtsVoice(
    val name: String,
    val languageTag: String,
    val displayName: String
)

data class TtsSpeakOptions(
    val mimoVoiceOverride: String? = null
)

data class SystemTtsRequest(
    val text: String,
    val languageTag: String,
    val voiceName: String,
    val speechRate: Float,
    val pitch: Float
)

sealed interface TtsSynthesisRequest {
    val text: String
}

data class MimoTtsRequest(
    override val text: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val voice: String,
    val instructions: String,
    val temperature: Float,
    val streaming: Boolean
) : TtsSynthesisRequest

data class AzureTtsRequest(
    override val text: String,
    val region: String,
    val apiKey: String,
    val voice: String,
    val speechRate: Float
) : TtsSynthesisRequest

interface TtsProvider {
    suspend fun synthesize(request: TtsSynthesisRequest, outputFile: File)
    fun stop()
}

class TtsException(message: String, cause: Throwable? = null) : Exception(message, cause)
