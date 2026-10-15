package com.olamkatan.ui.hub

import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.LocalVoicePlayer
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olamkatan.R
import com.olamkatan.ui.hub.components.HubBackground
import com.olamkatan.ui.hub.components.IslandButton
import com.olamkatan.ui.hub.components.IslandHeight
import com.olamkatan.ui.hub.components.IslandWidth
import com.olamkatan.ui.hub.components.StarCounter

private var greetedThisLaunch = false

@Composable
fun HubScreen(
    onIslandClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onStickersClick: () -> Unit
) {
    val hubViewModel: HubViewModel = viewModel()
    val progress by hubViewModel.progress.collectAsStateWithLifecycle()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val voicePlayer = LocalVoicePlayer.current
    val language = LocalLanguage.current

    // Say hello (by name, if the personal lines exist) the first time the hub opens
    LaunchedEffect(Unit) {
        if (!greetedThisLaunch) {
            greetedThisLaunch = true
            delay(600)
            voicePlayer.play(voicePlayer.personalKey("voice_hello", language), language)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HubBackground()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val width = maxWidth
            val height = maxHeight
            fun fx(island: IslandDef) = if (isRtl) 1f - island.fractionalX else island.fractionalX

            // Dotted sea route linking the islands in order, like a treasure map
            Canvas(modifier = Modifier.fillMaxSize()) {
                val points = allIslands.map { Offset(size.width * fx(it), size.height * it.fractionalY + 40.dp.toPx()) }
                val route = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val a = points[i - 1]
                        val b = points[i]
                        // Bow each leg slightly so the route meanders
                        val bend = if (i % 2 == 0) 1f else -1f
                        val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                        val ctrl = mid + Offset(-(b.y - a.y), b.x - a.x) * (0.18f * bend)
                        quadraticBezierTo(ctrl.x, ctrl.y, b.x, b.y)
                    }
                }
                drawPath(
                    route,
                    Color.White.copy(alpha = 0.75f),
                    style = Stroke(
                        width = 7.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 22.dp.toPx()))
                    )
                )
            }

            // Islands nearer the horizon are drawn a little smaller, for depth
            allIslands.forEachIndexed { index, island ->
                val depthScale = 0.82f + 0.35f * ((island.fractionalY - 0.42f) / 0.38f).coerceIn(0f, 1f)
                IslandButton(
                    island = island,
                    isUnlocked = true,
                    onClick = { onIslandClick(island.route) },
                    palmOnLeft = index % 2 == 0,
                    modifier = Modifier
                        .offset(
                            x = width * fx(island) - IslandWidth / 2,
                            y = height * island.fractionalY - IslandHeight / 2
                        )
                        .graphicsLayer {
                            scaleX = depthScale
                            scaleY = depthScale
                        }
                )
            }
        }

        StarCounter(
            stars = progress.totalStars,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )

        // Sticker album — top start
        CornerButton(
            icon = R.drawable.ic_ui_stickers,
            onClick = onStickersClick,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Settings — top end, long-press so the child doesn't open it by accident
        CornerButton(
            icon = R.drawable.ic_ui_settings,
            onLongClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // Quit — bottom start, double-tap to exit
        val activity = LocalContext.current as? Activity
        CornerButton(
            icon = R.drawable.ic_ui_exit,
            onDoubleClick = { activity?.finishAndRemoveTask() },
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // Free canvas — bottom end
        CornerButton(
            icon = R.drawable.ic_ui_canvas,
            onClick = { onIslandClick("canvas") },
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CornerButton(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .size(64.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onDoubleClick = onDoubleClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    }
}
