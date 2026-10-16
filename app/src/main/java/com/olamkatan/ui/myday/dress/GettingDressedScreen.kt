package com.olamkatan.ui.myday.dress

import com.olamkatan.ui.components.rememberReactionState
import com.olamkatan.ui.components.reaction
import com.olamkatan.ui.components.popIn
import com.olamkatan.ui.components.idleBob
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import com.olamkatan.ui.components.PromptBubble
import com.olamkatan.ui.components.Scene
import com.olamkatan.ui.components.BackArrowIcon
import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.common.LocalVoicePlayer
import com.olamkatan.ui.components.GameBackground
import com.olamkatan.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClothingItem(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val order: Int)

data class DressState(
    val clothes: List<ClothingItem> = listOf(
        ClothingItem("underwear", BilingualText("תחתונים", "Underwear"), R.drawable.ic_clothing_underwear, 0),
        ClothingItem("shirt", BilingualText("חולצה", "Shirt"), R.drawable.ic_clothing_shirt, 1),
        ClothingItem("pants", BilingualText("מכנסיים", "Pants"), R.drawable.ic_clothing_pants, 2),
        ClothingItem("socks", BilingualText("גרביים", "Socks"), R.drawable.ic_clothing_socks, 3),
        ClothingItem("shoes", BilingualText("נעליים", "Shoes"), R.drawable.ic_clothing_shoes, 4)
    ),
    val currentStep: Int = 0,
    val isComplete: Boolean = false,
    val wrongChoice: Boolean = false
)

class GettingDressedViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as OlamKatanApplication).progressRepository
    private val _state = MutableStateFlow(DressState())
    val state: StateFlow<DressState> = _state.asStateFlow()

    fun onClothingTapped(clothingId: String): Boolean {
        val current = _state.value
        val item = current.clothes.find { it.id == clothingId } ?: return false
        if (item.order == current.currentStep) {
            val nextStep = current.currentStep + 1
            val complete = nextStep >= current.clothes.size
            _state.value = current.copy(currentStep = nextStep, isComplete = complete, wrongChoice = false)
            if (complete) viewModelScope.launch { progressRepository.addStars(1) }
            return true
        } else {
            _state.value = current.copy(wrongChoice = true)
            viewModelScope.launch {
                delay(800)
                _state.value = _state.value.copy(wrongChoice = false)
            }
            return false
        }
    }
}

