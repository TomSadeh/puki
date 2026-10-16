package com.olamkatan.ui.canvas

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olamkatan.R
import com.olamkatan.audio.CompanionStatus
import com.olamkatan.audio.Sfx
import com.olamkatan.theme.EarthBrown
import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.components.BackArrowIcon
import com.olamkatan.ui.components.idleBob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.sin

data class DrawPath(val path: List<Offset>, val color: Color, val strokeWidth: Float)

// Six pots on the left, three more under the brushes on the right
private val leftColors = listOf(
    Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFFFFEE58),
    Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFFFF9800)
)
private val rightColors = listOf(Color(0xFFF48FB1), Color(0xFF8D6E63), Color(0xFF424242))

/** Thin and fat brushes, as stroke widths in dp. */
private val brushWidths = listOf(10f, 28f)

private val PaperColor = Color(0xFFFFFDF6)
private val Highlight = Color(0xFFFFD54F)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreeCanvasScreen(onBack: () -> Unit) {
    var selectedColor by remember { mutableStateOf(leftColors[0]) }
    var brushDp by remember { mutableStateOf(brushWidths[1]) }
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentPath by remember { mutableStateOf(listOf<Offset>()) }
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val brushSize = with(density) { brushDp.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var looking by remember { mutableStateOf(false) }
    var lastLooked by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { companion?.wake() }

    fun pickColor(color: Color) {
        selectedColor = color
        sfxPlayer.play(Sfx.POP, 0.6f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawStudioWall() }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left: back, then the paint pots
            Column(
                modifier = Modifier.width(136.dp).fillMaxHeight().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(EarthBrown.copy(alpha = 0.85f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { BackArrowIcon() }
                leftColors.forEachIndexed { i, color ->
                    PaintPot(color = color, selected = color == selectedColor, index = i, onClick = { pickColor(color) })
                }
            }

            // The easel with a sheet of paper taped to it
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 30.dp, bottom = 22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(10.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFC8935B), Color(0xFFAE7642))))
                        .padding(18.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(4.dp, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(PaperColor)
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(selectedColor, brushSize) {
                                // A tap leaves a dot of paint
                                detectTapGestures { offset -> paths.add(DrawPath(listOf(offset), selectedColor, brushSize)) }
                            }
                            .pointerInput(selectedColor, brushSize) {
                                detectDragGestures(
                                    onDragStart = { offset -> currentPath = listOf(offset) },
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
                    ) {
                        drawPaperGrain()
                        drawArtwork(paths)
                        // The stroke being drawn right now
                        drawStroke(DrawPath(currentPath, selectedColor, brushSize))
                    }
                }
                // Strips of tape holding the paper's corners
                Canvas(modifier = Modifier.fillMaxSize()) { drawTape() }

                // פוקי looks at the drawing: on tap, or by itself after a pause in drawing
                val drawn = paths.size
                val companionStatus = companion?.status?.collectAsStateWithLifecycle()?.value
                fun lookNow() {
                    if (companion == null || looking || canvasSize.width == 0) return
                    looking = true
                    lastLooked = drawn
                    scope.launch {
                        val png = snapshot(canvasSize, density, paths.toList())
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
                            .offset(y = (-24).dp)
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
            }

            // Right: clear (long press, for the grown-up), brushes and three more pots
            Column(
                modifier = Modifier.width(136.dp).fillMaxHeight().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFFFF8A80))
                        .combinedClickable(
                            onClick = { },
                            onLongClick = {
                                paths.clear()
                                sfxPlayer.play(Sfx.WHOOSH)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(R.drawable.ic_stamp_trash), contentDescription = null, modifier = Modifier.size(38.dp))
                }
                brushWidths.forEachIndexed { i, width ->
                    ToolPlate(selected = brushDp == width, index = i, onClick = {
                        brushDp = width
                        sfxPlayer.play(Sfx.POP, 0.6f)
                    }) {
                        Canvas(modifier = Modifier.size(84.dp)) { drawBrush(selectedColor, fat = i == 1) }
                    }
                }
                rightColors.forEachIndexed { i, color ->
                    PaintPot(color = color, selected = color == selectedColor, index = i + 6, onClick = { pickColor(color) })
                }
            }
        }
    }
}

