package com.olamkatan.ui.canvas

import java.io.ByteArrayOutputStream
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.audio.CompanionStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.detectTapGestures
import com.olamkatan.ui.components.BackArrowIcon
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olamkatan.R
import com.olamkatan.data.models.BilingualText
import com.olamkatan.theme.EarthBrown
import com.olamkatan.ui.common.LocalLanguage

data class DrawPath(val path: List<Offset>, val color: Color, val strokeWidth: Float)
data class Stamp(val position: Offset, @DrawableRes val icon: Int)

private val canvasColors = listOf(
    Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFFFFEE58),
    Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFFFF9800)
)

private val stampIcons = listOf(
    R.drawable.ic_sticker_star,
    R.drawable.ic_sticker_flower,
    R.drawable.ic_stamp_ladybug,
    R.drawable.ic_sticker_butterfly,
    R.drawable.ic_sticker_heart,
    R.drawable.ic_sticker_rainbow
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreeCanvasScreen(onBack: () -> Unit) {
    val language = LocalLanguage.current
    var selectedColor by remember { mutableStateOf(canvasColors[0]) }
    var brushSize by remember { mutableStateOf(8f) }
    var selectedStamp by remember { mutableStateOf<Int?>(null) }
    val paths = remember { mutableStateListOf<DrawPath>() }
    val stamps = remember { mutableStateListOf<Stamp>() }
    var currentPath by remember { mutableStateOf(listOf<Offset>()) }
    val stampPainters = stampIcons.associateWith { painterResource(it) }
    val companion = LocalCompanion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var looking by remember { mutableStateOf(false) }
    var lastLooked by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { companion?.wake() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Canvas area
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
                .pointerInput(selectedColor, brushSize, selectedStamp) {
                    if (selectedStamp != null) {
                        // A tap places the selected stamp where the finger touched
                        detectTapGestures { offset ->
                            stamps.add(Stamp(offset, selectedStamp!!))
                        }
                    } else {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPath = currentPath + change.position
                            },
                            onDragEnd = {
                                if (currentPath.size > 1) {
                                    paths.add(DrawPath(currentPath, selectedColor, brushSize))
                                }
                                currentPath = emptyList()
                            }
                        )
                    }
                }
        ) {
            drawArtwork(paths, stamps, stampPainters)
            // The stroke being drawn right now
            drawStroke(DrawPath(currentPath, selectedColor, brushSize))
        }

        // פּוּקִי looks at the drawing: on tap, or by itself after a pause in drawing
        val drawn = paths.size + stamps.size
        val companionStatus = companion?.status?.collectAsStateWithLifecycle()?.value
        fun lookNow() {
            if (companion == null || looking || canvasSize.width == 0) return
            looking = true
            lastLooked = drawn
            scope.launch {
                val png = snapshot(canvasSize, density, paths.toList(), stamps.toList(), stampPainters)
                companion.look(png)
                looking = false
            }
        }
        LaunchedEffect(drawn, companionStatus) {
            if (drawn > 0 && drawn != lastLooked && companionStatus == CompanionStatus.READY) {
                delay(7000)
                lookNow()
            }
        }
        if (companionStatus == CompanionStatus.READY && drawn > 0) {
            val bob by rememberInfiniteTransition(label = "look_bob").animateFloat(
                initialValue = 1f, targetValue = if (looking) 1.12f else 1.04f,
                animationSpec = infiniteRepeatable(tween(if (looking) 300 else 1200), RepeatMode.Reverse), label = "bob"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .graphicsLayer { scaleX = bob; scaleY = bob }
                    .size(84.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF8E1))
                    .clickable { lookNow() },
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(R.drawable.ic_companion_parrot), contentDescription = null, modifier = Modifier.size(70.dp))
            }
        }

        // Back button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(EarthBrown.copy(alpha = 0.7f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) { BackArrowIcon() }

        // Clear button (long press)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = 0.6f))
                .combinedClickable(
                    onClick = { },
                    onLongClick = {
                        paths.clear()
                        stamps.clear()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_stamp_trash),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }

        // Bottom toolbar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFFF5F5F5))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color picker
            canvasColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(if (color == selectedColor && selectedStamp == null) 48.dp else 40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable {
                            selectedColor = color
                            selectedStamp = null
                        }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Brush sizes
            listOf(6f to 24.dp, 14f to 36.dp).forEach { (size, displaySize) ->
                Box(
                    modifier = Modifier
                        .size(displaySize)
                        .clip(CircleShape)
                        .background(if (brushSize == size && selectedStamp == null) EarthBrown else EarthBrown.copy(alpha = 0.4f))
                        .clickable {
                            brushSize = size
                            selectedStamp = null
                        }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Stamps
            stampIcons.forEach { icon ->
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedStamp == icon) Color(0xFFE0E0E0) else Color.Transparent)
                        .clickable { selectedStamp = if (selectedStamp == icon) null else icon },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: DrawPath) {
    if (stroke.path.size < 2) return
    val path = Path().apply {
        moveTo(stroke.path[0].x, stroke.path[0].y)
        for (i in 1 until stroke.path.size) lineTo(stroke.path[i].x, stroke.path[i].y)
    }
    drawPath(path, stroke.color, style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round))
}

private fun DrawScope.drawArtwork(paths: List<DrawPath>, stamps: List<Stamp>, stampPainters: Map<Int, Painter>) {
    paths.forEach { drawStroke(it) }
    // Stamps centered on where they were placed
    val stampSize = 88.dp.toPx()
    stamps.forEach { stamp ->
        val painter = stampPainters.getValue(stamp.icon)
        translate(left = stamp.position.x - stampSize / 2, top = stamp.position.y - stampSize / 2) {
            with(painter) { draw(Size(stampSize, stampSize)) }
        }
    }
}

/** The drawing as a PNG, scaled down to at most 640 px (plenty for Gemma to see it). */
private fun snapshot(
    size: IntSize,
    density: Density,
    paths: List<DrawPath>,
    stamps: List<Stamp>,
    stampPainters: Map<Int, Painter>
): ByteArray {
    val factor = 640f / maxOf(size.width, size.height)
    val bitmap = ImageBitmap((size.width * factor).toInt(), (size.height * factor).toInt())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap),
        Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
        drawRect(Color.White)
        scale(factor, factor, pivot = Offset.Zero) { drawArtwork(paths, stamps, stampPainters) }
    }
    return ByteArrayOutputStream().also {
        bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
}
