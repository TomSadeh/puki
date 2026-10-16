package io.github.tomsadeh.puki.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Plays the child's recording back as a parrot would: higher and faster, by playing the 16 kHz
 * samples at a higher rate. No AI and no network, so it works anywhere.
 */
object EchoVoice {
    /** Plays [wav] (16 kHz mono, as [MicRecorder] makes it) [pitch] times higher; returns when done. */
    suspend fun play(wav: ByteArray, volume: Float, pitch: Float = 1.45f) = withContext(Dispatchers.IO) {
        if (wav.size <= 44) return@withContext
        val pcm = trimSilence(wav.copyOfRange(44, wav.size))
        if (pcm.size < MicRecorder.RATE / 5) return@withContext  // under a tenth of a second: nothing said
        val rate = (MicRecorder.RATE * pitch).toInt()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()
            delay(pcm.size / 2 * 1000L / rate + 150)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Cuts the quiet before and after the talking (keeping a short margin), so the echo comes right away. */
    private fun trimSilence(pcm: ByteArray): ByteArray {
        val samples = pcm.size / 2
        fun loud(i: Int): Boolean {
            val v = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()
            return abs(v) > 1500
        }
        var start = 0
        while (start < samples && !loud(start)) start++
        var end = samples - 1
        while (end > start && !loud(end)) end--
        if (start >= end) return ByteArray(0)
        val margin = MicRecorder.RATE / 12
        val from = maxOf(0, start - margin)
        val to = minOf(samples, end + margin)
        return pcm.copyOfRange(from * 2, to * 2)
    }
}
