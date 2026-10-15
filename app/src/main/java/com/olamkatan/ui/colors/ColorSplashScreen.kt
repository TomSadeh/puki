package com.olamkatan.ui.colors

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
    viewModel: ColorSplashViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    var showRoundSuccess by remember { mutableStateOf(false) }

    if (state.isLoading) return

    LaunchedEffect(state.roundIndex) {
        companion?.scene = "במשחק הצבעים: מחפשים את הצבע ${state.targetColor.name.he}."
        delay(500)
        voicePlayer.playSequence(
            listOf("voice_prompt_tap_the", "voice_color_${state.targetColor.id}"),
            language
        )
    }

    GameBackground(
        colors = listOf(Color(0xFFE1BEE7), Color(0xFFCE93D8), Color(0xFFBA68C8))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
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
            Text(
                text = BilingualText(
                    "תלחץ על ${state.targetColor.name.he}!",
                    "Tap the ${state.targetColor.name.en} one!",
                    heGirl = "תלחצי על ${state.targetColor.name.he}!"
                ).localized(),
                style = MaterialTheme.typography.headlineMedium,
                color = CloudWhite
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Color blobs
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.colorChoices.forEach { color ->
                    val blobReaction = rememberReactionState(state.roundIndex, color.id)
                    Box(modifier = Modifier.reaction(blobReaction)) {
                    ColorBlob(
                        color = color.color,
                        onClick = {
                            val correct = viewModel.onColorTapped(color.id)
                            if (correct) {
                                sfxPlayer.play(Sfx.SUCCESS)
                                if (companion != null) companion.react("במשחק הצבעים בחרו ${color.name.he}. זה נכון!") { voicePlayer.playPraise(language) } else voicePlayer.playPraise(language)
                                showRoundSuccess = true
                                scope.launch { blobReaction.celebrate() }
                                scope.launch {
                                    delay(if (companion?.isReady == true) 3400 else 2000)
                                    showRoundSuccess = false
                                    viewModel.nextRound()
                                }
                            } else {
                                sfxPlayer.play(Sfx.WRONG_GENTLE)
                                if (companion != null) companion.react("במשחק הצבעים בחרו ${color.name.he}, אבל חיפשנו ${state.targetColor.name.he}.") { voicePlayer.playSequence(listOf("voice_color_${color.id}", "voice_prompt_try_again"), language) } else voicePlayer.playSequence(listOf("voice_color_${color.id}", "voice_prompt_try_again"), language)
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
                onContinue = onBack,
                onHome = onBack
            )
        }
    }
}

@Composable
private fun ColorBlob(color: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "blob_scale"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.85f), color)
                )
            )
            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    )
}
