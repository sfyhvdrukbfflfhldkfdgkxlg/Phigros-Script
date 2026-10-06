package io.github.phiscript.vision

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.roundToInt

/** Coordinated back, retry and resume glyphs establish a Phira pause menu. */
class PhiraPauseDetector(private val viewport: RectF, private val retryPoint: NormalizedPoint) {
    fun visible(bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled || bitmap.width < 64 || bitmap.height < 64) return false
        val width = viewport.width() * bitmap.width
        val x = retryPoint.x * bitmap.width; val y = retryPoint.y * bitmap.height
        val spacing = width * 0.085f
        for (scale in floatArrayOf(0.85f, 1f, 1.15f, 1.3f)) {
            val size = width * 0.06f * scale
            val back = sample(bitmap, x - spacing, y, size) ?: continue
            val retry = sample(bitmap, x, y, size * (4f / 3f)) ?: continue
            val resume = sample(bitmap, x + spacing, y, size) ?: continue
            if (PhiraMenuGlyphs.matches(back, PhiraMenuGlyphs.Kind.BACK) &&
                PhiraMenuGlyphs.matches(retry, PhiraMenuGlyphs.Kind.RETRY) &&
                PhiraMenuGlyphs.matches(resume, PhiraMenuGlyphs.Kind.RESUME)) return true
        }; return false
    }
    private fun sample(bitmap: Bitmap, x: Float, y: Float, size: Float): IntArray? {
        if (size < 12 || x - size / 2 < 0 || y - size / 2 < 0 ||
            x + size / 2 >= bitmap.width || y + size / 2 >= bitmap.height) return null
        val side = PhiraMenuGlyphs.SIDE
        return IntArray(side * side) { index ->
            val px = (x + (index % side / (side - 1f) - 0.5f) * size).roundToInt()
            val py = (y + (index / side / (side - 1f) - 0.5f) * size).roundToInt()
            bitmap.getPixel(px, py)
        }
    }
}
internal object PhiraMenuGlyphs {
    const val SIDE = 33
    enum class Kind { BACK, RETRY, RESUME }
    fun matches(pixels: IntArray, kind: Kind): Boolean {
        if (pixels.size != SIDE * SIDE) return false
        val brightness = pixels.map { color ->
            val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
            if (maxOf(r, g, b) - minOf(r, g, b) > 48) 0 else minOf(r, g, b)
        }
        val border = pixels.indices.filter { index ->
            val x = index % SIDE; val y = index / SIDE
            x <= 2 || x >= SIDE - 3 || y <= 2 || y >= SIDE - 3
        }.map { brightness[it] }.sorted()
        val threshold = maxOf(145, border[border.size / 2] + 65)
        if (threshold > 240) return false
        val occupied = brightness.map { it >= threshold }
        if (occupied.count { it }.toDouble() / occupied.size !in 0.10..0.65) return false
        if (kind == Kind.RETRY) {
            var center = 0; var centerBright = 0; var ring = 0; var ringBright = 0
            for (i in occupied.indices) {
                val x = i % SIDE / (SIDE - 1.0) - 0.5; val y = i / SIDE / (SIDE - 1.0) - 0.5
                val radius = kotlin.math.sqrt(x * x + y * y)
                if (radius < 0.14) { center++; if (occupied[i]) centerBright++ }
                if (radius in 0.27..0.34 && !(x > 0.0 && y < 0.0)) {
                    ring++; if (occupied[i]) ringBright++
                }
            }
            return centerBright <= center * 0.12 && ringBright >= ring * 0.58
        }
        var intersection = 0; var union = 0
        for (i in occupied.indices) {
            val expected = template(i % SIDE / (SIDE - 1.0), i / SIDE / (SIDE - 1.0), kind)
            if (expected && occupied[i]) intersection++
            if (expected || occupied[i]) union++
        }
        return union > 0 && intersection.toDouble() / union >= 0.50
    }
    internal fun template(x: Double, y: Double, kind: Kind): Boolean = when (kind) {
        Kind.RESUME -> x in 0.23..0.83 && kotlin.math.abs(y - 0.5) <= 0.36 * (0.83 - x) / 0.60
        Kind.BACK -> y in 0.085..0.915 && kotlin.math.abs(x - (0.34 + kotlin.math.abs(y - 0.5))) <= 0.085
        Kind.RETRY -> {
            val radius = kotlin.math.sqrt((x - 0.5) * (x - 0.5) + (y - 0.5) * (y - 0.5))
            radius in 0.22..0.36 && !(x > 0.5 && y < 0.5)
        }
    }
}
