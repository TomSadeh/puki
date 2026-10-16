package io.github.tomsadeh.puki.audio

import io.github.tomsadeh.puki.R

enum class Sfx(val resId: Int) {
    POP(R.raw.snd_pop),
    SUCCESS(R.raw.snd_success),
    WHOOSH(R.raw.snd_whoosh),
    WRONG_GENTLE(R.raw.snd_wrong_gentle),
    DROP(R.raw.snd_drop),
    STAR_COLLECT(R.raw.snd_star_collect),
    DRUM(R.raw.snd_drum),
    XYLOPHONE(R.raw.snd_xylophone),
    TAMBOURINE(R.raw.snd_tambourine),
    MARACAS(R.raw.snd_maracas);

    companion object {
        fun preloadAll(sfxPlayer: SfxPlayer) {
            entries.forEach { sfx ->
                sfxPlayer.preload(sfx.name, sfx.resId)
            }
        }
    }
}
