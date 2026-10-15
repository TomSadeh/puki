package com.olamkatan.ui.myday.dress

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
                        .background(Color.White.copy(alpha = 0.25f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { BackArrowIcon() }
                Text(BilingualText("להתלבש", "Getting Dressed").resolve(language), style = MaterialTheme.typography.headlineMedium, color = CloudWhite)
                Spacer(modifier = Modifier.width(52.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Character with worn clothes
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFE0B2)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painter = painterResource(R.drawable.ic_myday_child), contentDescription = null, modifier = Modifier.size(64.dp))
                    Row {
                        state.clothes.filter { it.order < state.currentStep }.forEach {
                            Image(painter = painterResource(it.icon), contentDescription = null, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Clothing choices
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.clothes.filter { it.order >= state.currentStep }.forEach { item ->
                    val interactionSource = remember(item.id) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
                        label = "cloth_scale"
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .shadow(6.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.9f))
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    val correct = viewModel.onClothingTapped(item.id)
                                    if (correct) {
                                        sfxPlayer.play(Sfx.SUCCESS)
                                        voicePlayer.play("voice_clothing_${item.id}", language)
                                    } else {
                                        sfxPlayer.play(Sfx.WRONG_GENTLE)
                                        voicePlayer.play("voice_prompt_try_again", language)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Image(painter = painterResource(item.icon), contentDescription = null, modifier = Modifier.size(66.dp)) }
                        Text(item.name.resolve(language), style = MaterialTheme.typography.labelLarge, color = CloudWhite)
                    }
                }
            }
        }

        if (state.isComplete) {
            RoundCompleteSheet(starsEarned = 1, onContinue = onBack, onHome = onBack)
        }
    }
}
