package com.olamkatan.ui.counting

import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.ui.components.reaction
import com.olamkatan.ui.components.ReactionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olamkatan.audio.Sfx
import com.olamkatan.theme.CloudWhite
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.common.LocalVoicePlayer
import com.olamkatan.ui.components.ConfettiOverlay
import com.olamkatan.ui.components.GameBackground
import com.olamkatan.ui.components.GameTopBar
import com.olamkatan.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CountingGardenScreen(
    onBack: () -> Unit,
    viewModel: CountingGardenViewModel = viewModel()
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
        companion?.scene = "במשחק הספירה: סופרים ${state.currentRound.itemName.he}."
        delay(500)
        voicePlayer.play("voice_prompt_count_along", language)
    }

    GameBackground(
        colors = listOf(Color(0xFFC8E6C9), Color(0xFFA5D6A7), Color(0xFF81C784))
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

            Spacer(modifier = Modifier.height(16.dp))

            // Big number display
            Text(
                text = "${state.currentCount}",
                fontSize = 80.sp,
                color = CloudWhite,
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Garden area — shows placed items
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFE8F5E9), Color(0xFFA5D6A7))))
                    .border(BorderStroke(6.dp, Color.White.copy(alpha = 0.8f)), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                // A faded outline for every item to count; each tap fills the next one in
                val target = state.currentRound.targetNumber
                val itemReactions = remember(state.roundIndex) { List(target) { ReactionState() } }
                LaunchedEffect(state.roundComplete) {
                    // All the counted items hop one after another
                    if (state.roundComplete) itemReactions.forEachIndexed { i, r -> launch { delay(i * 90L); r.celebrate() } }
                }
                val itemSize = if (target <= 5) 130.dp else 92.dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(target) { i ->
                        val filled = i < state.currentCount
                        val itemScale by animateFloatAsState(
                            targetValue = if (filled) 1f else 0.75f,
                            animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
                            label = "item_scale_$i"
                        )
                        Image(
                            painter = painterResource(state.currentRound.itemIcon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(itemSize)
                                .reaction(itemReactions[i])
                                .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                                .alpha(if (filled) 1f else 0.22f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tap button to add items
            if (!state.roundComplete) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val tapScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
                    label = "tap_scale"
                )

                val pulse by rememberInfiniteTransition(label = "tap_pulse").animateFloat(
                    initialValue = 1f,
                    targetValue = if (state.currentCount == 0) 1.1f else 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "tap_pulse_scale"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = tapScale * pulse; scaleY = tapScale * pulse }
                        .size(130.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(state.currentRound.itemColor)
                        .border(BorderStroke(6.dp, Color.White), CircleShape)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            val newCount = viewModel.onItemTapped()
                            sfxPlayer.play(Sfx.POP)
                            if (newCount > 0) {
                                voicePlayer.play("voice_number_$newCount", language)
                            } else {
                                // Round complete
                                val counted = "ספרו ${state.currentRound.targetNumber} ${state.currentRound.itemName.he}!"
                                voicePlayer.play("voice_number_${state.currentRound.targetNumber}", language) {
                                    if (companion != null) companion.react(counted) { voicePlayer.playPraise(language) } else voicePlayer.playPraise(language)
                                }
                                sfxPlayer.play(Sfx.SUCCESS)
                                showRoundSuccess = true
                                scope.launch {
                                    delay(if (companion?.isReady == true) 3800 else 2600)
                                    showRoundSuccess = false
                                    viewModel.nextRound()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(state.currentRound.itemIcon),
                        contentDescription = null,
                        modifier = Modifier.size(76.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
