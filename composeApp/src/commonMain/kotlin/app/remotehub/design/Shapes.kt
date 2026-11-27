package app.remotehub.design

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Material 3 corner radius scale, including the tokens added by the
 * M3 Expressive update (May 2025).
 *
 * Reference: https://m3.material.io/styles/shape/overview-principles
 *   none 0 · extra small 4 · small 8 · medium 12 · large 16 · large increased 20
 *   extra large 28 · extra large increased 32 · extra extra large 48 · full
 *
 * "Full" is expressed as a percentage rather than half of a measured size, which is
 * what the updated spec asks for.
 */
object CornerRadius {
    val none = RoundedCornerShape(0.dp)
    val extraSmall = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val largeIncreased = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val extraLargeIncreased = RoundedCornerShape(32.dp)
    val extraExtraLarge = RoundedCornerShape(48.dp)
    val full = RoundedCornerShape(percent = 50)

    /** Sheets round only their leading edge, as the bottom sheet spec describes. */
    val sheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val barTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

/**
 * The expressive half of the Material 3 shape library. Every shape here is generated
 * from the geometry the shape library defines (regular polygons, alternating-radius
 * "cookie" and "clover" families, stars and arcs) so the set stays consistent instead
 * of being a pile of unrelated drawings.
 *
 * Shapes are decorative, never semantic: the same meaning is never encoded by a
 * single shape, and the resting/selected pair is what changes on interaction.
 */
object ExpressiveShapes {
    val circle = GenericShape { size, _ -> addOval(Rect(0f, 0f, size.width, size.height)) }
    val oval = GenericShape { size, _ -> addOval(Rect(0f, 0f, size.width, size.height)) }
    val square = CornerRadius.none
    val pill = CornerRadius.full
    val slanted = roundedPolygon(listOf(0.16f to 0f, 1f to 0f, 0.84f to 1f, 0f to 1f), 0.16f)
    val arch = GenericShape { size, _ ->
        val r = size.width / 2f
        moveTo(0f, size.height)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, size.width, r * 2f), 180f, 180f, false)
        lineTo(size.width, size.height)
        close()
    }
    val fan = GenericShape { size, _ ->
        val r = min(size.width, size.height) * 0.72f
        moveTo(0f, size.height)
        lineTo(0f, size.height - r)
        arcTo(Rect(-r, size.height - r * 2f, r, size.height), 180f, 90f, false)
        lineTo(size.width, size.height)
        close()
    }
    val gem = roundedPolygon(listOf(0.5f to 0f, 1f to 0.36f, 0.78f to 1f, 0.22f to 1f, 0f to 0.36f), 0.14f)
    val pentagon = regularPolygon(5, rotation = -PI / 2, corner = 0.16f)
    val triangle = regularPolygon(3, rotation = -PI / 2, corner = 0.22f)
    val diamond = regularPolygon(4, rotation = -PI / 2, corner = 0.18f)
    val semicircle = GenericShape { size, _ ->
        moveTo(0f, size.height)
        lineTo(0f, size.height * 0.5f)
        arcTo(Rect(0f, 0f, size.width, size.height), 180f, 180f, false)
        lineTo(size.width, size.height)
        close()
    }
    val cookie4 = alternating(4, inner = 0.80f, corner = 0.5f)
    val cookie6 = alternating(6, inner = 0.84f, corner = 0.5f)
    val cookie9 = alternating(9, inner = 0.88f, corner = 0.5f)
    val cookie12 = alternating(12, inner = 0.90f, corner = 0.5f)
    val clover4 = alternating(4, inner = 0.62f, corner = 0.5f)
    val clover8 = alternating(8, inner = 0.78f, corner = 0.5f)
    val sunny = star(8, inner = 0.74f, corner = 0.34f)
    val verySunny = star(12, inner = 0.80f, corner = 0.34f)
    val burst = star(12, inner = 0.62f, corner = 0.16f)
    val softBurst = star(8, inner = 0.80f, corner = 0.48f)
    val boom = star(6, inner = 0.52f, corner = 0.30f)
    val flower = alternating(8, inner = 0.66f, corner = 0.5f)
    val puffy = puffy(1.0f)
    val puffyDiamond = puffy(0.82f)

    /** A different container per checklist row, so the list is not a wall of circles. */
    val rowContainers = listOf(cookie6, clover4, gem, sunny, pentagon, cookie9, boom, softBurst)

    /** Resting shape morphs into the selected shape, per the shape-morph principle. */
    fun forIndex(index: Int) = rowContainers[index.mod(rowContainers.size)]
}

