package com.olamkatan.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SfxPlayer(private val context: Context) {

    var enabled = true

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedSounds = mutableMapOf<String, Int>()

    fun preload(name: String, resId: Int) {
        loadedSounds[name] = soundPool.load(context, resId, 1)
    }

    /** The parent's sound-effects volume, 0..1 (parent settings). */
    var level = 1f

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!enabled || level <= 0f) return
        loadedSounds[sfx.name]?.let { soundId ->
            soundPool.play(soundId, volume * level, volume * level, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