/** A pot of paint. The chosen one lifts up, wobbles and has a brush standing in it. */
@Composable
private fun PaintPot(color: Color, selected: Boolean, index: Int, onClick: () -> Unit) {
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "pot_lift"
    )
    Canvas(
        modifier = Modifier
            .idleBob(index, amount = if (selected) 1.4f else 0.4f)
            .graphicsLayer {
                translationY = -lift * 10.dp.toPx()
                scaleX = 1f + lift * 0.14f
                scaleY = 1f + lift * 0.14f
            }
            .size(width = 100.dp, height = 92.dp)
            .clickable(onClick = onClick)
    ) { drawPaintPot(color, lift) }
}

/** A round plate for a brush; the chosen one is ringed in yellow and lifted. */
@Composable
private fun ToolPlate(selected: Boolean, index: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "plate_lift"
    )
    // The spring overshoots; colours must stay within 0..1
    val shown = lift.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .idleBob(index + 9, amount = if (selected) 1.2f else 0.3f)
            .graphicsLayer {
                scaleX = 1f + lift * 0.12f
                scaleY = 1f + lift * 0.12f
            }
            .size(100.dp)
            .shadow(if (selected) 10.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.6f + 0.4f * shown))
            .border(6.dp, Highlight.copy(alpha = shown), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ---------- Drawing the studio ----------

/** A warm wall with soft polka dots behind the easel. */
private fun DrawScope.drawStudioWall() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF1DC), Color(0xFFFFE2BF))))
    val dot = Color(0x22FF8A65)
    val step = 70.dp.toPx()
    var row = 0
    var y = step / 2
    while (y < size.height) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < size.width) {
            drawCircle(dot, 6.dp.toPx(), Offset(x, y))
            x += step
        }
        y += step * 0.75f
        row++
    }
    // Floorboards along the bottom
    val floorTop = size.height * 0.93f
    drawRect(Color(0xFFD7A86E), Offset(0f, floorTop), Size(size.width, size.height - floorTop))
    drawRect(Color(0xFFBF8A55), Offset(0f, floorTop), Size(size.width, 4.dp.toPx()))
}

/** Very faint fibres so the page reads as paper rather than a white screen. */
private fun DrawScope.drawPaperGrain() {
    val fibre = Color(0x068D6E63)
    val step = 22.dp.toPx()
    var y = step
    var i = 0
    while (y < size.height) {
        val wobble = sin(i * 1.7f) * 3.dp.toPx()
        drawLine(fibre, Offset(0f, y + wobble), Offset(size.width, y - wobble), 1.dp.toPx())
        y += step
        i++
    }
}

private fun DrawScope.drawTape() {
    val tape = Color(0xAAFFF59D)
    val w = 110.dp.toPx()
    val h = 34.dp.toPx()
    listOf(
        Triple(Offset(30.dp.toPx(), 26.dp.toPx()), -35f, tape),
        Triple(Offset(size.width - 30.dp.toPx(), 26.dp.toPx()), 35f, Color(0xAAB3E5FC)),
        Triple(Offset(30.dp.toPx(), size.height - 26.dp.toPx()), 35f, Color(0xAAF8BBD0)),
        Triple(Offset(size.width - 30.dp.toPx(), size.height - 26.dp.toPx()), -35f, tape)
    ).forEach { (c, angle, color) ->
        rotate(angle, pivot = c) {
            drawRect(color, Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
            // Zig-zag torn ends
            for (end in listOf(-1f, 1f)) {
                val x = c.x + end * w / 2
                var y = c.y - h / 2
                val tooth = h / 5
                while (y < c.y + h / 2 - 1f) {
                    val tri = Path().apply {
                        moveTo(x, y); lineTo(x + end * tooth * 0.6f, y + tooth / 2); lineTo(x, y + tooth); close()
                    }
                    drawPath(tri, color)
                    y += tooth
                }
            }
        }
    }
}

private fun DrawScope.drawPaintPot(color: Color, lift: Float) {
    val w = size.width
    val h = size.height
    val dark = color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f)
    // Shadow on the "table"
    drawOval(Color(0x33000000), Offset(w * 0.2f, h * 0.88f), Size(w * 0.6f, h * 0.1f))

    // A brush standing in the chosen pot
    if (lift > 0.01f) {
        rotate(18f, pivot = Offset(w * 0.5f, h * 0.45f)) {
            val top = h * (0.42f - 0.42f * lift)
            drawLine(Color(0xFFA1683A), Offset(w * 0.5f, top), Offset(w * 0.5f, h * 0.5f), w * 0.07f, StrokeCap.Round)
            drawRoundRect(Color(0xFFB0BEC5), Offset(w * 0.46f, h * 0.38f), Size(w * 0.08f, h * 0.1f), CornerRadius(3f))
        }
    }

    // Pot body: a white ceramic jar with paint dribbling down and a coloured band
    val bodyTop = h * 0.36f
    val bodyLeft = w * 0.18f
    val bodyW = w * 0.64f
    drawRoundRect(Color(0xFFFAFAFA), Offset(bodyLeft, bodyTop), Size(bodyW, h * 0.54f), CornerRadius(w * 0.12f))
    // Paint pooled at the bottom, and drips running down from the rim
    drawRoundRect(color.copy(alpha = 0.35f), Offset(bodyLeft, h * 0.76f), Size(bodyW, h * 0.14f), CornerRadius(w * 0.12f))
    listOf(0.3f to 0.3f, 0.5f to 0.4f, 0.68f to 0.2f).forEach { (fx, len) ->
        val x = w * fx
        drawLine(color, Offset(x, bodyTop), Offset(x, bodyTop + h * len), w * 0.07f, StrokeCap.Round)
        drawCircle(color, w * 0.05f, Offset(x, bodyTop + h * len))
    }
    // Rim and the paint inside
    drawOval(Color(0xFFE0E0E0), Offset(w * 0.14f, h * 0.27f), Size(w * 0.72f, h * 0.18f))
    drawOval(color, Offset(w * 0.19f, h * 0.29f), Size(w * 0.62f, h * 0.13f))
    drawOval(dark.copy(alpha = 0.35f), Offset(w * 0.3f, h * 0.33f), Size(w * 0.4f, h * 0.06f))
    // Shine
    drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(bodyLeft + w * 0.06f, h * 0.48f), Size(w * 0.06f, h * 0.3f), CornerRadius(w * 0.03f))
}

