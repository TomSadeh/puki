package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText

data class InstrumentDef(
    val id: String,
    val name: BilingualText,
    val emoji: String,
    val color: Color,
    val noteIndex: Int,
    @DrawableRes val drawableRes: Int = 0
)

val instruments = listOf(
    InstrumentDef("drum", BilingualText("תוף", "Drum"), "\uD83E\uDD41", Color(0xFFEF5350), 0, R.drawable.ic_instrument_drum),
    InstrumentDef("xylophone", BilingualText("קסילופון", "Xylophone"), "\uD83C\uDFB5", Color(0xFF42A5F5), 1, R.drawable.ic_instrument_xylophone),
    InstrumentDef("tambourine", BilingualText("תוף מרים", "Tambourine"), "\uD83E\uDE98", Color(0xFFFFEE58), 2, R.drawable.ic_instrument_tambourine),
    InstrumentDef("maracas", BilingualText("רעשנים", "Maracas"), "\uD83C\uDF89", Color(0xFF66BB6A), 3, R.drawable.ic_instrument_maracas)
)
