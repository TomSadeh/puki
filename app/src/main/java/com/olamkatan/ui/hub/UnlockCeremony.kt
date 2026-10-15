package com.olamkatan.ui.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.olamkatan.R
import com.olamkatan.data.models.BilingualText
import com.olamkatan.theme.CloudWhite
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.LocalVoicePlayer
import com.olamkatan.ui.components.ConfettiOverlay

@Composable
fun UnlockCeremony(
    unlockedIslands: List<IslandDef>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current

    LaunchedEffect(unlockedIslands) {
        if (unlockedIslands.isNotEmpty()) {
            voicePlayer.play("voice_prompt_new_island", language)
        }
    }

    AnimatedVisibility(
        visible = unlockedIslands.isNotEmpty(),
        enter = fadeIn() + scaleIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            ConfettiOverlay(show = true)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ui_unlock),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = BilingualText("אי חדש נפתח!", "New island unlocked!").resolve(language),
                    style = MaterialTheme.typography.headlineLarge,
                    color = CloudWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                unlockedIslands.forEach { island ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (island.drawableRes != 0) {
                            Image(
                                painter = painterResource(island.drawableRes),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                        Text(
                            text = island.name.resolve(language),
                            style = MaterialTheme.typography.headlineMedium,
                            color = CloudWhite
                        )
                    }
                }
            }
        }
    }
}