/** A paintbrush lying diagonally, its tip dipped in [color]. */
private fun DrawScope.drawBrush(color: Color, fat: Boolean) {
    val w = size.width
    val h = size.height
    rotate(-45f, pivot = Offset(w / 2, h / 2)) {
        val cx = w / 2
        val handleW = if (fat) w * 0.13f else w * 0.09f
        drawLine(Color(0xFFA1683A), Offset(cx, h * 0.92f), Offset(cx, h * 0.5f), handleW, StrokeCap.Round)
        val ferruleW = if (fat) w * 0.22f else w * 0.13f
        drawRoundRect(Color(0xFFB0BEC5), Offset(cx - ferruleW / 2, h * 0.38f), Size(ferruleW, h * 0.14f), CornerRadius(4f))
        val tipW = if (fat) w * 0.3f else w * 0.15f
        val tip = Path().apply {
            moveTo(cx - tipW / 2, h * 0.39f)
            cubicTo(cx - tipW / 2, h * 0.2f, cx, h * 0.12f, cx, h * 0.04f)
            cubicTo(cx, h * 0.12f, cx + tipW / 2, h * 0.2f, cx + tipW / 2, h * 0.39f)
            close()
        }
        drawPath(tip, color)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - tipW * 0.15f, h * 0.34f), Offset(cx - tipW * 0.05f, h * 0.16f), w * 0.025f, StrokeCap.Round)
    }
}

// ---------- The artwork ----------

private fun DrawScope.drawStroke(stroke: DrawPath) {
    val pts = stroke.path
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        drawCircle(stroke.color, stroke.strokeWidth / 2, pts[0])
        return
    }
    // Smooth the finger's path through the midpoints so lines look painted, not jagged
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size - 1) {
            val mid = (pts[i] + pts[i + 1]) / 2f
            quadraticBezierTo(pts[i].x, pts[i].y, mid.x, mid.y)
        }
        lineTo(pts.last().x, pts.last().y)
    }
    drawPath(path, stroke.color, style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawArtwork(paths: List<DrawPath>) {
    paths.forEach { drawStroke(it) }
}

/** The drawing as a PNG, scaled down to at most 640 px (plenty for Gemma to see it). */
private fun snapshot(size: IntSize, density: Density, paths: List<DrawPath>): ByteArray {
    val factor = 640f / maxOf(size.width, size.height)
    val bitmap = ImageBitmap((size.width * factor).toInt(), (size.height * factor).toInt())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap),
        Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
        drawRect(Color.White)
        scale(factor, factor, pivot = Offset.Zero) { drawArtwork(paths) }
    }
    return ByteArrayOutputStream().also {
        bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
}
