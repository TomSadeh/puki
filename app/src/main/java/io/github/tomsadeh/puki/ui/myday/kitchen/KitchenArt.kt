package io.github.tomsadeh.puki.ui.myday.kitchen

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin

val BatterColor = Color(0xFFFFE082)
private val Lumps = Color(0xFFFFFDE7)

/** A tiled kitchen wall with a wooden counter along the bottom. */
fun DrawScope.drawKitchen() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3))))
    val tile = 64f * density
    var y = 0f
    var row = 0
    while (y < size.height * 0.78f) {
        var x = if (row % 2 == 0) 0f else -tile / 2
        while (x < size.width) {
            drawRoundRect(Color(0x14FF8F00), Offset(x + 3, y + 3), Size(tile - 6, tile - 6), CornerRadius(8f * density))
            x += tile
        }
        y += tile
        row++
    }
    val counterTop = size.height * 0.78f
    drawRect(Color(0xFFBCAAA4), Offset(0f, counterTop), Size(size.width, size.height - counterTop))
    drawRect(Color(0xFFD7CCC8), Offset(0f, counterTop), Size(size.width, 10f * density))
}

/**
 * The mixing bowl, seen a little from above: [fill] (0..1) of what's been added, [smooth] (0..1)
 * how well it's stirred (lumps fade), [swirl] the batter's turn in radians, and the spoon at
 * [spoonAngle] if any.
 */
fun DrawScope.drawBowl(fill: Float, smooth: Float, swirl: Float, spoonAngle: Float?) {
    val w = size.width
    val h = size.height
    val rimY = h * BOWL_RIM
    val oh = h * 0.24f  // the opening's height
    val c = Offset(w / 2, rimY)
    // Inside the bowl
    drawOval(Color(0xFF1976D2), Offset(0f, rimY - oh / 2), Size(w, oh))
    // The batter, rising with each thing added
    if (fill > 0f) {
        val bw = w * (0.7f + 0.22f * fill)
        val bh = oh * (0.62f + 0.3f * fill)
        val topLeft = Offset(c.x - bw / 2, c.y - bh / 2 + oh * 0.06f)
        drawOval(lerp(Color(0xFFFFF3E0), BatterColor, smooth), topLeft, Size(bw, bh))
        val bc = Offset(c.x, c.y + oh * 0.06f)
        // Lumps of flour and egg, fading as it's stirred, turning with the swirl
        for (i in 0 until 9) {
            val a = swirl + i * 0.7f
            val r = 0.12f + (i % 3) * 0.11f
            val p = Offset(bc.x + cos(a) * bw * r, bc.y + sin(a) * bh * r)
            drawCircle(Lumps.copy(alpha = (1f - smooth) * 0.95f), w * 0.028f, p)
            if (i % 3 == 0) drawCircle(Color(0xFFFFB300).copy(alpha = (1f - smooth) * 0.9f), w * 0.022f, p + Offset(w * 0.05f, 0f))
        }
        // A swirl line once it's getting smooth
        if (smooth > 0.3f) {
            val swirlPath = Path()
            for (k in 0..40) {
                val t = k / 40f
                val a = swirl + t * 7f
                val p = Offset(bc.x + cos(a) * bw * t * 0.42f, bc.y + sin(a) * bh * t * 0.42f)
                if (k == 0) swirlPath.moveTo(p.x, p.y) else swirlPath.lineTo(p.x, p.y)
            }
            drawPath(swirlPath, Color(0xFFFFA000).copy(alpha = smooth * 0.8f), style = Stroke(w * 0.014f, cap = StrokeCap.Round))
        }
    }
    // The spoon, its bowl in the batter and its handle leaning out
    spoonAngle?.let { a ->
        val tip = Offset(c.x + cos(a) * w * 0.28f, c.y + sin(a) * oh * 0.3f)
        val handle = Offset(tip.x + w * 0.12f, tip.y - h * 0.5f)
        drawLine(Color(0xFFA1683A), tip, handle, w * 0.035f, StrokeCap.Round)
        drawOval(Color(0xFFBCAAA4), tip - Offset(w * 0.055f, oh * 0.14f), Size(w * 0.11f, oh * 0.28f))
    }
    // The bowl's body, in front: down from the rim and back along the opening's front edge
    val k = oh / 2 * 1.33f
    val body = Path().apply {
        moveTo(0f, rimY)
        cubicTo(0f, h * 1.02f, w, h * 1.02f, w, rimY)
        cubicTo(w, rimY + k, 0f, rimY + k, 0f, rimY)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFF64B5F6), Color(0xFF1E88E5)), startY = rimY, endY = h))
    listOf(0.25f to 0.72f, 0.5f to 0.8f, 0.75f to 0.72f).forEach { (fx, fy) ->
        drawCircle(Color.White.copy(alpha = 0.8f), w * 0.03f, Offset(w * fx, h * fy))
    }
    // The rim
    drawOval(Color(0xFF90CAF9), Offset(0f, rimY - oh / 2), Size(w, oh), style = Stroke(w * 0.022f))
}

