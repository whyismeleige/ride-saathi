package com.ridesaathi.app.core.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal sealed interface CaptureEvent {
    data class Ready(val bargeInAvailable: Boolean) : CaptureEvent
    data object Started : CaptureEvent
    data class Utterance(val pcm: ByteArray) : CaptureEvent
    data object Failed : CaptureEvent
    data object TooLong : CaptureEvent
}

/** One microphone owner, local WebRTC VAD and hardware AEC; no audio files. */
internal class VoiceCapture(private val context: Context, private val playback: () -> Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    @Volatile private var recorder: AudioRecord? = null
    private var closed = false

    fun start(onEvent: (CaptureEvent) -> Unit) {
        stop()
        if (closed) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onEvent(CaptureEvent.Failed); return
        }
        val token = generation.get()
        fun emit(event: CaptureEvent) = handler.post { if (generation.get() == token && !closed) onEvent(event) }
        worker.execute {
            var record: AudioRecord? = null
            var aec: AcousticEchoCanceler? = null
            var noise: NoiseSuppressor? = null
            val manager = context.getSystemService(AudioManager::class.java)
            val oldMode = manager.mode
            var changedMode = false
            var speakerRoute = false
            @Suppress("DEPRECATION")
            val oldSpeaker = manager.isSpeakerphoneOn
            try {
                if (generation.get() != token) return@execute
                // Do not take over an existing phone/VoIP call.
                check(oldMode == AudioManager.MODE_NORMAL)
                manager.mode = AudioManager.MODE_IN_COMMUNICATION
                changedMode = true
                if (Build.VERSION.SDK_INT >= 31) {
                    val route = manager.communicationDevice
                    if (route == null || route.type in listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)) {
                        manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                            ?.let { speakerRoute = manager.setCommunicationDevice(it) }
                    }
                } else {
                    val external = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                        it.type in listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET)
                    }
                    if (!external) {
                        @Suppress("DEPRECATION")
                        manager.isSpeakerphoneOn = true
                        speakerRoute = true
                    }
                }
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 640 * 8))
                check(record.state == AudioRecord.STATE_INITIALIZED)
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noise = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
                }
                val echoCancelled = aec?.enabled == true
                recorder = record
                if (generation.get() != token) return@execute
                record.startRecording()
                check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                emit(CaptureEvent.Ready(echoCancelled))
                val segmenter = UtteranceSegmenter()
                VadWebRTC(SampleRate.SAMPLE_RATE_16K, FrameSize.FRAME_SIZE_320, Mode.VERY_AGGRESSIVE).use { vad ->
                    val frame = ByteArray(640)
                    while (generation.get() == token) {
                        var offset = 0
                        while (offset < frame.size && generation.get() == token) {
                            val count = record.read(frame, offset, frame.size - offset)
                            check(count > 0)
                            offset += count
                        }
                        if (generation.get() != token) break
                        val event = segmenter.accept(frame, vad.isSpeech(frame), playback(), echoCancelled)
                        if (event.started) emit(CaptureEvent.Started)
                        event.pcm?.let { emit(CaptureEvent.Utterance(it)) }
                        if (event.tooLong) emit(CaptureEvent.TooLong)
                    }
                }
            } catch (_: Exception) {
                emit(CaptureEvent.Failed)
            } catch (_: LinkageError) {
                emit(CaptureEvent.Failed)
            } finally {
                if (recorder === record) recorder = null
                runCatching { record?.stop() }
                noise?.release(); aec?.release(); record?.release()
                if (changedMode && manager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                    if (speakerRoute) {
                        if (Build.VERSION.SDK_INT >= 31) manager.clearCommunicationDevice()
                        else {
                            @Suppress("DEPRECATION")
                            manager.isSpeakerphoneOn = oldSpeaker
                        }
                    }
                    manager.mode = oldMode
                }
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        runCatching { recorder?.stop() }
    }

    fun close() {
        closed = true
        stop()
        worker.shutdown()
        handler.removeCallbacksAndMessages(null)
    }
}
