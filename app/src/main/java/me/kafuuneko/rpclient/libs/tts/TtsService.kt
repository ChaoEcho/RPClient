package me.kafuuneko.rpclient.libs.tts

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.AppModel
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TtsService(
    private val cache: TtsAudioCache,
    private val systemTtsProvider: SystemTtsProvider,
    private val mimoTtsProvider: MimoTtsProvider,
    private val azureTtsProvider: AzureTtsProvider
) {
    private val lock = Any()
    private var nextSessionId = 0L
    private var activeSessionId: Long? = null
    private var activePlayer: MediaPlayer? = null
    private var playbackContinuation: CancellableContinuation<Unit>? = null

    suspend fun speak(text: String, onPlaybackStarted: () -> Unit = {}) {
        if (text.isBlank()) return
        val sessionId = beginSession()
        val providerType = TtsProviderType.fromPersistedValue(AppModel.ttsProvider)
        try {
            when (providerType) {
                TtsProviderType.System -> systemTtsProvider.speak(
                    SystemTtsRequest(
                        text = text,
                        languageTag = AppModel.ttsSystemLanguageTag,
                        voiceName = AppModel.ttsSystemVoiceName,
                        speechRate = AppModel.ttsSystemSpeechRate,
                        pitch = AppModel.ttsSystemPitch
                    ),
                    onPlaybackStarted
                )
                TtsProviderType.Mimo,
                TtsProviderType.Azure -> {
                    val providerAndRequest = providerAndRequest(providerType, text)
                    val audioFile = try {
                        cache.getOrCreate(providerType, providerAndRequest.second) { outputFile ->
                            providerAndRequest.first.synthesize(providerAndRequest.second, outputFile)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        throw synthesisFailure(error)
                    }
                    checkActive(sessionId)
                    play(sessionId, audioFile, onPlaybackStarted)
                }
            }
        } finally {
            finishWithoutPlayback(sessionId)
        }
    }

    suspend fun getSystemVoices(): List<TtsVoice> = systemTtsProvider.getVoices()

    fun stop() {
        val (player, continuation) = synchronized(lock) {
            activeSessionId = null
            val oldPlayer = activePlayer
            val oldContinuation = playbackContinuation
            activePlayer = null
            playbackContinuation = null
            oldPlayer to oldContinuation
        }
        systemTtsProvider.stop()
        mimoTtsProvider.stop()
        azureTtsProvider.stop()
        player?.let { releasePlayer(it) }
        if (continuation != null && !continuation.isCancelled) {
            continuation.resumeWithException(TtsException("Speech playback stopped"))
        }
    }

    private fun beginSession(): Long {
        val state = synchronized(lock) {
            nextSessionId += 1L
            val sessionId = nextSessionId
            activeSessionId = sessionId
            val oldPlayer = activePlayer
            val oldContinuation = playbackContinuation
            activePlayer = null
            playbackContinuation = null
            Triple(sessionId, oldPlayer, oldContinuation)
        }
        systemTtsProvider.stop()
        mimoTtsProvider.stop()
        azureTtsProvider.stop()
        state.second?.let { releasePlayer(it) }
        state.third?.let {
            if (!it.isCancelled) it.resumeWithException(TtsException("Speech playback replaced"))
        }
        return state.first
    }

    private fun providerAndRequest(
        providerType: TtsProviderType,
        text: String
    ): Pair<TtsProvider, TtsSynthesisRequest> {
        return when (providerType) {
            TtsProviderType.Mimo -> mimoTtsProvider to MimoTtsRequest(
                text = text,
                baseUrl = AppModel.ttsMimoBaseUrl,
                apiKey = AppModel.ttsMimoApiKey,
                voice = AppModel.ttsMimoVoice,
                instructions = AppModel.ttsMimoInstructions,
                temperature = AppModel.ttsMimoTemperature
            )
            TtsProviderType.Azure -> azureTtsProvider to AzureTtsRequest(
                text = text,
                region = AppModel.ttsAzureRegion,
                apiKey = AppModel.ttsAzureApiKey,
                voice = AppModel.ttsAzureVoice,
                speechRate = AppModel.ttsAzureSpeechRate
            )
            TtsProviderType.System -> error("System TTS does not use file synthesis")
        }
    }

    private fun checkActive(sessionId: Long) {
        synchronized(lock) {
            if (activeSessionId != sessionId) throw TtsException("Speech playback stopped")
        }
    }

    private suspend fun play(
        sessionId: Long,
        audioFile: File,
        onPlaybackStarted: () -> Unit
    ) {
        val player = MediaPlayer()
        try {
            withContext(Dispatchers.IO) {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(audioFile.absolutePath)
                player.prepare()
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { stopSession(sessionId) }
                var started = false
                synchronized(lock) {
                    if (!continuation.isActive || activeSessionId != sessionId) {
                        continuation.resumeWithException(TtsException("Speech playback stopped"))
                        return@synchronized
                    }
                    activePlayer = player
                    playbackContinuation = continuation
                    player.setOnCompletionListener { completePlayback(sessionId, null) }
                    player.setOnErrorListener { _, what, extra ->
                        completePlayback(sessionId, IllegalStateException("MediaPlayer error ($what/$extra)"))
                        true
                    }
                    try {
                        player.start()
                        started = true
                    } catch (error: Throwable) {
                        activePlayer = null
                        playbackContinuation = null
                        continuation.resumeWithException(error)
                    }
                }
                if (started) runCatching { onPlaybackStarted() }
            }
        } catch (cancelled: CancellationException) {
            releasePlayer(player)
            throw cancelled
        } catch (error: Throwable) {
            releasePlayer(player)
            throw playbackFailure(error)
        }
    }

    private fun completePlayback(sessionId: Long, error: Throwable?) {
        val (player, continuation) = synchronized(lock) {
            if (activeSessionId != sessionId) return
            activeSessionId = null
            val currentPlayer = activePlayer
            val currentContinuation = playbackContinuation
            activePlayer = null
            playbackContinuation = null
            currentPlayer to currentContinuation
        }
        player?.let { releasePlayer(it) }
        if (continuation != null && !continuation.isCancelled) {
            if (error == null) continuation.resume(Unit) else continuation.resumeWithException(error)
        }
    }

    private fun stopSession(sessionId: Long) {
        val shouldStop = synchronized(lock) { activeSessionId == sessionId }
        if (shouldStop) stop()
    }

    private fun finishWithoutPlayback(sessionId: Long) {
        synchronized(lock) {
            if (activeSessionId == sessionId) activeSessionId = null
        }
    }

    private fun synthesisFailure(error: Throwable): TtsException {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName ?: "Unknown error"
        return TtsException("Speech synthesis failed: $detail", error)
    }

    private fun playbackFailure(error: Throwable): TtsException {
        if (error is TtsException && error.message?.startsWith("Speech playback failed:") == true) {
            return error
        }
        if (error is TtsException && error.message == "Speech playback stopped") {
            return error
        }
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName ?: "Unknown error"
        return TtsException("Speech playback failed: $detail", error)
    }

    private fun releasePlayer(player: MediaPlayer) {
        runCatching { player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
    }
}
