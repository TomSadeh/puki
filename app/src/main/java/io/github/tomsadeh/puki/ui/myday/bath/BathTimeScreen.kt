package io.github.tomsadeh.puki.ui.myday.bath

import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.ui.common.LocalGender
import io.github.tomsadeh.puki.ui.myday.aboveWater
import io.github.tomsadeh.puki.ui.myday.drawMudSplat
import io.github.tomsadeh.puki.ui.myday.drawSoapBubbles
import io.github.tomsadeh.puki.ui.myday.drawToddler
import io.github.tomsadeh.puki.ui.myday.drawTub
import io.github.tomsadeh.puki.ui.components.popIn
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.absoluteOffset
import io.github.tomsadeh.puki.ui.myday.PukiCorner
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.MyDayTopBar
import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.DragDropState
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.DragTarget
import io.github.tomsadeh.puki.ui.components.DropZone
import io.github.tomsadeh.puki.ui.components.LocalDragDropState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BodyPart(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val cleaned: Boolean = false)

data class BathState(
    val bodyParts: List<BodyPart> = listOf(
        BodyPart("arms", BilingualText("ידיים", "Arms"), R.drawable.ic_body_arm),
        BodyPart("legs", BilingualText("רגליים", "Legs"), R.drawable.ic_body_leg),
        BodyPart("tummy", BilingualText("בטן", "Tummy"), R.drawable.ic_body_tummy),
        BodyPart("face", BilingualText("פנים", "Face"), R.drawable.ic_body_face)
    ),
    val showRinse: Boolean = false,
    val isComplete: Boolean = false
)

class BathTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as PukiApplication).progressRepository
    private val _state = MutableStateFlow(BathState())
    val state: StateFlow<BathState> = _state.asStateFlow()

    fun onSoapDropped(bodyPartId: String): Boolean {
        val current = _state.value
        val updated = current.bodyParts.map {
            if (it.id == bodyPartId) it.copy(cleaned = true) else it
        }
        val allClean = updated.all { it.cleaned }
        _state.value = current.copy(bodyParts = updated, showRinse = allClean)
        return true
    }

    fun onRinse() {
        _state.value = _state.value.copy(isComplete = true)
        viewModelScope.launch { progressRepository.addStars(1) }
    }
}

