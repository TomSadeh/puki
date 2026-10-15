package com.olamkatan.ui.music

import com.olamkatan.ui.common.LocalCompanion
import android.app.Application
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olamkatan.OlamKatanApplication
import com.olamkatan.audio.Sfx
import com.olamkatan.data.content.InstrumentDef
import com.olamkatan.data.content.instruments
import com.olamkatan.data.models.BilingualText
import com.olamkatan.theme.CloudWhite
import com.olamkatan.theme.EarthBrown
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.localized
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.common.LocalVoicePlayer
import com.olamkatan.ui.components.GameBackground
import com.olamkatan.ui.components.GameTopBar
import com.olamkatan.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MusicPhase { FREE_PLAY, SHOW_PATTERN, PLAYER_TURN, SUCCESS, SESSION_COMPLETE }

data class MusicState(
    val phase: MusicPhase = MusicPhase.FREE_PLAY,
    val pattern: List<Int> = emptyList(),
    val playerInput: List<Int> = emptyList(),
    val highlightedInstrument: Int = -1,
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val totalStarsEarned: Int = 0,
    val patternLength: Int = 2
)

class MusicMeadowViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as OlamKatanApplication).progressRepository
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    fun startSimonSays() {
        val pattern = List(_state.value.patternLength) { (0 until instruments.size).random() }
        _state.value = _state.value.copy(phase = MusicPhase.SHOW_PATTERN, pattern = pattern, playerInput = emptyList())
    }

    fun onShowPatternDone() {
        _state.value = _state.value.copy(phase = MusicPhase.PLAYER_TURN, highlightedInstrument = -1)
    }

    fun setHighlight(index: Int) {
        _state.value = _state.value.copy(highlightedInstrument = index)
    }

    fun onInstrumentTapped(index: Int): Boolean? {
        val current = _state.value
        if (current.phase == MusicPhase.FREE_PLAY) return null

        val newInput = current.playerInput + index
        val expectedSoFar = current.pattern.take(newInput.size)

        if (newInput != expectedSoFar) {
            // Wrong — restart this round
            _state.value = current.copy(playerInput = emptyList(), phase = MusicPhase.FREE_PLAY)
            return false
        }

        if (newInput.size == current.pattern.size) {
            // Pattern complete!
            val newStars = current.totalStarsEarned + 1
            val nextRound = current.roundIndex + 1
            _state.value = current.copy(
                playerInput = newInput,
                phase = if (nextRound >= current.totalRounds) MusicPhase.SESSION_COMPLETE else MusicPhase.SUCCESS,
                totalStarsEarned = newStars,
                roundIndex = nextRound,
                patternLength = current.patternLength + if (nextRound % 2 == 0) 1 else 0
            )
            viewModelScope.launch { progressRepository.addStars(1) }
            return true
        }

        _state.value = current.copy(playerInput = newInput)
        return null // still going
    }

    fun nextRound() {
        _state.value = _state.value.copy(phase = MusicPhase.FREE_PLAY, playerInput = emptyList())
    }
}

@Composable
fun MusicMeadowScreen(onBack: () -> Unit, viewModel: MusicMeadowViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        companion?.setScene("במשחק המוזיקה: יש תוף, קסילופון, תוף מרים ומרקסים.", "In the music game: there's a drum, a xylophone, a tambourine and maracas.")
        delay(500)
        voicePlayer.play("voice_music_free", language)
    }

    // Show pattern animation
    LaunchedEffect(state.phase) {
        if (state.phase == MusicPhase.PLAYER_TURN) voicePlayer.play("voice_music_your_turn", language)
        if (state.phase == MusicPhase.SUCCESS) {
            if (companion != null) companion.react("ניגנו את המנגינה בדיוק נכון!", "The child played the tune back just right!") { voicePlayer.playPraise(language) } else voicePlayer.playPraise(language)
        }
        if (state.phase == MusicPhase.SHOW_PATTERN) {
            // "Listen and remember!" first, then the pattern
            voicePlayer.play("voice_music_listen", language)
            delay(1800)
            for (noteIndex in state.pattern) {
                viewModel.setHighlight(noteIndex)
                sfxPlayer.play(Sfx.POP)
                delay(600)
                viewModel.setHighlight(-1)
                delay(200)
            }
            viewModel.onShowPatternDone()
        }
    }

    GameBackground(
        colors = listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2), Color(0xFFFFCC80))
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

            Text(
                text = when (state.phase) {
                    MusicPhase.FREE_PLAY -> BilingualText("תלחץ על הכלים ותנגן!", "Tap the instruments and play!", heGirl = "תלחצי על הכלים ותנגני!").localized()
                    MusicPhase.SHOW_PATTERN -> BilingualText("תקשיב ותזכור...", "Listen and remember...", heGirl = "תקשיבי ותזכרי...").localized()
                    MusicPhase.PLAYER_TURN -> BilingualText("עכשיו תורך!", "Your turn!").resolve(language)
                    MusicPhase.SUCCESS -> BilingualText("מצוין!", "Awesome!").resolve(language)
                    MusicPhase.SESSION_COMPLETE -> ""
                },
                style = MaterialTheme.typography.titleLarge,
                color = EarthBrown
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Instruments
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                instruments.forEachIndexed { index, instrument ->
                    val isHighlighted = state.highlightedInstrument == index
                    val interactionSource = remember(instrument.id) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = when {
                            isHighlighted -> 1.2f
                            isPressed -> 0.85f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
                        label = "inst_scale"
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .shadow(if (isHighlighted) 16.dp else 8.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            if (isHighlighted) instrument.color else instrument.color.copy(alpha = 0.8f),
                                            if (isHighlighted) instrument.color.copy(alpha = 0.9f) else instrument.color.copy(alpha = 0.6f)
                                        )
                                    )
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    sfxPlayer.play(Sfx.POP)
                                    voicePlayer.play("voice_instrument_${instrument.id}", language)
                                    if (state.phase == MusicPhase.PLAYER_TURN) {
                                        val result = viewModel.onInstrumentTapped(index)
                                        if (result == false) {
                                            sfxPlayer.play(Sfx.WRONG_GENTLE)
                                            voicePlayer.play("voice_prompt_try_again", language)
                                        } else if (result == true) {
                                            sfxPlayer.play(Sfx.SUCCESS)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (instrument.drawableRes != 0) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = instrument.drawableRes),
                                    contentDescription = instrument.name.en,
                                    modifier = androidx.compose.ui.Modifier.size(110.dp)
                                )
                            } else {
                                Text(instrument.emoji, fontSize = 52.sp)
                            }
                        }
                        Text(instrument.name.resolve(language), style = MaterialTheme.typography.labelLarge, color = EarthBrown)
                    }
                }
            }

            // Start button (free play only)
            if (state.phase == MusicPhase.FREE_PLAY) {
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 60.dp)
                        .shadow(6.dp, RoundedCornerShape(30.dp))
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFF66BB6A))
                        .clickable { viewModel.startSimonSays() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(BilingualText("מתחילים!", "Let's go!").resolve(language), style = MaterialTheme.typography.titleLarge, color = CloudWhite)
                }
            }

            if (state.phase == MusicPhase.SUCCESS) {
                LaunchedEffect(state.roundIndex) {
                    delay(1000)
                    viewModel.nextRound()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.phase == MusicPhase.SESSION_COMPLETE) {
            RoundCompleteSheet(starsEarned = state.totalStarsEarned, onContinue = onBack, onHome = onBack)
        }
    }
}
