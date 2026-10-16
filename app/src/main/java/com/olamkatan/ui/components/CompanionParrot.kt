package com.olamkatan.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olamkatan.R
import com.olamkatan.audio.CompanionStatus
import com.olamkatan.audio.MicRecorder
import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.ui.common.LocalVoicePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * פוקי sitting in the corner of a game. Shows up once the brain server is ready,
 * flaps its beak while it talks. A tap makes it say something; holding it records
 * the child talking (Whisper on the PC listens) and it answers when let go.
 */
@Composable
fun CompanionParrot(modifier: Modifier = Modifier) {
    val companion = LocalCompanion.current ?: return
    val voicePlayer = LocalVoicePlayer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by companion.status.collectAsStateWithLifecycle()
    val speaking by companion.speaking.collectAsStateWithLifecycle()
    val autoListening by companion.listening.collectAsStateWithLifecycle()

    val recorder = remember { MicRecorder() }
    var listening by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (recorder.isRecording) recorder.stop() } }

    // Asked once; a parent approves the microphone the first time someone holds the parrot
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) { companion.wake() }

    // Beak opens and closes while talking
    var beakOpen by remember { mutableStateOf(false) }
    LaunchedEffect(speaking) {
        beakOpen = false
        while (speaking) {
            beakOpen = !beakOpen
            delay(140)
        }
        beakOpen = false
    }

    val listenScale by animateFloatAsState(if (listening || autoListening) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "listen_scale")

    AnimatedVisibility(
        visible = status == CompanionStatus.READY,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.5f)) { it },
        exit = slideOutVertically { it } + fadeOut()
    ) {
        // (inside, so nothing ticks while the parrot is away)
        val bob by rememberInfiniteTransition(label = "parrot").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (speaking) 260 else 1400), RepeatMode.Reverse),
            label = "bob"
        )
        Box(contentAlignment = Alignment.Center) {
            // Pulsing rings while it listens
            if (listening || autoListening) {
                val pulse by rememberInfiniteTransition(label = "listen").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
                    label = "listen_pulse"
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    for (k in 0..1) {
                        val t = (pulse + k * 0.5f) % 1f
                        drawCircle(
                            color = Color(0xFFFFEB3B).copy(alpha = 0.8f * (1f - t)),
                            radius = size.minDimension * (0.35f + 0.25f * t),
                            center = Offset(size.width / 2, size.height / 2),
                            style = Stroke(width = 6.dp.toPx())
                        )
                    }
                }
            }
            Image(
                painter = painterResource(if (beakOpen) R.drawable.ic_companion_parrot_talk else R.drawable.ic_companion_parrot),
                contentDescription = null,
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleX = listenScale
                        scaleY = listenScale
                        translationY = -bob * (if (speaking) 10.dp.toPx() else 4.dp.toPx())
                        rotationZ = if (speaking) (bob - 0.5f) * 8f else 0f
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            // Holding for a moment starts listening; a quick tap just chats
                            val hold = scope.launch {
                                delay(300)
                                if (!hasMic()) {
                                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    voicePlayer.stop()  // so the mic doesn't hear the app
                                    listening = recorder.start()
                                }
                            }
                            val released = tryAwaitRelease()
                            hold.cancel()
                            if (listening) {
                                listening = false
                                val wav = recorder.stop()
                                if (released) companion.listen(wav)
                            } else if (released && !hold.isCompleted) {
                                companion.chat()
                            }
                        })
                    }
            )
        }
    }
}
