package io.github.tomsadeh.puki.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser

/**
 * A page to color in: shapes drawn in a 100x100 box, back to front. A tap inside a shape fills
 * it; the outlines are drawn over the paint, like a coloring book's lines.
 */
sealed interface CanvasPage

class ColoringPage(val id: String, vararg shapes: String) : CanvasPage {
    val shapes: List<Path> = shapes.map { PathParser().parsePathString(it).toPath() }
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r},$cy A$r,$r 0 1,0 ${cx + r},$cy A$r,$r 0 1,0 ${cx - r},$cy Z"

/** A page with a big letter (or number) to paint over; each visit brings the next one. */
class TracePage(val numbers: Boolean) : CanvasPage

/** Blank paper first, then the pages, in the order the page button goes through them. */
val coloringPages: List<CanvasPage?> = listOf(
    null,
    ColoringPage(
        "fish",
        "M68,50 L94,32 L90,50 L94,68 Z",                  // tail
        "M36,34 Q48,16 62,34 Z",                          // top fin
        "M14,50 A30,19 0 1,0 74,50 A30,19 0 1,0 14,50 Z",  // body
        "M40,52 Q50,44 56,54 Q48,62 40,52 Z",             // side fin
        circle(28f, 46f, 5f),                             // eye
        circle(10f, 26f, 3.5f), circle(16f, 14f, 2.5f)    // bubbles
    ),
    ColoringPage(
        "flower",
        "M48.5,52 L51.5,52 L51.5,94 L48.5,94 Z",          // stem
        "M51.5,78 Q66,62 76,68 Q66,82 51.5,82 Z",         // leaf
        "M48.5,70 Q34,56 24,62 Q34,76 48.5,76 Z",         // leaf
        circle(50f, 25f, 12f), circle(63f, 34.5f, 12f), circle(58f, 49.5f, 12f),
        circle(42f, 49.5f, 12f), circle(37f, 34.5f, 12f), // petals
        circle(50f, 38f, 9f)                              // middle
    ),
    ColoringPage(
        "house",
        circle(86f, 14f, 8f),                             // sun
        "M24,46 L76,46 L76,88 L24,88 Z",                  // wall
        "M16,48 L50,16 L84,48 Z",                         // roof
        "M44,64 L56,64 L56,88 L44,88 Z",                  // door
        "M29,54 L39,54 L39,64 L29,64 Z",                  // window
        "M61,54 L71,54 L71,64 L61,64 Z"                   // window
    ),
    ColoringPage(
        "car",
        "M8,66 L14,50 L34,46 L44,34 L66,34 L78,48 L92,52 L92,68 L8,68 Z",  // body
        "M37,46 L46,38 L54,38 L54,46 Z",                  // front window
        "M58,38 L64,38 L72,46 L58,46 Z",                  // back window
        circle(28f, 68f, 10f), circle(74f, 68f, 10f),     // wheels
        circle(28f, 68f, 4f), circle(74f, 68f, 4f),       // hubs
        circle(88f, 58f, 2.5f)                            // light
    ),
    TracePage(numbers = false),
    TracePage(numbers = true)
)

/** [text] as a big glyph from the font, centered on paper of [size]. */
fun glyphPath(text: String, size: Size): Path {
    val paint = android.graphics.Paint().apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = size.height * 0.78f
        isAntiAlias = true
    }
    val path = android.graphics.Path()
    paint.getTextPath(text, 0, text.length, 0f, 0f, path)
    val bounds = android.graphics.RectF()
    path.computeBounds(bounds, true)
    path.offset(size.width / 2 - bounds.centerX(), size.height / 2 - bounds.centerY())
    return path.asComposePath()
}

/** Points on a grid [step] apart that fall inside [glyph]: painting near them is what counts. */
fun samplesInside(glyph: Path, step: Float): List<Offset> {
    val b = glyph.getBounds()
    if (b.isEmpty) return emptyList()
    val region = android.graphics.Region().apply {
        setPath(glyph.asAndroidPath(), android.graphics.Region(b.left.toInt() - 1, b.top.toInt() - 1, b.right.toInt() + 1, b.bottom.toInt() + 1))
    }
    val points = mutableListOf<Offset>()
    var y = b.top + step / 2
    while (y < b.bottom) {
        var x = b.left + step / 2
        while (x < b.right) {
            if (region.contains(x.toInt(), y.toInt())) points.add(Offset(x, y))
            x += step
        }
        y += step
    }
    return points
}

/** The letter to trace, pale (under the paint). */
fun DrawScope.drawTraceGlyph(glyph: Path) {
    drawPath(glyph, Color(0xFFEDE7F6))
}

/** The letter's dashed edge (over the paint, so its shape always shows). */
fun DrawScope.drawTraceEdge(glyph: Path) {
    val width = minOf(size.width, size.height) * 0.008f
    drawPath(glyph, Color(0xFF9575CD), style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 3, width * 2.5f))))
}

/** The page's shapes placed on paper of [size]: centered, as large as fits with a margin. */
fun ColoringPage.placed(size: Size): List<Path> {
    val scale = minOf(size.width, size.height) * 0.88f / 100f
    val m = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate((size.width - 100f * scale) / 2f, (size.height - 100f * scale) / 2f)
    }
    return shapes.map { shape -> Path().apply { addPath(shape); asAndroidPath().transform(m) } }
}

/** The front-most shape under [point], or -1. */
fun shapeAt(shapes: List<Path>, point: Offset): Int {
    for (i in shapes.indices.reversed()) {
        val bounds = shapes[i].getBounds()
        if (!bounds.contains(point)) continue
        val clip = android.graphics.Region(bounds.left.toInt() - 1, bounds.top.toInt() - 1, bounds.right.toInt() + 1, bounds.bottom.toInt() + 1)
        val region = android.graphics.Region().apply { setPath(shapes[i].asAndroidPath(), clip) }
        if (region.contains(point.x.toInt(), point.y.toInt())) return i
    }
    return -1
}

/** The paint in the shapes, back to front; an unfilled one is paper, so it hides what's behind it. */
fun DrawScope.drawPageFills(shapes: List<Path>, fills: Map<Int, Color>, paper: Color) {
    shapes.forEachIndexed { i, shape -> drawPath(shape, fills[i] ?: paper) }
}

/** The page's lines (over the strokes, like a coloring book), each hidden where a shape in front covers it. */
fun DrawScope.drawPageLines(shapes: List<Path>) {
    val width = minOf(size.width, size.height) * 0.009f
    val line = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    shapes.forEachIndexed { i, shape ->
        clippedOutside(shapes, i + 1) { drawPath(shape, Color(0xFF4E342E), style = line) }
    }
}

/** Draws [block] everywhere except inside shapes [from] onwards. */
private fun DrawScope.clippedOutside(shapes: List<Path>, from: Int, block: DrawScope.() -> Unit) {
    if (from >= shapes.size) block()
    else clipPath(shapes[from], ClipOp.Difference) { clippedOutside(shapes, from + 1, block) }
}
