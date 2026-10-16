package com.olamkatan.ui.myday.tidy

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

data class TidyItem(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val homeId: String, val tidied: Boolean = false)
data class TidyHome(val id: String, val name: BilingualText, @DrawableRes val icon: Int)

data class TidyState(
    val items: List<TidyItem> = listOf(
        TidyItem("teddy", BilingualText("דובי", "Teddy"), R.drawable.ic_tidy_teddy, "shelf"),
        TidyItem("book", BilingualText("ספר", "Book"), R.drawable.ic_tidy_book, "bookcase"),
        TidyItem("shirt", BilingualText("חולצה", "Shirt"), R.drawable.ic_clothing_shirt, "hamper"),
        TidyItem("ball", BilingualText("כדור", "Ball"), R.drawable.ic_tidy_ball, "toybox")
    ),
    val homes: List<TidyHome> = listOf(
        TidyHome("shelf", BilingualText("מדף", "Shelf"), R.drawable.ic_tidy_shelf),
        TidyHome("bookcase", BilingualText("ארון ספרים", "Bookcase"), R.drawable.ic_tidy_bookcase),
        TidyHome("hamper", BilingualText("סל כביסה", "Hamper"), R.drawable.ic_tidy_hamper),
        TidyHome("toybox", BilingualText("ארגז צעצועים", "Toy Box"), R.drawable.ic_tidy_toybox)
    ),
    val isComplete: Boolean = false
)

class TidyUpViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as OlamKatanApplication).progressRepository
    private val _state = MutableStateFlow(TidyState())
    val state: StateFlow<TidyState> = _state.asStateFlow()

    fun onItemDropped(itemId: String, homeId: String): Boolean {
        val item = _state.value.items.find { it.id == itemId } ?: return false
        if (item.homeId != homeId) return false
        val updated = _state.value.items.map { if (it.id == itemId) it.copy(tidied = true) else it }
        val complete = updated.all { it.tidied }
        _state.value = _state.value.copy(items = updated, isComplete = complete)
        if (complete) viewModelScope.launch { progressRepository.addStars(1) }
        return true
    }
}

@Composable
fun TidyUpScreen(onBack: () -> Unit, viewModel: TidyUpViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val dragDropState = remember { DragDropState() }

    LaunchedEffect(Unit) {
        delay(500)
        voicePlayer.play("voice_prompt_tidy_up", language)
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        GameBackground(
            scene = Scene.HOME,
            colors = listOf(Color(0xFFFFE0B2), Color(0xFFFFCC80), Color(0xFFFFB74D))
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
                    PromptBubble(BilingualText("סידור חדר", "Tidy Up").resolve(language))
                    Spacer(modifier = Modifier.width(52.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Homes (drop zones) at top
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    state.homes.forEach { home ->
                        DropZone(zoneId = home.id) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .shadow(4.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(painter = painterResource(home.icon), contentDescription = null, modifier = Modifier.size(90.dp))
                                    Text(home.name.resolve(language), fontSize = 10.sp, color = EarthBrown)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Items on floor (draggable)
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.items.filter { !it.tidied }.forEach { item ->
                        DragTarget(
                            itemId = item.id,
                            onDragEnd = { dropId ->
                                if (dropId != null) {
                                    val correct = viewModel.onItemDropped(item.id, dropId)
                                    if (correct) {
                                        sfxPlayer.play(Sfx.SUCCESS)
                                        voicePlayer.play("voice_tidy_${item.id}", language)
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
                                    .background(Color(0xFFFFF3E0)),
                                contentAlignment = Alignment.Center
                            ) { Image(painter = painterResource(item.icon), contentDescription = null, modifier = Modifier.size(100.dp)) }
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
