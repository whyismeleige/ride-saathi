package com.ridesaathi.app.core.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.ridesaathi.app.data.speech.SpeechAudioRequest
import java.util.Locale
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor

/** Fetch on a worker; prepare/play on main. Audio lives only in a bounded memory buffer. */
internal class OnlineSpeechOutput(context: Context, private val baseUrl: String) : SpeechOutput {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes get() = AudioAttributes.Builder()
        .setUsage(if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION)
            AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var generation = 0L
    private var closed = false
    private var download: SpeechAudioRequest? = null
    private var task: Future<*>? = null
    private var player: MediaPlayer? = null
    private var source: MemoryAudio? = null
    private var focus: AudioFocusRequest? = null
    private var done: ((SpeechResult) -> Unit)? = null
    private var deadline: Runnable? = null

    override fun speak(text: String, locale: Locale, id: String, done: (SpeechResult) -> Unit) {
        stop()
        if (closed || baseUrl.isBlank()) { done(SpeechResult.FAILED); return }
        this.done = done
        val token = generation
        val request = SpeechAudioRequest(baseUrl)
        download = request
        // Includes download and decoder preparation, not the duration of speech.
        deadline = Runnable { finish(token, SpeechResult.FAILED) }.also { handler.postDelayed(it, 12_000) }
        task = worker.submit {
            val bytes = try { request.fetch(text, locale.language) } catch (_: Exception) { null }
            handler.post {
                if (token != generation || closed) return@post
                download = null
                task = null
                if (bytes == null) finish(token, SpeechResult.FAILED) else prepare(token, bytes)
            }
        }
    }

    private fun prepare(token: Long, bytes: ByteArray) {
        try {
            val audio = MemoryAudio(bytes)
            source = audio
            val media = MediaPlayer()
            player = media
            media.setAudioAttributes(attributes)
            media.setDataSource(audio)
            media.setOnCompletionListener { finish(token, SpeechResult.COMPLETED) }
            media.setOnErrorListener { _, _, _ -> finish(token, SpeechResult.FAILED); true }
            media.setOnPreparedListener {
                if (token == generation && !closed) {
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener({ change ->
                            if (change == AudioManager.AUDIOFOCUS_LOSS ||
                                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                            ) finish(token, SpeechResult.CANCELLED)
                        }, handler).build()
                    focus = request
                    if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        deadline?.let { handler.removeCallbacks(it) }
                        deadline = null
                        try { media.start() } catch (_: IllegalStateException) { finish(token, SpeechResult.FAILED) }
                    } else finish(token, SpeechResult.CANCELLED)
                }
            }
            media.prepareAsync()
        } catch (_: Exception) {
            finish(token, SpeechResult.FAILED)
        }
    }

    private fun finish(token: Long, result: SpeechResult) {
        if (token != generation) return
        val callback = done
        stop()
        callback?.invoke(result)
    }

    override fun stop() {
        generation++
        done = null
        deadline?.let { handler.removeCallbacks(it) }
        deadline = null
        download?.cancel()
        download = null
        task?.cancel(true)
        task = null
        player?.release()
        player = null
        source?.close()
        source = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }

    override fun close() {
        closed = true
        stop()
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }

    private class MemoryAudio(private var bytes: ByteArray) : MediaDataSource() {
        @Synchronized override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0) return -1
            if (size == 0) return 0
            if (position >= bytes.size) return -1
            val count = minOf(size, bytes.size - position.toInt())
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }
        @Synchronized override fun getSize(): Long = bytes.size.toLong()
        @Synchronized override fun close() { bytes = ByteArray(0) }
    }
}
