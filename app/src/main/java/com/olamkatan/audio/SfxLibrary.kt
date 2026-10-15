package com.olamkatan.audio

import com.olamkatan.R

enum class Sfx(val resId: Int) {
    POP(R.raw.snd_pop),
    SUCCESS(R.raw.snd_success),
    WHOOSH(R.raw.snd_whoosh),
    WRONG_GENTLE(R.raw.snd_wrong_gentle),
    DROP(R.raw.snd_drop),
    STAR_COLLECT(R.raw.snd_star_collect);

    companion object {
        fun preloadAll(sfxPlayer: SfxPlayer) {
            entries.forEach { sfx ->
                sfxPlayer.preload(sfx.name, sfx.resId)
            }
        }
    }
}
