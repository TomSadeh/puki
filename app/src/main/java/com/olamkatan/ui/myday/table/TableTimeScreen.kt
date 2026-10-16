package com.olamkatan.ui.myday.table

import com.olamkatan.ui.components.PromptBubble
import com.olamkatan.ui.components.Scene
import com.olamkatan.ui.components.BackArrowIcon
import android.app.Application
import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.remember
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
import com.olamkatan.theme.EarthBrown
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

data class PlaceSetting(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val placed: Boolean = false)

data class TableState(
    val items: List<PlaceSetting> = listOf(
        PlaceSetting("plate", BilingualText("צלחת", "Plate"), R.drawable.ic_table_plate),
        PlaceSetting("cup", BilingualText("כוס", "Cup"), R.drawable.ic_table_cup),
        PlaceSetting("fork", BilingualText("מזלג", "Fork"), R.drawable.ic_table_fork),
        PlaceSetting("spoon", BilingualText("כפית", "Spoon"), R.drawable.ic_table_spoon)
    ),
    val isComplete: Boolean = false
)

class TableTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as OlamKatanApplication).progressRepository
    private val _state = MutableStateFlow(TableState())
    val state: StateFlow<TableState> = _state.asStateFlow()

    fun onItemPlaced(itemId: String, targetId: String): Boolean {
        if (itemId != targetId) return false
        val updated = _state.value.items.map { if (it.id == itemId) it.copy(placed = true) else it }
        val complete = updated.all { it.placed }
        _state.value = _state.value.copy(items = updated, isComplete = complete)
        if (complete) viewModelScope.launch { progressRepository.addStars(1) }
        return true
    }
}

@Composable
fun TableTimeScreen(onBack: () -> Unit, viewModel: TableTimeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val dragDropState = remember { DragDropState() }

    LaunchedEffect(Unit) {
        delay(500)
        voicePlayer.play("voice_prompt_set_table", language)
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        GameBackground(
            scene = Scene.HOME,
            colors = listOf(Color(0xFFC8E6C9), Color(0xFFA5D6A7), Color(0xFF81C784))
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
                    PromptBubble(BilingualText("ארוחה", "Table Time").resolve(language))
                    Spacer(modifier = Modifier.width(52.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Placemat with drop zones
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFFE0B2)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        state.items.forEach { item ->
                            DropZone(zoneId = item.id) {
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (item.placed) Color.White else Color.White.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.placed) {
                                        Image(painter = painterResource(item.icon), contentDescription = null, modifier = Modifier.size(90.dp))
                                    } else {
                                        Text("?", fontSize = 28.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Draggable items
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.items.filter { !it.placed }.forEach { item ->
                        DragTarget(
                            itemId = item.id,
                            onDragEnd = { dropId ->
                                if (dropId != null) {
                                    val correct = viewModel.onItemPlaced(item.id, dropId)
                                    if (correct) {
                                        sfxPlayer.play(Sfx.DROP)
                                        voicePlayer.play("voice_table_${item.id}", language)
                                    } else {
                                        sfxPlayer.play(Sfx.WRONG_GENTLE)
                                    }
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .shadow(6.dp, RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(painter = painterResource(item.icon), contentDescription = null, modifier = Modifier.size(100.dp))
                                    Text(item.name.resolve(language), fontSize = 10.sp, color = EarthBrown)
                                }
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
}
