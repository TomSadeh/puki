package com.olamkatan.ui.myday.bath

import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.ui.components.popIn
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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import com.olamkatan.ui.components.idleBob
import com.olamkatan.ui.components.Scene
import com.olamkatan.ui.components.MyDayTopBar
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
import com.olamkatan.OlamKatanApplication
import com.olamkatan.R
import com.olamkatan.audio.Sfx
import com.olamkatan.data.models.BilingualText
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.common.LocalVoicePlayer
import com.olamkatan.ui.components.DragDropState
import com.olamkatan.ui.components.GameBackground
import com.olamkatan.ui.components.DragTarget
import com.olamkatan.ui.components.DropZone
import com.olamkatan.ui.components.LocalDragDropState
import com.olamkatan.ui.components.RoundCompleteSheet
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
    private val progressRepository = (application as OlamKatanApplication).progressRepository
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
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
    Spot("face", "face", 0.60f, 0.32f, 0.07f),  // a cheek, clear of the eyes
    Spot("arms", "arms", 0.21f, 0.58f, 0.09f),
    Spot("arms2", "arms", 0.79f, 0.58f, 0.09f),
    Spot("tummy", "tummy", 0.50f, 0.55f, 0.10f),
    Spot("legs", "legs", 0.39f, 0.73f, 0.075f),
    Spot("legs2", "legs", 0.61f, 0.73f, 0.075f),
)

/** The child standing in a tub: mud on each spot until it's soaped, then bubbles. */
@Composable
private fun BathScene(parts: List<BodyPart>, rinsing: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bath")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "wave")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "fall")
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(modifier = Modifier.size(side)) {
            Image(painter = painterResource(R.drawable.ic_myday_child_bath), contentDescription = null, modifier = Modifier.fillMaxSize())
            SPOTS.forEach { spot ->
                val cleaned = parts.firstOrNull { it.id == spot.partId }?.cleaned == true
                DropZone(
                    zoneId = spot.zoneId,
                    modifier = Modifier
                        // From the picture's top-left corner in both languages (the picture doesn't mirror)
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(x = side * (spot.x - spot.r), y = side * (spot.y - spot.r))
                        .size(side * spot.r * 2)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height / 2)
                        val r = size.minDimension / 2
                        if (!cleaned) {
                            // Mud: a few brown blobs
                            val mud = Color(0xFF795548)
                            drawCircle(mud.copy(alpha = 0.75f), r * 0.42f, c + Offset(-r * 0.18f, -r * 0.1f))
                            drawCircle(mud.copy(alpha = 0.7f), r * 0.3f, c + Offset(r * 0.32f, r * 0.12f))
                            drawCircle(mud.copy(alpha = 0.65f), r * 0.2f, c + Offset(-r * 0.05f, r * 0.42f))
                            drawCircle(mud.copy(alpha = 0.6f), r * 0.12f, c + Offset(r * 0.45f, -r * 0.4f))
                        } else {
                            // Bubbles
                            val bubble = Color.White.copy(alpha = 0.85f)
                            listOf(Triple(-0.35f, -0.2f, 0.28f), Triple(0.25f, -0.35f, 0.22f), Triple(0.3f, 0.25f, 0.3f),
                                   Triple(-0.2f, 0.35f, 0.2f)).forEach { (dx, dy, br) ->
                                val bc = c + Offset(dx * r, dy * r - wave * r * 0.15f)
                                drawCircle(bubble, br * r, bc)
                                drawCircle(Color(0xFFB3E5FC), br * r, bc, style = Stroke(width = 2.5f))
                                drawCircle(Color.White, br * r * 0.3f, bc + Offset(-br * r * 0.35f, -br * r * 0.35f))
                            }
                        }
                    }
                }
            }
            // The tub, in front of the legs
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val top = h * 0.80f
                val water = Path().apply {
                    moveTo(w * 0.08f, top + h * 0.02f)
                    var x = w * 0.08f
                    while (x <= w * 0.92f) {
                        lineTo(x, top + h * 0.02f + kotlin.math.sin((x / w * 6f + wave) * 2 * Math.PI.toFloat()) * h * 0.008f)
                        x += w * 0.02f
                    }
                    lineTo(w * 0.92f, h * 0.9f); lineTo(w * 0.08f, h * 0.9f); close()
                }
                drawPath(water, Color(0xFF81D4FA).copy(alpha = 0.85f))
                // Foam along the water line
                for (i in 0 until 9) {
                    val fx = w * (0.12f + i * 0.095f)
                    drawCircle(Color.White, h * (0.022f + 0.01f * (i % 3)), Offset(fx, top + h * 0.02f))
                }
                // The tub's body and rim
                drawRoundRect(Color.White, Offset(w * 0.06f, top + h * 0.035f), Size(w * 0.88f, h * 0.15f), CornerRadius(h * 0.06f))
                drawRoundRect(Color(0xFFE3F2FD), Offset(w * 0.06f, top + h * 0.12f), Size(w * 0.88f, h * 0.065f), CornerRadius(h * 0.05f))
                drawRoundRect(Color(0xFFB0BEC5), Offset(w * 0.04f, top + h * 0.02f), Size(w * 0.92f, h * 0.03f), CornerRadius(h * 0.015f))
                // Feet of the tub
                drawCircle(Color(0xFFFFC107), h * 0.025f, Offset(w * 0.16f, h * 0.99f))
                drawCircle(Color(0xFFFFC107), h * 0.025f, Offset(w * 0.84f, h * 0.99f))
                // The shower: water drops falling over the child
                if (rinsing) {
                    for (i in 0 until 26) {
                        val dx = w * (0.2f + (i * 37 % 60) / 100f)
                        val dy = ((fall + i * 0.137f) % 1f) * h * 0.8f
                        drawLine(Color(0xFF4FC3F7), Offset(dx, dy), Offset(dx, dy + h * 0.04f), strokeWidth = 5f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}
