package com.olamkatan.ui.components

import com.olamkatan.ui.common.LocalVoicePlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.olamkatan.R
import com.olamkatan.data.models.BilingualText
import com.olamkatan.theme.CloudWhite
import com.olamkatan.theme.GrassGreen
import com.olamkatan.theme.SunYellow
import com.olamkatan.ui.common.LocalLanguage
import kotlinx.coroutines.delay

@Composable
fun RoundCompleteSheet(
    starsEarned: Int,
    onContinue: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    var showContent by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
        showConfetti = true
        voicePlayer.play(voicePlayer.personalKey("voice_round_done", language), language)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        ConfettiOverlay(show = showConfetti, onFinished = { showConfetti = false })

        AnimatedVisibility(
            visible = showContent,
            enter = scaleIn() + fadeIn()
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(CloudWhite)
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = BilingualText("כל הכבוד!", "Great job!").resolve(language),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_ui_party),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(starsEarned) {
                        Image(
                            painter = painterResource(R.drawable.ic_ui_star),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BigTapButton(
                        text = BilingualText("המשך", "Continue"),
                        backgroundColor = GrassGreen,
                        onClick = onContinue
                    )
                    BigTapButton(
                        text = BilingualText("בית", "Home"),
                        backgroundColor = SunYellow,
                        onClick = onHome
                    )
                }
            }
        }
    }
}