/** Where the bowl's rim sits, as a fraction of its box's height (the stirring circles round it). */
const val BOWL_RIM = 0.32f

/** A paper bag of flour. */
fun DrawScope.drawFlour() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFFF5F5F5), Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, h * 0.72f), CornerRadius(w * 0.06f))
    val top = Path().apply { moveTo(w * 0.2f, h * 0.22f); lineTo(w * 0.3f, h * 0.08f); lineTo(w * 0.7f, h * 0.08f); lineTo(w * 0.8f, h * 0.22f); close() }
    drawPath(top, Color(0xFFE0E0E0))
    drawCircle(Color(0xFFFFCC80), w * 0.14f, Offset(w * 0.5f, h * 0.55f))
    // A sprig of wheat on the label
    drawLine(Color(0xFFF9A825), Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.45f), w * 0.02f, StrokeCap.Round)
    for (k in 0..2) {
        val y = h * (0.47f + k * 0.05f)
        drawOval(Color(0xFFF9A825), Offset(w * 0.42f, y), Size(w * 0.07f, h * 0.035f))
        drawOval(Color(0xFFF9A825), Offset(w * 0.51f, y), Size(w * 0.07f, h * 0.035f))
    }
}

/** A sugar jar with cubes. */
fun DrawScope.drawSugar() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFFE1F5FE).copy(alpha = 0.9f), Offset(w * 0.2f, h * 0.3f), Size(w * 0.6f, h * 0.62f), CornerRadius(w * 0.1f))
    drawRoundRect(Color(0xFFF48FB1), Offset(w * 0.24f, h * 0.18f), Size(w * 0.52f, h * 0.14f), CornerRadius(w * 0.05f))
    listOf(0.32f to 0.6f, 0.5f to 0.66f, 0.42f to 0.48f, 0.58f to 0.5f, 0.36f to 0.76f, 0.56f to 0.78f).forEach { (fx, fy) ->
        drawRoundRect(Color.White, Offset(w * fx, h * fy), Size(w * 0.12f, w * 0.12f), CornerRadius(w * 0.02f))
    }
}

/** Two eggs. */
fun DrawScope.drawEggs() {
    val w = size.width
    val h = size.height
    drawOval(Color(0xFFFFF8E1), Offset(w * 0.14f, h * 0.3f), Size(w * 0.38f, h * 0.5f))
    drawOval(Color(0xFFFFE0B2), Offset(w * 0.46f, h * 0.36f), Size(w * 0.38f, h * 0.5f))
    drawOval(Color.White.copy(alpha = 0.7f), Offset(w * 0.22f, h * 0.38f), Size(w * 0.08f, h * 0.12f))
    drawOval(Color.White.copy(alpha = 0.6f), Offset(w * 0.54f, h * 0.44f), Size(w * 0.08f, h * 0.12f))
}

/** A milk carton. */
fun DrawScope.drawMilk() {
    val w = size.width
    val h = size.height
    drawRect(Color.White, Offset(w * 0.25f, h * 0.3f), Size(w * 0.5f, h * 0.62f))
    val roof = Path().apply { moveTo(w * 0.25f, h * 0.3f); lineTo(w * 0.5f, h * 0.12f); lineTo(w * 0.75f, h * 0.3f); close() }
    drawPath(roof, Color(0xFF42A5F5))
    drawRect(Color(0xFF42A5F5), Offset(w * 0.25f, h * 0.62f), Size(w * 0.5f, h * 0.12f))
    drawCircle(Color(0xFF90CAF9), w * 0.08f, Offset(w * 0.5f, h * 0.47f))
    drawRect(Color(0xFF1E88E5), Offset(w * 0.45f, h * 0.08f), Size(w * 0.1f, h * 0.06f))
}

