package com.olamkatan.ui.myday.bedtime

import com.olamkatan.ui.common.LocalMusicPlayer
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.olamkatan.ui.common.LocalCompanion
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Pictures the story can use, with a label that tells Gemma what each one shows. */
private val STORY_ICONS = linkedMapOf(
    "ic_animal_cow" to "פרה", "ic_animal_dog" to "כלב", "ic_animal_cat" to "חתול", "ic_animal_elephant" to "פיל",
    "ic_animal_lion" to "אריה", "ic_animal_rabbit" to "ארנב", "ic_animal_duck" to "ברווז", "ic_animal_sheep" to "כבשה",
    "ic_animal_monkey" to "קוף", "ic_animal_giraffe" to "ג׳ירפה", "ic_animal_penguin" to "פינגווין", "ic_animal_horse" to "סוס",
    "ic_bedtime_moon" to "ירח", "ic_sticker_star" to "כוכב", "ic_sticker_sun" to "שמש", "ic_sticker_rainbow" to "קשת",
    "ic_sticker_flower" to "פרח", "ic_sticker_butterfly" to "פרפר", "ic_sticker_heart" to "לב", "ic_tidy_teddy" to "דובי",
    "ic_word_house" to "בית", "ic_food_banana" to "בננה", "ic_count_apple" to "תפוח", "ic_tidy_ball" to "כדור",
    "ic_myday_bathtub" to "אמבטיה", "ic_bedtime_zzz" to "שינה",
)

/**
 * After the bedtime routine, פּוּקִי tells a short calm story about the child's day,
 * one sentence at a time with a matching picture. Shows nothing if the brain isn't there.
 */
@Composable
fun BedtimeStory(modifier: Modifier = Modifier) {
    val companion = LocalCompanion.current ?: return
    val musicPlayer = LocalMusicPlayer.current
    val context = LocalContext.current
    var sentences by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var current by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        delay(2500)  // let "good night" land first
        fun res(name: String) = context.resources.getIdentifier(name, "drawable", context.packageName)
        val story = companion.story(STORY_ICONS.filterKeys { res(it) != 0 }) ?: return@LaunchedEffect
        val items = story.map { (text, icon) -> text to res(icon) }.filter { it.second != 0 }
        if (items.isEmpty()) return@LaunchedEffect
        sentences = items
        musicPlayer.duck(true)  // quieter music under the story
        coroutineScope {
            // Fetch the next sentence's audio while the current one plays
            var next = async { companion.fetchSpeech(items[0].first) }
            for (i in items.indices) {
                val audio = next.await()
                if (i + 1 < items.size) next = async { companion.fetchSpeech(items[i + 1].first) }
                current = i
                if (audio != null) companion.playAndWait(audio) else delay(2500)
                delay(600)
            }
        }
        delay(1500)
        musicPlayer.duck(false)
        current = -1
    }

    if (current in sentences.indices) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xDD1A237E)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Crossfade(targetState = current, animationSpec = tween(900), label = "story_picture") { i ->
                    Image(
                        painter = painterResource(sentences[i].second),
                        contentDescription = null,
                        modifier = Modifier.size(260.dp)
                    )
                }
                Text(
                    text = sentences[current].first,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp, start = 48.dp, end = 48.dp)
                )
            }
        }
    }
}
