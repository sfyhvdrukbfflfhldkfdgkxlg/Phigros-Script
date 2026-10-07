package io.github.phiscript.input
import kotlin.math.hypot
import kotlin.math.min

/** Phigros accepts any flick direction; clipping must not turn it into a tap. */
internal object FlickGeometry {
    data class Pixel(val x: Float, val y: Float)
    fun endpoint(x: Float, y: Float, dx: Float, dy: Float, width: Int, height: Int): Pixel {
        require(width > 1 && height > 1)
        require(listOf(x, y, dx, dy).all { it.isFinite() })
        val maxX = (width - 1).toFloat(); val maxY = (height - 1).toFloat()
        val from = Pixel(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
        fun bounded(vx: Float, vy: Float) = Pixel((from.x + vx).coerceIn(0f, maxX), (from.y + vy).coerceIn(0f, maxY))
        fun length(p: Pixel) = hypot((p.x - from.x).toDouble(), (p.y - from.y).toDouble())
        val minimum = min(width, height) * 0.035
        val norm = hypot(dx.toDouble(), dy.toDouble())
        val factor = if (norm > 0.0) maxOf(1.0, minimum / norm) else 1.0
        val vx = (dx * factor).toFloat(); val vy = (dy * factor).toFloat()
        val normal = bounded(vx, vy)
        if (length(normal) >= minimum * 0.9) return normal
        val reversed = bounded(-vx, -vy)
        if (length(reversed) >= minimum * 0.9) return reversed
        return listOf(bounded(minimum.toFloat(), 0f), bounded(-minimum.toFloat(), 0f),
            bounded(0f, minimum.toFloat()), bounded(0f, -minimum.toFloat())).maxBy { length(it) }
    }
}
