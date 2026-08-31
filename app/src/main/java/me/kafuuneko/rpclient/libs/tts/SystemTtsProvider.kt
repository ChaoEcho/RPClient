package me.kafuuneko.rpclient.libs.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SystemTtsProvider(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val utteranceCounter = AtomicLong()
    private var textToSpeech: TextToSpeech? = null
    private var initialization: CompletableDeferred<TextToSpeech>? = null
    private var activeUtteranceId: String? = null
    private var activeContinuation: CancellableContinuation<Unit>? = null

    suspend fun getVoices(): List<TtsVoice> {
        val engine = getTextToSpeech()
        return engine.voices.orEmpty().map { voice ->
            TtsVoice(
                name = voice.name,
                languageTag = voice.locale.toLanguageTag(),
                displayName = voice.name
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    }

    suspend fun speak(request: SystemTtsRequest, onPlaybackStarted: () -> Unit = {}) {
        if (request.text.isBlank()) throw TtsException("Speech text is blank")
        if (request.text.length > TextToSpeech.getMaxSpeechInputLength()) {
            throw TtsException("Speech text exceeds the Android TTS input limit")
        }

        val engine = getTextToSpeech()
        val voice = selectVoice(engine, request)
        val localeResult = engine.setLanguage(voice.locale)
        if (localeResult == TextToSpeech.LANG_MISSING_DATA ||
            localeResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            throw TtsException("The selected system TTS language is unavailable")
        }
        if (!request.speechRate.isFinite() || request.speechRate <= 0f ||
            !request.pitch.isFinite() || request.pitch <= 0f
        ) {
            throw TtsException("System TTS speed and pitch must be positive")
        }

        val utteranceId = "rpclient-tts-${utteranceCounter.incrementAndGet()}"
        suspendCancellableCoroutine<Unit> { continuation ->
            synchronized(lock) {
                activeUtteranceId = utteranceId
                @Suppress("DEPRECATION")
                activeContinuation = continuation
            }
            continuation.invokeOnCancellation {
                val shouldStop = synchronized(lock) {
                    if (activeUtteranceId != utteranceId) {
                        false
                    } else {
                        activeUtteranceId = null
                        activeContinuation = null
                        true
                    }
                }
                if (shouldStop) runCatching { engine.stop() }
            }

            val result = try {
                engine.setSpeechRate(request.speechRate)
                engine.setPitch(request.pitch)
                engine.voice = voice
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) {
                        val isActive = synchronized(lock) { activeUtteranceId == id }
                        if (isActive) runCatching { onPlaybackStarted() }
                    }

                    override fun onDone(id: String) {
                        if (id != utteranceId) return
                        val continuationToResume = clearActive(id)
                        if (continuationToResume != null && !continuationToResume.isCancelled) {
                            continuationToResume.resume(Unit)
                        }
                    }

                    override fun onError(id: String) {
                        if (id != utteranceId) return
                        val continuationToResume = clearActive(id)
                        if (continuationToResume != null && !continuationToResume.isCancelled) {
                            continuationToResume.resumeWithException(TtsException("System TTS playback failed"))
                        }
                    }
                })
                engine.speak(request.text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            } catch (cancelled: CancellationException) {
                clearActive(utteranceId)
                throw cancelled
            } catch (error: Throwable) {
                val continuationToResume = clearActive(utteranceId)
                if (continuationToResume != null && !continuationToResume.isCancelled) {
                    continuationToResume.resumeWithException(TtsException("System TTS playback failed", error))
                }
                return@suspendCancellableCoroutine
            }
            if (result != TextToSpeech.SUCCESS) {
                val continuationToResume = clearActive(utteranceId)
                if (continuationToResume != null && !continuationToResume.isCancelled) {
                    continuationToResume.resumeWithException(TtsException("System TTS playback could not start"))
                }
            }
        }
    }

    fun stop() {
        val continuationToResume = synchronized(lock) {
            activeUtteranceId = null
            @Suppress("DEPRECATION")
            activeContinuation.also { activeContinuation = null }
        }
        runCatching { textToSpeech?.stop() }
        if (continuationToResume != null && !continuationToResume.isCancelled) {
            continuationToResume.resumeWithException(TtsException("System TTS playback stopped"))
        }
    }

    private fun clearActive(id: String): CancellableContinuation<Unit>? {
        return synchronized(lock) {
            if (activeUtteranceId != id) {
                null
            } else {
                activeUtteranceId = null
                @Suppress("DEPRECATION")
                activeContinuation.also { activeContinuation = null }
            }
        }
    }

    private suspend fun getTextToSpeech(): TextToSpeech {
        val state = synchronized(lock) {
            textToSpeech?.let { return it }
            initialization?.let { it to false } ?: CompletableDeferred<TextToSpeech>().also {
                initialization = it
            }.let { it to true }
        }
        val pending = state.first
        if (state.second) {
            try {
                lateinit var created: TextToSpeech
                created = TextToSpeech(appContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        synchronized(lock) {
                            textToSpeech = created
                            if (initialization === pending) initialization = null
                        }
                        pending.complete(created)
                    } else {
                        synchronized(lock) {
                            if (initialization === pending) initialization = null
                        }
                        pending.completeExceptionally(TtsException("System TTS initialization failed"))
                    }
                }
            } catch (error: Throwable) {
                synchronized(lock) {
                    if (initialization === pending) initialization = null
                }
                pending.completeExceptionally(TtsException("System TTS initialization failed", error))
            }
        }
        return pending.await()
    }

    private fun selectVoice(engine: TextToSpeech, request: SystemTtsRequest): Voice {
        val voices = engine.voices.orEmpty()
        if (voices.isEmpty()) throw TtsException("No system TTS voices are available")
        if (request.voiceName.isNotBlank()) {
            return voices.firstOrNull { it.name == request.voiceName }
                ?: throw TtsException("The selected system TTS voice is unavailable")
        }
        val requestedTag = request.languageTag.trim()
        if (requestedTag.isNotBlank()) {
            val requestedLocale = Locale.forLanguageTag(requestedTag)
            voices.firstOrNull { it.locale.toLanguageTag().equals(requestedTag, ignoreCase = true) }
                ?.let { return it }
            voices.firstOrNull { it.locale.language.equals(requestedLocale.language, ignoreCase = true) }
                ?.let { return it }
            throw TtsException("The selected system TTS voice is unavailable")
        }
        return voices.firstOrNull { it.locale == Locale.getDefault() } ?: voices.first()
    }
}
