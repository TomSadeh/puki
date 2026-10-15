package com.olamkatan.audio

import android.content.Context
import android.media.MediaPlayer

class MusicPlayer(val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var enabled = true

    fun play(resId: Int, loop: Boolean = true) {
        if (!enabled) return
        stop()
        mediaPlayer = MediaPlayer.create(context, resId)?.apply {
            isLooping = loop
            setVolume(0.4f, 0.4f)
            start()
        }
    }

    fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        if (!enabled) return
        mediaPlayer?.takeIf { !it.isPlaying }?.start()
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            pause()
        } else if (mediaPlayer != null) {
            resume()
        }
    }

    fun release() {
        stop()
    }
}