/** The oven: [baking] (0..1) glows warm through the window while the cake bakes. */
fun DrawScope.drawOven(baking: Float, open: Boolean) {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFFECEFF1), Offset.Zero, size, CornerRadius(w * 0.06f))
    // Knobs
    for (k in 0..2) drawCircle(Color(0xFF90A4AE), w * 0.05f, Offset(w * (0.3f + k * 0.2f), h * 0.1f))
    drawRect(Color(0xFFCFD8DC), Offset(0f, h * 0.18f), Size(w, h * 0.02f))
    if (open) {
        // The dark inside, and the door down
        drawRoundRect(Color(0xFF37474F), Offset(w * 0.1f, h * 0.26f), Size(w * 0.8f, h * 0.58f), CornerRadius(w * 0.04f))
        drawRoundRect(Color(0xFFB0BEC5), Offset(w * 0.06f, h * 0.84f), Size(w * 0.88f, h * 0.12f), CornerRadius(w * 0.03f))
    } else {
        drawRoundRect(Color(0xFFB0BEC5), Offset(w * 0.08f, h * 0.24f), Size(w * 0.84f, h * 0.68f), CornerRadius(w * 0.05f))
        val glow = lerp(Color(0xFF455A64), Color(0xFFFF9800), baking)
        drawRoundRect(glow, Offset(w * 0.18f, h * 0.36f), Size(w * 0.64f, h * 0.4f), CornerRadius(w * 0.04f))
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(w * 0.22f, h * 0.39f), Size(w * 0.14f, h * 0.06f), CornerRadius(w * 0.02f))
        drawLine(Color(0xFF78909C), Offset(w * 0.25f, h * 0.29f), Offset(w * 0.75f, h * 0.29f), w * 0.04f, StrokeCap.Round)
    }
}

/** A cake tin full of batter (before baking). */
fun DrawScope.drawPan() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF9E9E9E), Offset(w * 0.08f, h * 0.4f), Size(w * 0.84f, h * 0.4f), CornerRadius(w * 0.05f))
    drawOval(Color(0xFFBDBDBD), Offset(w * 0.08f, h * 0.3f), Size(w * 0.84f, h * 0.22f))
    drawOval(BatterColor, Offset(w * 0.12f, h * 0.33f), Size(w * 0.76f, h * 0.16f))
}

/** The baked cake: sponge, pink icing and drips. Toppings are drawn on it separately. */
fun DrawScope.drawCake() {
    val w = size.width
    val h = size.height
    drawOval(Color(0xFFE0E0E0), Offset(w * 0.02f, h * 0.84f), Size(w * 0.96f, h * 0.14f))  // plate
    drawRoundRect(Color(0xFFD7A15E), Offset(w * 0.1f, h * 0.42f), Size(w * 0.8f, h * 0.46f), CornerRadius(w * 0.04f))
    drawRect(Color(0xFFFFF59D), Offset(w * 0.1f, h * 0.62f), Size(w * 0.8f, h * 0.05f))  // cream layer
    // Icing on top, dripping down the sides
    drawOval(Color(0xFFF48FB1), Offset(w * 0.08f, h * 0.32f), Size(w * 0.84f, h * 0.22f))
    listOf(0.16f to 0.12f, 0.3f to 0.07f, 0.46f to 0.14f, 0.62f to 0.08f, 0.78f to 0.12f).forEach { (fx, len) ->
        drawLine(Color(0xFFF48FB1), Offset(w * fx, h * 0.46f), Offset(w * fx, h * (0.46f + len)), w * 0.05f, StrokeCap.Round)
    }
    drawOval(Color.White.copy(alpha = 0.35f), Offset(w * 0.2f, h * 0.35f), Size(w * 0.2f, h * 0.05f))
}

/** Toppings, each drawn in a box of its own. */
enum class Topping { STRAWBERRY, CANDLE, CHERRY, HEART }

