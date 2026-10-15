package com.olamkatan.ui.stickers

import androidx.compose.runtime.LaunchedEffect
import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.ui.hub.components.StarCounter
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olamkatan.OlamKatanApplication
import com.olamkatan.R
import com.olamkatan.audio.Sfx
import com.olamkatan.data.models.BilingualText
import com.olamkatan.data.models.GameProgress
import com.olamkatan.theme.CloudWhite
import com.olamkatan.theme.EarthBrown
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.common.LocalVoicePlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class StickerDef(
    val id: String,
    val name: BilingualText,
    @DrawableRes val icon: Int,
    val starsRequired: Int
)

val allStickers = listOf(
    StickerDef("cow", BilingualText("פרה", "Cow"), R.drawable.ic_animal_cow, 0),
    StickerDef("dog", BilingualText("כלב", "Dog"), R.drawable.ic_animal_dog, 2),
    StickerDef("cat", BilingualText("חתול", "Cat"), R.drawable.ic_animal_cat, 4),
    StickerDef("rainbow", BilingualText("קשת", "Rainbow"), R.drawable.ic_sticker_rainbow, 6),
    StickerDef("star", BilingualText("כוכב", "Star"), R.drawable.ic_sticker_star, 8),
    StickerDef("flower", BilingualText("פרח", "Flower"), R.drawable.ic_sticker_flower, 10),
    StickerDef("butterfly", BilingualText("פרפר", "Butterfly"), R.drawable.ic_sticker_butterfly, 14),
    StickerDef("heart", BilingualText("לב", "Heart"), R.drawable.ic_sticker_heart, 18),
    StickerDef("music", BilingualText("מוזיקה", "Music"), R.drawable.ic_sticker_music, 22),
    StickerDef("sun", BilingualText("שמש", "Sun"), R.drawable.ic_sticker_sun, 26),
    StickerDef("moon", BilingualText("ירח", "Moon"), R.drawable.ic_sticker_moon, 30),
    StickerDef("rocket", BilingualText("טיל", "Rocket"), R.drawable.ic_sticker_rocket, 40),
    StickerDef("crown", BilingualText("כתר", "Crown"), R.drawable.ic_sticker_crown, 50),
    StickerDef("trophy", BilingualText("גביע", "Trophy"), R.drawable.ic_sticker_trophy, 60),
    StickerDef("diamond", BilingualText("יהלום", "Diamond"), R.drawable.ic_sticker_diamond, 80),
    StickerDef("unicorn", BilingualText("חד קרן", "Unicorn"), R.drawable.ic_sticker_unicorn, 100)
)

class StickerAlbumViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as OlamKatanApplication).progressRepository
    val progress: StateFlow<GameProgress> = MutableStateFlow(GameProgress()).also { flow ->
        // Collect in init
    }

    // Use the app-level progress
    val appProgress = progressRepository.progress
}

@Composable
fun StickerAlbumScreen(onBack: () -> Unit, viewModel: StickerAlbumViewModel = viewModel()) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    LaunchedEffect(Unit) { companion?.wake() }
    val sfxPlayer = LocalSfxPlayer.current
    val progress by viewModel.appProgress.collectAsStateWithLifecycle(GameProgress())

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7), Color(0xFFCE93D8)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(EarthBrown.copy(alpha = 0.7f)).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { BackArrowIcon() }
                Text(BilingualText("אלבום מדבקות", "Sticker Album").resolve(language), style = MaterialTheme.typography.headlineMedium, color = CloudWhite)
                StarCounter(stars = progress.totalStars)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Two rows of eight square stickers, like a page in a real album
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allStickers) { sticker ->
                    val isUnlocked = progress.totalStars >= sticker.starsRequired
                    val interactionSource = remember(sticker.id) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
                        label = "sticker_scale"
                    )

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .shadow(if (isUnlocked) 6.dp else 2.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isUnlocked) Color.White else Color(0xFFF3E5F5))
                            .clickable(interactionSource = interactionSource, indication = null) {
                                if (isUnlocked) {
                                    sfxPlayer.play(Sfx.POP)
                                    val fallback = { voicePlayer.play("voice_sticker_${sticker.id}", language) }
                                    if (companion != null) {
                                        companion.react("באלבום המדבקות לחצו על המדבקה של ${sticker.name.he}. תגיד עליה משהו קטן ומצחיק.", fallback)
                                    } else fallback()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUnlocked) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(sticker.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(84.dp)
                                )
                                Text(sticker.name.resolve(language), style = MaterialTheme.typography.labelLarge.copy(shadow = null), color = EarthBrown)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(R.drawable.ic_sticker_locked),
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp).alpha(0.8f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_ui_star),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("${sticker.starsRequired}", style = MaterialTheme.typography.labelLarge.copy(shadow = null), color = EarthBrown)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
