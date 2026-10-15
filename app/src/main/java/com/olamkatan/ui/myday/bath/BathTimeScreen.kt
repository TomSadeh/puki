package com.olamkatan.ui.myday.bath

import com.olamkatan.ui.components.BackArrowIcon
import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.olamkatan.ui.components.ConfettiOverlay
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
fun BathTimeScreen(onBack: () -> Unit, viewModel: BathTimeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            colors = listOf(Color(0xFFB3E5FC), Color(0xFF81D4FA), Color(0xFF4FC3F7))
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
                    Text(BilingualText("אמבטיה", "Bath Time").resolve(language), style = MaterialTheme.typography.headlineMedium, color = CloudWhite)
                    Spacer(modifier = Modifier.width(52.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Body parts as drop zones
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.bodyParts.forEach { part ->
                        DropZone(zoneId = part.id) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .shadow(4.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (part.cleaned) Color(0xFFE1F5FE) else Color(0xFF8D6E63)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(painter = painterResource(part.icon), contentDescription = null, modifier = Modifier.size(52.dp))
                                    if (part.cleaned) Image(painter = painterResource(R.drawable.ic_myday_sparkle), contentDescription = null, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.showRinse && !state.isComplete) {
                    // Rinse button
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFF29B6F6))
                            .clickable {
                                sfxPlayer.play(Sfx.WHOOSH)
                                voicePlayer.play("voice_prompt_bath", language)
                                viewModel.onRinse()
                            },
                        contentAlignment = Alignment.Center
                    ) { Image(painter = painterResource(R.drawable.ic_myday_shower), contentDescription = null, modifier = Modifier.size(76.dp)) }
                } else if (!state.showRinse) {
                    // Soap draggable
                    DragTarget(
                        itemId = "soap",
                        onDragEnd = { dropId ->
                            if (dropId != null) {
                                viewModel.onSoapDropped(dropId)
                                sfxPlayer.play(Sfx.POP)
                                val part = state.bodyParts.find { it.id == dropId }
                                if (part != null) voicePlayer.play("voice_body_${part.id}", language)
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier.size(100.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color(0xFFFFF9C4)),
                            contentAlignment = Alignment.Center
                        ) { Image(painter = painterResource(R.drawable.ic_myday_soap), contentDescription = null, modifier = Modifier.size(66.dp)) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.isComplete) {
                RoundCompleteSheet(starsEarned = 1, onContinue = onBack, onHome = onBack)
            }
        }
    }
}