val LocalExpressiveShapes = staticCompositionLocalOf { ExpressiveShapes }

// --------------------------------------------------------------------------- geometry

private fun Path.roundedThrough(points: List<Pair<Float, Float>>, corner: Float) {
    if (points.size < 3) return
    val n = points.size
    // Each vertex is replaced by a quadratic curve whose control point is the vertex
    // itself; `corner` says how far along the neighbouring edges the curve starts.
    val t = corner.coerceIn(0.02f, 0.5f)
    fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, k: Float) =
        (a.first + (b.first - a.first) * k) to (a.second + (b.second - a.second) * k)

    val start = lerp(points[n - 1], points[0], 1f - t)
    moveTo(start.first, start.second)
    for (i in 0 until n) {
        val vertex = points[i]
        val next = points[(i + 1) % n]
        val entry = lerp(vertex, points[(i + n - 1) % n], t)
        val exit = lerp(vertex, next, t)
        lineTo(entry.first, entry.second)
        quadraticTo(vertex.first, vertex.second, exit.first, exit.second)
    }
    close()
}

private fun roundedPolygon(unit: List<Pair<Float, Float>>, corner: Float) = GenericShape { size, _ ->
    val side = min(size.width, size.height)
    val ox = (size.width - side) / 2f
    val oy = (size.height - side) / 2f
    roundedThrough(unit.map { (x, y) -> (ox + x * side) to (oy + y * side) }, corner)
}

private fun regularPolygon(sides: Int, rotation: Double, corner: Float) = GenericShape { size, _ ->
    val r = min(size.width, size.height) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val points = (0 until sides).map { i ->
        val a = rotation + 2 * PI * i / sides
        (cx + r * cos(a).toFloat()) to (cy + r * sin(a).toFloat())
    }
    roundedThrough(points, corner)
}

private fun alternating(lobes: Int, inner: Float, corner: Float) = GenericShape { size, _ ->
    val outerR = min(size.width, size.height) / 2f
    val innerR = outerR * inner
    val cx = size.width / 2f
    val cy = size.height / 2f
    val count = lobes * 2
    val points = (0 until count).map { i ->
        val a = -PI / 2 + PI * i / lobes
        val r = if (i % 2 == 0) outerR else innerR
        (cx + r * cos(a).toFloat()) to (cy + r * sin(a).toFloat())
    }
    roundedThrough(points, corner)
}

private fun star(points: Int, inner: Float, corner: Float) = alternating(points, inner, corner)

private fun puffy(waist: Float) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val inset = (1f - waist) * 0.5f
    val cx = w / 2f
    val cy = h / 2f
    moveTo(cx, 0f)
    cubicTo(w * (1f - inset), 0f, w, h * inset, w, cy)
    cubicTo(w, h * (1f - inset), w * (1f - inset), h, cx, h)
    cubicTo(w * inset, h, 0f, h * (1f - inset), 0f, cy)
    cubicTo(0f, h * inset, w * inset, 0f, cx, 0f)
    close()
}

/** Mirrors a shape for right-to-left layouts where the geometry is directional. */
internal fun Path.mirrorIfNeeded(direction: LayoutDirection, width: Float) {
    if (direction == LayoutDirection.Rtl) {
        val matrix = androidx.compose.ui.graphics.Matrix()
        matrix.scale(-1f, 1f)
        matrix.translate(-width, 0f)
        transform(matrix)
    }
}