fun DrawScope.drawTopping(topping: Topping, flame: Float = 0f) {
    val w = size.width
    val h = size.height
    when (topping) {
        Topping.STRAWBERRY -> {
            val berry = Path().apply {
                moveTo(w * 0.5f, h * 0.92f)
                cubicTo(w * 0.1f, h * 0.6f, w * 0.12f, h * 0.25f, w * 0.5f, h * 0.28f)
                cubicTo(w * 0.88f, h * 0.25f, w * 0.9f, h * 0.6f, w * 0.5f, h * 0.92f)
                close()
            }
            drawPath(berry, Color(0xFFE53935))
            for (k in 0 until 6) drawCircle(Color(0xFFFFEB3B), w * 0.025f, Offset(w * (0.35f + (k % 3) * 0.15f), h * (0.45f + (k / 3) * 0.18f)))
            drawOval(Color(0xFF43A047), Offset(w * 0.3f, h * 0.18f), Size(w * 0.4f, h * 0.16f))
        }
        Topping.CANDLE -> {
            drawRoundRect(Color(0xFF64B5F6), Offset(w * 0.4f, h * 0.35f), Size(w * 0.2f, h * 0.6f), CornerRadius(w * 0.04f))
            for (k in 0..2) drawLine(Color.White, Offset(w * 0.4f, h * (0.45f + k * 0.17f)), Offset(w * 0.6f, h * (0.4f + k * 0.17f)), w * 0.04f)
            val f = 1f + 0.12f * sin(flame * 6.28f)
            drawOval(Color(0xFFFFB300), Offset(w * (0.5f - 0.09f * f), h * (0.06f)), Size(w * 0.18f * f, h * 0.26f))
            drawOval(Color(0xFFFFF59D), Offset(w * 0.46f, h * 0.15f), Size(w * 0.08f, h * 0.13f))
        }
        Topping.CHERRY -> {
            drawLine(Color(0xFF6D4C41), Offset(w * 0.5f, h * 0.45f), Offset(w * 0.62f, h * 0.1f), w * 0.04f, StrokeCap.Round)
            drawCircle(Color(0xFFC62828), w * 0.3f, Offset(w * 0.5f, h * 0.62f))
            drawCircle(Color.White.copy(alpha = 0.5f), w * 0.07f, Offset(w * 0.4f, h * 0.52f))
        }
        Topping.HEART -> {
            val heart = Path().apply {
                moveTo(w * 0.5f, h * 0.88f)
                cubicTo(w * 0.05f, h * 0.55f, w * 0.15f, h * 0.12f, w * 0.5f, h * 0.32f)
                cubicTo(w * 0.85f, h * 0.12f, w * 0.95f, h * 0.55f, w * 0.5f, h * 0.88f)
                close()
            }
            drawPath(heart, Color(0xFFAB47BC))
            drawCircle(Color.White.copy(alpha = 0.45f), w * 0.06f, Offset(w * 0.34f, h * 0.38f))
        }
    }
}

/** A circling arrow around the bowl, showing which way to stir. */
fun DrawScope.drawStirGuide(phase: Float) {
    val c = Offset(size.width / 2, size.height / 2)
    val rx = size.width * 0.5f
    val ry = size.height * 0.5f
    for (k in 0 until 10) {
        val a = phase * 6.28f + k * 0.45f
        val p = Offset(c.x + cos(a) * rx, c.y + sin(a) * ry)
        drawCircle(Color(0xFFFF8F00).copy(alpha = 0.15f + k * 0.07f), size.minDimension * (0.012f + k * 0.0015f), p)
    }
    // The arrow head at the front
    val a = phase * 6.28f + 9 * 0.45f
    val p = Offset(c.x + cos(a) * rx, c.y + sin(a) * ry)
    rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f, pivot = p) {
        val s = size.minDimension * 0.05f
        val head = Path().apply { moveTo(p.x + s, p.y); lineTo(p.x - s * 0.6f, p.y - s * 0.7f); lineTo(p.x - s * 0.6f, p.y + s * 0.7f); close() }
        drawPath(head, Color(0xFFFF8F00))
    }
}

/** Where the [index]th topping sits on top of the cake (fractions of the cake's box). */
fun toppingSpot(index: Int): Offset = listOf(
    Offset(0.5f, 0.37f), Offset(0.26f, 0.42f), Offset(0.74f, 0.42f),
    Offset(0.38f, 0.38f), Offset(0.62f, 0.38f), Offset(0.36f, 0.48f),
    Offset(0.64f, 0.48f), Offset(0.5f, 0.47f)
)[index % 8]
