package io.github.tomsadeh.puki.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Records the child talking to the parrot: 16 kHz mono 16-bit PCM (what Whisper
 * wants), returned as a WAV file. Stops on its own after [MAX_SECONDS].
 */
class MicRecorder {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false

    val isRecording get() = running

    /** Caller must hold the RECORD_AUDIO permission. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE))
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        pcm.reset()
        record = rec
        running = true
        rec.startRecording()
        worker = thread(name = "mic") {
            val buf = ByteArray(minBuf)
            val maxBytes = RATE * 2 * MAX_SECONDS
            while (running && pcm.size() < maxBytes) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) synchronized(pcm) { pcm.write(buf, 0, n) }
            }
        }
        return true
    }

    /** Stops recording and returns the WAV bytes (empty if nothing was recorded). */
    fun stop(): ByteArray {
        running = false
        worker?.join(500)
        record?.run {
            runCatching { stop() }
            release()
        }
        record = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        return if (data.isEmpty()) data else wav(data)
    }

    private fun wav(pcmData: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcmData.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcmData.size)
        }
        return header.array() + pcmData
    }

    companion object {
        const val RATE = 16000
        const val MAX_SECONDS = 8
    }
}