@Composable
fun GettingDressedScreen(onBack: () -> Unit, viewModel: GettingDressedViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(500)
        voicePlayer.play("voice_prompt_get_dressed", language)
    }

    LaunchedEffect(state.currentStep) {
        if (state.currentStep > 0 && !state.isComplete) {
            val nextItem = state.clothes.getOrNull(state.currentStep)
            if (nextItem != null) {
                delay(300)
                voicePlayer.play("voice_clothing_${nextItem.id}", language)
            }
        }
    }

    GameBackground(
            scene = Scene.HOME,
        colors = listOf(Color(0xFFF8BBD0), Color(0xFFF48FB1), Color(0xFFEC407A))
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
                PromptBubble(BilingualText("להתלבש", "Getting Dressed").resolve(language))
                Spacer(modifier = Modifier.width(52.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The child, dressed as far as we've got, and the open wardrobe
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DressedChild(worn = state.clothes.filter { it.order < state.currentStep },
                    modifier = Modifier.weight(1f).fillMaxHeight())
                Wardrobe(modifier = Modifier.width(430.dp).fillMaxHeight()) {
                    state.clothes.forEach { item ->
                        if (item.order >= state.currentStep) {
                            val spot = WARDROBE_SPOTS.getValue(item.id)
                            val reaction = rememberReactionState(item.id)
                            val next = item.order == state.currentStep
                            Image(
                                painter = painterResource(item.icon),
                                contentDescription = item.name.resolve(language),
                                modifier = Modifier
                                    .align(AbsoluteAlignment.TopLeft)
                                    .absoluteOffset(spot.x.dp, spot.y.dp)
                                    .idleBob(item.order, if (next) 1.4f else 0.4f)  // what comes next moves a little more
                                    .reaction(reaction)
                                    .size(spot.size.dp)
                                    .graphicsLayer { rotationZ = spot.tilt }
                                    .clickable {
                                        val correct = viewModel.onClothingTapped(item.id)
                                        if (correct) {
                                            sfxPlayer.play(Sfx.SUCCESS)
                                            voicePlayer.play("voice_clothing_${item.id}", language)
                                        } else {
                                            sfxPlayer.play(Sfx.WRONG_GENTLE)
                                            voicePlayer.play("voice_prompt_try_again", language)
                                            scope.launch { reaction.shake() }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        if (state.isComplete) {
            RoundCompleteSheet(starsEarned = 1, onContinue = onBack, onHome = onBack)
        }
    }
}


/** Where a piece of clothing sits on the child (fractions of the picture), its size, and
 *  whether it's the mirrored one (the second sock or shoe). */
private data class OnBody(val x: Float, val y: Float, val size: Float, val mirrored: Boolean = false)

private val ON_BODY = mapOf(
    "underwear" to listOf(OnBody(0.50f, 0.64f, 0.30f)),
    "shirt" to listOf(OnBody(0.50f, 0.53f, 0.50f)),
    "pants" to listOf(OnBody(0.50f, 0.78f, 0.46f)),
    // One on each foot
    "socks" to listOf(OnBody(0.37f, 0.885f, 0.17f), OnBody(0.63f, 0.885f, 0.17f, mirrored = true)),
    "shoes" to listOf(OnBody(0.35f, 0.91f, 0.19f), OnBody(0.65f, 0.91f, 0.19f, mirrored = true)),
)

/** The big child, with whatever's been put on so far shown on the body. */
@Composable
private fun DressedChild(worn: List<ClothingItem>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(modifier = Modifier.size(side)) {
            Image(painter = painterResource(R.drawable.ic_myday_child_bath), contentDescription = null, modifier = Modifier.fillMaxSize())
            // In dressing order, so the shirt goes over the underwear and the shoes over the socks
            worn.sortedBy { it.order }.forEach { item ->
                ON_BODY.getValue(item.id).forEach { at ->
                    Image(
                        painter = painterResource(item.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)  // the picture doesn't mirror in Hebrew
                            .absoluteOffset(side * (at.x - at.size / 2), side * (at.y - at.size / 2))
                            .popIn(key = item.id)
                            .size(side * at.size)
                            .graphicsLayer { scaleX = if (at.mirrored) -1f else 1f }
                    )
                }
            }
        }
    }
}

/** Where each piece waits in the wardrobe: offset and size in dp, and a little tilt. */
private data class WardrobeSpot(val x: Float, val y: Float, val size: Float, val tilt: Float)

private val WARDROBE_SPOTS = mapOf(
    "shirt" to WardrobeSpot(55f, 70f, 140f, -4f),
    "pants" to WardrobeSpot(225f, 70f, 140f, 5f),
    "underwear" to WardrobeSpot(50f, 268f, 110f, -6f),
    "socks" to WardrobeSpot(230f, 262f, 110f, 8f),
    "shoes" to WardrobeSpot(140f, 390f, 130f, 0f),
)

/** An open wardrobe: a rail with hangers, a shelf, and the floor for shoes. */
@Composable
private fun Wardrobe(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(430.dp, 540.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val wood = Color(0xFFB07A45)
                val inside = Color(0xFFFFF1DC)
                drawRoundRect(wood, cornerRadius = CornerRadius(w * 0.04f))
                drawRoundRect(inside, Offset(w * 0.05f, h * 0.05f), Size(w * 0.9f, h * 0.9f), CornerRadius(w * 0.02f))
                // The rail and two hangers
                drawLine(Color(0xFF9E9E9E), Offset(w * 0.07f, h * 0.12f), Offset(w * 0.93f, h * 0.12f), strokeWidth = w * 0.015f)
                for (cx in listOf(w * 0.29f, w * 0.69f)) {
                    drawArc(Color(0xFF757575), 200f, 140f, false, Offset(cx - w * 0.03f, h * 0.085f), Size(w * 0.06f, h * 0.05f),
                        style = Stroke(width = w * 0.01f))
                    val hanger = Path().apply {
                        moveTo(cx, h * 0.13f); lineTo(cx - w * 0.13f, h * 0.18f); lineTo(cx + w * 0.13f, h * 0.18f); close()
                    }
                    drawPath(hanger, Color(0xFF8D6E63), style = Stroke(width = w * 0.012f))
                }
                // The shelf
                drawRect(wood, Offset(w * 0.05f, h * 0.68f), Size(w * 0.9f, h * 0.025f))
                drawRect(Color.Black.copy(alpha = 0.08f), Offset(w * 0.05f, h * 0.705f), Size(w * 0.9f, h * 0.02f))
                // Open doors, seen edge-on at the sides
                drawRect(wood.copy(alpha = 0.9f), Offset(-w * 0.0f, h * 0.02f), Size(w * 0.03f, h * 0.96f))
                drawRect(wood.copy(alpha = 0.9f), Offset(w * 0.97f, h * 0.02f), Size(w * 0.03f, h * 0.96f))
            }
            content()
        }
    }
}
