package com.olamkatan.ui.myday.brush

import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.ui.components.PromptBubble
import com.olamkatan.ui.components.Scene
import com.olamkatan.ui.components.BackArrowIcon
import android.app.Application
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olamkatan.OlamKatanApplication
import com.olamkatan.R
import com.olamkatan.audio.Sfx
import com.olamkatan.data.models.BilingualText
import com.olamkatan.theme.CloudWhite
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.localized
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.common.LocalVoicePlayer
import com.olamkatan.ui.components.GameBackground
import com.olamkatan.ui.components.RoundCompleteSheet
import com.olamkatan.ui.components.WigglingItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrushState(
    val teeth: List<Boolean> = List(8) { false }, // false = dirty, true = clean
    val isComplete: Boolean = false
)

class BrushyBrushyViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as OlamKatanApplication).progressRepository
    private val _state = MutableStateFlow(BrushState())
    val state: StateFlow<BrushState> = _state.asStateFlow()

    fun cleanTooth(index: Int) {
        val current = _state.value
        if (current.teeth[index]) return
        val updated = current.teeth.toMutableList().apply { this[index] = true }
        val complete = updated.all { it }
        _state.value = current.copy(teeth = updated, isComplete = complete)
        if (complete) {
            viewModelScope.launch { progressRepository.addStars(1) }
        }
    }
}

@Composable
fun BrushyBrushyScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: BrushyBrushyViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion = LocalCompanion.current
    // Puki knows where she is (tapping him talks about this routine), and wakes up on the way in
    LaunchedEffect(Unit) {
        companion?.setScene("בצחצוח שיניים: מצחצחים כל שן עד שהיא מבריקה.", "Brushing teeth: brushing each tooth until it sparkles.")
        companion?.wake()
    }
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) companion?.react("צחצחו את כל השיניים, והן מבריקות!", "The child brushed every tooth, and they sparkle!")
    }
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current

    LaunchedEffect(Unit) {
        delay(500)
        voicePlayer.play("voice_prompt_brush_teeth", language)
    }

    GameBackground(
            scene = Scene.BATH,
        colors = listOf(Color(0xFFB2EBF2), Color(0xFF80DEEA), Color(0xFF4DD0E1))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { BackArrowIcon() }
                PromptBubble(BilingualText("צחצוח שיניים", "Brushy Brushy").resolve(language))
                Spacer(modifier = Modifier.width(52.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Big mouth with teeth
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color(0xFFEF9A9A)),
                contentAlignment = Alignment.Center
            ) {
                // Two rows of teeth
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Top teeth
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (i in 0..3) {
                            Tooth(
                                isClean = state.teeth[i],
                                onClick = {
                                    viewModel.cleanTooth(i)
                                    sfxPlayer.play(Sfx.POP)
                                }
                            )
                        }
                    }
                    // Bottom teeth
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (i in 4..7) {
                            Tooth(
                                isClean = state.teeth[i],
                                onClick = {
                                    viewModel.cleanTooth(i)
                                    sfxPlayer.play(Sfx.POP)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toothbrush
            Image(painter = painterResource(R.drawable.ic_myday_toothbrush), contentDescription = null, modifier = Modifier.size(84.dp))
            PromptBubble(BilingualText("תלחץ על כל שן!", "Tap each tooth!", heGirl = "תלחצי על כל שן!").localized())

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.isComplete) {
            RoundCompleteSheet(starsEarned = 1, onContinue = onPlayAgain, onHome = onBack)
        }
    }
}

@Composable
private fun Tooth(isClean: Boolean, onClick: () -> Unit) {
    val wiggle = if (!isClean) {
        val t = rememberInfiniteTransition(label = "tooth_wiggle")
        val r by t.animateFloat(
            initialValue = -2f, targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
            label = "wiggle_rot"
        )
        r
    } else 0f

    Box(
        modifier = Modifier
            .size(110.dp, 130.dp)
            .graphicsLayer { rotationZ = wiggle }
            .shadow(6.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 40.dp, bottomEnd = 40.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(if (isClean) Color.White else Color(0xFFFFE082))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isClean) {
            Image(painter = painterResource(R.drawable.ic_myday_sparkle), contentDescription = null, modifier = Modifier.size(60.dp))
        }
    }
}
