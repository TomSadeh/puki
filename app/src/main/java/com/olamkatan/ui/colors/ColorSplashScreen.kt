package com.olamkatan.ui.colors

import com.olamkatan.ui.components.hintGlow
import com.olamkatan.ui.components.noticeTouches
import com.olamkatan.ui.components.rememberHelp
import com.olamkatan.ui.components.rememberSessionSeed
import com.olamkatan.ui.components.scatterTilt
import com.olamkatan.ui.components.Scatter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import com.olamkatan.ui.components.popIn
import com.olamkatan.ui.components.idleBob
import com.olamkatan.ui.components.PromptBubble
import com.olamkatan.ui.components.Scene
import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.ui.components.reaction
import com.olamkatan.ui.components.rememberReactionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olamkatan.audio.Sfx
import com.olamkatan.data.models.BilingualText
import com.olamkatan.theme.CloudWhite
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.localized
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.common.LocalVoicePlayer
import com.olamkatan.ui.components.ConfettiOverlay
import com.olamkatan.ui.components.GameBackground
import com.olamkatan.ui.components.GameTopBar
import com.olamkatan.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ColorSplashScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: ColorSplashViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionSeed = rememberSessionSeed()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    var showRoundSuccess by remember { mutableStateOf(false) }

    if (state.isLoading) return

    // Stuck? Ask again after a quiet moment; after misses, the right balloon wiggles
    val help = rememberHelp(state.roundIndex, active = !showRoundSuccess) {
        voicePlayer.playSequence(listOf("voice_prompt_tap_the", "voice_color_${state.targetColor.id}"), language)
    }

    LaunchedEffect(state.roundIndex) {
        companion?.setScene("במשחק הצבעים: מחפשים את הצבע ${state.targetColor.name.he}.", "In the color game: we're looking for ${state.targetColor.name.en.lowercase()}.")
        delay(500)
        voicePlayer.playSequence(
            listOf("voice_prompt_tap_the", "voice_color_${state.targetColor.id}"),
            language
        )
    }

    GameBackground(
            scene = Scene.PLAYGROUND,
        colors = listOf(Color(0xFFE1BEE7), Color(0xFFCE93D8), Color(0xFFBA68C8))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().noticeTouches(help).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                onBack = onBack,
                roundIndex = state.roundIndex,
                totalRounds = state.totalRounds,
                starsEarned = state.totalStarsEarned
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Prompt
            PromptBubble(
                BilingualText(
                    "תלחץ על ${state.targetColor.name.he}!",
                    "Tap the ${state.targetColor.name.en} one!",
                    heGirl = "תלחצי על ${state.targetColor.name.he}!"
                ).localized()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Balloons floating about, high and low
            Scatter(seed = sessionSeed + state.roundIndex, modifier = Modifier.weight(1f).fillMaxWidth()) {
                state.colorChoices.forEachIndexed { i, color ->
                    val blobReaction = rememberReactionState(state.roundIndex, color.id)
                    Box(modifier = Modifier.popIn(i, state.roundIndex).idleBob(i).reaction(blobReaction)
                        .hintGlow(help.showHint && color.id == state.targetColor.id)
                        .graphicsLayer { rotationZ = scatterTilt(sessionSeed + state.roundIndex, i) }) {
                    ColorBlob(
                        color = color.color,
                        onClick = {
                            val correct = viewModel.onColorTapped(color.id) ?: return@ColorBlob
                            if (correct) {
                                sfxPlayer.play(Sfx.SUCCESS)
                                if (companion != null) companion.react("במשחק הצבעים בחרו ${color.name.he}. זה נכון!", "In the color game the child picked ${color.name.en.lowercase()}. That's right!") { voicePlayer.playPraise(language) } else voicePlayer.playPraise(language)
                                showRoundSuccess = true
                                scope.launch { blobReaction.celebrate() }
                                scope.launch {
                                    delay(if (companion?.isReady == true) 3400 else 2000)
                                    showRoundSuccess = false
                                    viewModel.nextRound()
                                }
                            } else {
                                sfxPlayer.play(Sfx.WRONG_GENTLE)
                                if (companion != null) companion.react("במשחק הצבעים בחרו ${color.name.he}, אבל חיפשנו ${state.targetColor.name.he}.", "In the color game the child picked ${color.name.en.lowercase()}, but we were looking for ${state.targetColor.name.en.lowercase()}.") { voicePlayer.playSequence(listOf("voice_color_${color.id}", "voice_prompt_try_again"), language) } else voicePlayer.playSequence(listOf("voice_color_${color.id}", "voice_prompt_try_again"), language)
                                help.missed()
                                scope.launch { blobReaction.shake() }
                            }
                        }
                    )
                    }
                }
            }
        }

        ConfettiOverlay(show = showRoundSuccess)

        if (state.sessionComplete) {
            RoundCompleteSheet(
                starsEarned = state.totalStarsEarned,
                onContinue = onPlayAgain,
                onHome = onBack
            )
        }
    }
}

/** A shiny balloon on a string, in [color]. */
@Composable
private fun ColorBlob(color: Color, onClick: () -> Unit) {
    val light = lerp(color, Color.White, 0.4f)
    val dark = lerp(color, Color.Black, 0.25f)
    Canvas(
        modifier = Modifier
            .size(width = 170.dp, height = 250.dp)
            .clickable(onClick = onClick)
    ) {
        val w = size.width
        val h = size.height
        val bodyW = w * 0.88f
        val bodyH = h * 0.7f
        val left = (w - bodyW) / 2
        val string = Path().apply {
            moveTo(w / 2, bodyH + h * 0.04f)
            cubicTo(w * 0.36f, bodyH + h * 0.11f, w * 0.64f, bodyH + h * 0.2f, w * 0.47f, h)
        }
        drawPath(string, Color(0xFF8D6E63), style = Stroke(3.dp.toPx()))
        val knot = Path().apply {
            moveTo(w / 2, bodyH - 4f)
            lineTo(w / 2 - 11.dp.toPx(), bodyH + h * 0.045f)
            lineTo(w / 2 + 11.dp.toPx(), bodyH + h * 0.045f)
            close()
        }
        drawPath(knot, dark)
        drawOval(Color.Black.copy(alpha = 0.12f), Offset(left + 7.dp.toPx(), 9.dp.toPx()), Size(bodyW, bodyH))
        drawOval(
            Brush.radialGradient(listOf(light, color, dark), center = Offset(w * 0.38f, bodyH * 0.32f), radius = bodyW * 0.95f),
            Offset(left, 0f), Size(bodyW, bodyH)
        )
        drawOval(Color.White.copy(alpha = 0.6f), Offset(left + bodyW * 0.17f, bodyH * 0.12f), Size(bodyW * 0.2f, bodyH * 0.27f))
    }
}
