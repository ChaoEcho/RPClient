package me.kafuuneko.rpclient.libs.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class PcmAudioPlayer {
    private val lock = Any()
    private var activePlayback: Playback? = null

    suspend fun play(
        producer: suspend (write: suspend (ByteArray) -> Unit) -> Unit,
        onPlaybackStarted: () -> Unit = {}
    ) {
        val playback = Playback(currentCoroutineContext()[Job])
        val previousPlayback = synchronized(lock) {
            activePlayback?.also { it.stopped = true }.also {
                activePlayback = playback
            }
        }
        previousPlayback?.stop()

        val started = AtomicBoolean(false)
        val writtenFrames = AtomicLong(0L)
        var pendingByte: Byte? = null
        try {
            val track = createAudioTrack()
            synchronized(playback) {
                // Attach even after a concurrent stop so the finally block can release it.
                playback.audioTrack = track
            }
            if (playback.stopped) throw stoppedPlayback()

            try {
                track.play()
            } catch (error: Throwable) {
                throw TtsException("PCM AudioTrack playback failed", error)
            }
            ensurePlaybackActive(playback)

            producer { chunk ->
                ensurePlaybackActive(playback)
                if (chunk.isEmpty()) return@producer

                val data = pendingByte?.let { firstByte ->
                    ByteArray(chunk.size + 1).also { merged ->
                        merged[0] = firstByte
                        chunk.copyInto(merged, destinationOffset = 1)
                    }
                } ?: chunk
                pendingByte = if (data.size % BYTES_PER_FRAME == 0) {
                    null
                } else {
                    data.last()
                }
                val completeByteCount = data.size - (data.size % BYTES_PER_FRAME)
                if (completeByteCount == 0) return@producer

                writeChunk(
                    playback = playback,
                    audioTrack = track,
                    bytes = data,
                    byteCount = completeByteCount,
                    started = started,
                    writtenFrames = writtenFrames,
                    onPlaybackStarted = onPlaybackStarted
                )
            }
            if (pendingByte != null) {
                throw TtsException("PCM audio data ended on an incomplete frame")
            }
            awaitPlaybackDrain(playback, track, writtenFrames.get())
        } catch (cancelled: CancellationException) {
            if (playback.stopped) throw stoppedPlayback(cancelled)
            throw cancelled
        } catch (error: TtsException) {
            if (playback.stopped) throw stoppedPlayback(error)
            throw error
        } finally {
            synchronized(lock) {
                if (activePlayback === playback) activePlayback = null
            }
            release(playback)
        }
    }

    fun stop() {
        val playback = synchronized(lock) {
            activePlayback?.also {
                it.stopped = true
                activePlayback = null
            }
        }
        playback?.stop()
    }

    private fun createAudioTrack(): AudioTrack {
        val minBufferSize = try {
            AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_MASK,
                ENCODING
            )
        } catch (error: Throwable) {
            throw TtsException("PCM AudioTrack initialization failed", error)
        }
        if (minBufferSize <= 0) {
            throw TtsException(
                "PCM AudioTrack initialization failed: invalid buffer size ($minBufferSize)"
            )
        }
        val bufferSize = minBufferSize.coerceAtLeast(MIN_BUFFER_SIZE).let { size ->
            if (size % BYTES_PER_FRAME == 0) size else size + 1
        }
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(ENCODING)
                        .setChannelMask(CHANNEL_MASK)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (error: Throwable) {
            throw TtsException("PCM AudioTrack initialization failed", error)
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            throw TtsException("PCM AudioTrack initialization failed: track is not initialized")
        }
        return track
    }

    private suspend fun writeChunk(
        playback: Playback,
        audioTrack: AudioTrack,
        bytes: ByteArray,
        byteCount: Int,
        started: AtomicBoolean,
        writtenFrames: AtomicLong,
        onPlaybackStarted: () -> Unit
    ) {
        withContext(Dispatchers.IO) {
            var offset = 0
            while (offset < byteCount) {
                ensurePlaybackActive(playback)
                val written = try {
                    audioTrack.write(
                        bytes,
                        offset,
                        byteCount - offset,
                        AudioTrack.WRITE_BLOCKING
                    )
                } catch (error: Throwable) {
                    throw TtsException("PCM AudioTrack write failed", error)
                }
                if (written <= 0 || written > byteCount - offset) {
                    throw TtsException("PCM AudioTrack write failed (result=$written)")
                }
                if (started.compareAndSet(false, true)) {
                    runCatching { onPlaybackStarted() }
                }
                if (written % BYTES_PER_FRAME != 0) {
                    throw TtsException("PCM AudioTrack write failed: incomplete frame")
                }
                offset += written
                writtenFrames.addAndGet(written.toLong() / BYTES_PER_FRAME)
            }
        }
    }

    private suspend fun awaitPlaybackDrain(
        playback: Playback,
        audioTrack: AudioTrack,
        writtenFrames: Long
    ) {
        if (writtenFrames <= 0L) return
        while (true) {
            ensurePlaybackActive(playback)
            val playbackHead = audioTrack.playbackHeadPosition.toLong() and UINT_MASK
            if (playbackHead >= writtenFrames) return
            delay(DRAIN_POLL_MILLIS)
        }
    }

    private suspend fun ensurePlaybackActive(playback: Playback) {
        if (playback.stopped) throw stoppedPlayback()
        currentCoroutineContext().ensureActive()
    }

    private fun release(playback: Playback) {
        val audioTrack = synchronized(playback) {
            playback.audioTrack.also { playback.audioTrack = null }
        }
        audioTrack?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
    }

    private fun stoppedPlayback(cause: Throwable? = null): TtsException {
        return TtsException("Speech playback stopped", cause)
    }

    private class Playback(val job: Job?) {
        @Volatile
        var stopped: Boolean = false
        var audioTrack: AudioTrack? = null

        fun stop() {
            job?.cancel()
            val audioTrack = synchronized(this) {
                this.audioTrack.also { this.audioTrack = null }
            }
            audioTrack?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_FRAME = 2
        const val MIN_BUFFER_SIZE = 4_096
        const val DRAIN_POLL_MILLIS = 20L
        const val UINT_MASK = 0xffff_ffffL
    }
}