@Composable
fun BathTimeScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: BathTimeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion = LocalCompanion.current
    // Puki knows where she is (tapping him talks about this routine), and wakes up on the way in
    LaunchedEffect(Unit) {
        companion?.setScene("באמבטיה: מסבנים את הבוץ מהפנים, מהידיים, מהבטן ומהרגליים, ואז שוטפים במקלחת.", "At bath time: soaping the mud off the face, arms, tummy and legs, then a rinse in the shower.")
        companion?.wake()
    }
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) companion?.react("באמבטיה סיבנו את כל הבוץ ושטפו במקלחת. עכשיו הכול נקי!", "At bath time the child soaped off all the mud and rinsed in the shower. All clean!")
    }
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val dragDropState = androidx.compose.runtime.remember { DragDropState() }

    LaunchedEffect(Unit) {
        delay(500)
        voicePlayer.play("voice_prompt_bath", language)
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        GameBackground(
            scene = Scene.BATH,
            colors = listOf(Color(0xFFB3E5FC), Color(0xFF81D4FA), Color(0xFF4FC3F7))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MyDayTopBar(BilingualText("אמבטיה", "Bath Time"), onBack)

                Spacer(modifier = Modifier.height(16.dp))

                // The child in the tub, with the soap (or, once clean, the shower) beside it
                var rinsing by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(start = PukiCorner),  // clear of Puki
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BathScene(parts = state.bodyParts, rinsing = rinsing, modifier = Modifier.weight(1f).fillMaxHeight())
                    Box(modifier = Modifier.width(220.dp), contentAlignment = Alignment.Center) {
                        if (state.showRinse && !state.isComplete) {
                            Box(
                                modifier = Modifier
                                    .popIn()
                                    .idleBob(1)
                                    .size(150.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color(0xFF29B6F6))
                                    .clickable(enabled = !rinsing) {
                                        sfxPlayer.play(Sfx.WHOOSH)
                                        voicePlayer.play("voice_prompt_bath", language)
                                        rinsing = true
                                        scope.launch {
                                            delay(1800)  // let the water pour first
                                            viewModel.onRinse()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Image(painter = painterResource(R.drawable.ic_myday_shower), contentDescription = null, modifier = Modifier.size(96.dp)) }
                        } else if (!state.showRinse) {
                            DragTarget(
                                itemId = "soap",
                                // A tap soaps the next muddy spot
                                tapToZone = SPOTS.firstOrNull { spot -> state.bodyParts.none { it.id == spot.partId && it.cleaned } }?.zoneId,
                                onDragEnd = { dropId ->
                                    val partId = dropId?.removeSuffix("2")  // each arm and leg is its own spot
                                    if (partId != null && state.bodyParts.any { it.id == partId }) {
                                        viewModel.onSoapDropped(partId)
                                        sfxPlayer.play(Sfx.POP)
                                        voicePlayer.play("voice_body_$partId", language)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier.idleBob(0).size(140.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color(0xFFFFF9C4)),
                                    contentAlignment = Alignment.Center
                                ) { Image(painter = painterResource(R.drawable.ic_myday_soap), contentDescription = null, modifier = Modifier.size(96.dp)) }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.isComplete) {
                RoundCompleteSheet(starsEarned = 1, onContinue = onPlayAgain, onHome = onBack)
            }
        }
    }
}


/** Where each muddy spot sits on the child (fractions of the picture), and its radius. */
private data class Spot(val zoneId: String, val partId: String, val x: Float, val y: Float, val r: Float)

private val SPOTS = listOf(
    Spot("face", "face", 0.565f, 0.295f, 0.045f),  // a cheek, clear of the eyes
    Spot("arms", "arms", 0.31f, 0.52f, 0.05f),
    Spot("arms2", "arms", 0.69f, 0.52f, 0.05f),
    Spot("tummy", "tummy", 0.5f, 0.5f, 0.06f),
    Spot("legs", "legs", 0.435f, 0.74f, 0.045f),
    Spot("legs2", "legs", 0.565f, 0.74f, 0.045f),
)

/** The child standing in the tub: mud on each spot until it's soaped, then bubbles. */
@Composable
private fun BathScene(parts: List<BodyPart>, rinsing: Boolean, modifier: Modifier = Modifier) {
    val girl = LocalGender.current == Gender.GIRL
    val t = rememberInfiniteTransition(label = "bath")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "wave")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "fall")
    // The rinse washes the bubbles away
    val bubblesLeft by animateFloatAsState(if (rinsing) 0f else 1f, tween(1400, delayMillis = 300), label = "bubbles")
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(modifier = Modifier.size(side)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                aboveWater { drawToddler(girl) }
                SPOTS.forEachIndexed { i, spot ->
                    val cleaned = parts.firstOrNull { it.id == spot.partId }?.cleaned == true
                    val at = Offset(spot.x, spot.y)
                    if (cleaned) drawSoapBubbles(at, spot.r, wave, bubblesLeft) else drawMudSplat(at, spot.r, seed = i * 7 + 3)
                }
                drawTub(wave, fall, rinsing)
            }
            // Where the soap goes: a little bigger than the mud, so it's easy to hit
            SPOTS.forEach { spot ->
                val r = spot.r * 1.5f
                DropZone(
                    zoneId = spot.zoneId,
                    modifier = Modifier
                        // From the picture's top-left corner in both languages (the picture doesn't mirror)
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(x = side * (spot.x - r), y = side * (spot.y - r))
                        .size(side * r * 2)
                ) {}
            }
        }
    }
}
