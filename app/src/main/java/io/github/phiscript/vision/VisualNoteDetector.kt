package io.github.phiscript.vision

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class VisualNoteDetector(normalizedViewport: RectF = RectF(0f, 0f, 1f, 1f)) {
    private val roi = SongIdentifier.validatedRoi(normalizedViewport)
    private val detector = VisualPixelDetector()
    private var pixels = IntArray(0)
    fun detect(bitmap: Bitmap): VisualScene {
        check(!bitmap.isRecycled)
        val width = minOf(640, bitmap.width)
        val height = (bitmap.height.toDouble() * width / bitmap.width).roundToInt().coerceAtLeast(1)
        if (height >= width || height > 640) return VisualScene(width, height, emptyList(), emptyList(), false)
        val small = if (width == bitmap.width) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            if (pixels.size != width * height) pixels = IntArray(width * height)
            small.getPixels(pixels, 0, width, 0, 0, width, height)
            val left = (roi.left * width).toInt().coerceIn(0, width - 1)
            val top = (roi.top * height).toInt().coerceIn(0, height - 1)
            val right = (roi.right * width).toInt().coerceIn(left + 1, width)
            val bottom = (roi.bottom * height).toInt().coerceIn(top + 1, height)
            return detector.detect(pixels, width, height, left, top, right, bottom)
        } finally { if (small !== bitmap) small.recycle() }
    }
}

/** Default colors and visible straight lines; complex skins and intersections may fail. */
class VisualPixelDetector {
    private data class Blob(val color: Int, val count: Int, val x: Double, val y: Double,
        val dx: Double, val dy: Double, val major: Double, val minor: Double)
    private var lineMask = ByteArray(0)
    private var noteMask = ByteArray(0)
    private var queue = IntArray(0)
    fun detect(pixels: IntArray, width: Int, height: Int, left: Int = 0, top: Int = 0,
               right: Int = width, bottom: Int = height): VisualScene {
        require(width in 32..640 && height in 16..640 && pixels.size == width * height)
        require(left in 0 until right && right <= width && top in 0 until bottom && bottom <= height)
        val size = pixels.size
        if (lineMask.size != size) {
            lineMask = ByteArray(size); noteMask = ByteArray(size); queue = IntArray(size)
        } else { lineMask.fill(0); noteMask.fill(0) }
        for (y in top until bottom) for (x in left until right) {
            val i = y * width + x; val color = pixels[i]
            val red = color ushr 16 and 255; val green = color ushr 8 and 255; val blue = color and 255
            val high = maxOf(red, green, blue); val low = minOf(red, green, blue); val delta = high - low
            var hue = 0.0
            if (delta > 0) {
                hue = when (high) {
                    red -> 60.0 * (green - blue) / delta
                    green -> 120.0 + 60.0 * (blue - red) / delta
                    else -> 240.0 + 60.0 * (red - green) / delta
                }
                if (hue < 0) hue += 360.0
            }
            if (high >= 170 && (delta <= 55 || (hue in 32.0..82.0 && green >= 100))) lineMask[i] = 1
            if (high < 125 || delta < high * 0.24) continue
            noteMask[i] = when {
                hue < 20 || hue > 315 -> 4
                hue in 32.0..82.0 -> 2
                hue in 165.0..250.0 -> 1
                else -> 0
            }.toByte()
        }
        val lineBlobs = components(lineMask, width, height, 256, 32)
        val roiWidth = right - left
        val lines = lineBlobs.filter {
            it.count >= 40 && it.y > top + (bottom - top) * 0.06 &&
                it.y < bottom - (bottom - top) * 0.04 && it.major >= roiWidth * 0.28 &&
                it.minor <= max(7.0, width * 0.014) && it.major / it.minor >= 18.0 &&
                it.count / (it.major * it.minor) >= 0.30
        }.sortedWith(compareBy<Blob> { it.y }.thenBy { it.x }).map {
            VisualJudgeLine(it.x, it.y, it.dx, it.dy, it.major, it.minor)
        }
        if (lines.isEmpty() || lines.size > 12 || lineBlobs.size >= 256)
            return VisualScene(width, height, lines.take(12), emptyList(), false)
        val blobs = components(noteMask, width, height, 256)
        if (blobs.size >= 256) return VisualScene(width, height, lines, emptyList(), false)
        val notes = ArrayList<VisualNoteBlob>()
        for (blob in blobs) {
            if (blob.count < 6 || blob.major < 7.0 || blob.minor < 1.2 ||
                blob.count / (blob.major * blob.minor) < 0.13) continue
            data class Association(val index: Int, val hold: Boolean, val score: Double)
            val associations = ArrayList<Association>()
            for ((index, line) in lines.withIndex()) {
                val tangent = (blob.x - line.x) * line.dx + (blob.y - line.y) * line.dy
                if (abs(tangent) > line.length * 0.5 + 12.0) continue
                val parallel = abs(blob.dx * line.dx + blob.dy * line.dy)
                val normal = abs(blob.dx * -line.dy + blob.dy * line.dx)
                val hold = blob.color == 1 && normal > 0.86 && blob.major / blob.minor > 2.8 &&
                    blob.major >= 18.0 && blob.major <= height * 1.8 && blob.minor in 4.0..(width * 0.10)
                val ordinary = parallel > 0.82 && blob.major / blob.minor >= 2.0 &&
                    blob.major <= width * 0.18 && blob.minor <= max(18.0, width * 0.035)
                if (!hold && !ordinary) continue
                val distance = abs((blob.x - line.x) * -line.dy + (blob.y - line.y) * line.dx)
                associations.add(Association(index, hold, distance + (1.0 - if (hold) normal else parallel) * 80.0))
            }
            val ranked = associations.sortedBy { it.score }; val chosen = ranked.firstOrNull() ?: continue
            if (ranked.size > 1 && ranked[1].score - chosen.score < 8.0) continue
            val half = if (chosen.hold) blob.major * 0.5 else 0.0
            notes.add(VisualNoteBlob(if (chosen.hold) 3 else blob.color,
                blob.x, blob.y, chosen.index, blob.dx * half, blob.dy * half, 0.85))
            if (notes.size > 128) return VisualScene(width, height, lines, emptyList(), false)
        }
        return VisualScene(width, height, lines, notes)
    }
    private fun components(mask: ByteArray, width: Int, height: Int, limit: Int,
                           minimumCount: Int = 4): List<Blob> {
        val output = ArrayList<Blob>()
        for (first in mask.indices) {
            val color = mask[first]; if (color.toInt() == 0) continue
            var head = 0; var tail = 1; queue[0] = first; mask[first] = 0
            var count = 0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
            while (head < tail) {
                val index = queue[head++]; val x = index % width; val y = index / width
                count++; sx += x; sy += y
                sxx += x.toDouble() * x; syy += y.toDouble() * y; sxy += x.toDouble() * y
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until width || yy !in 0 until height) continue
                    val next = yy * width + xx
                    if (mask[next] != color) continue
                    mask[next] = 0; queue[tail++] = next
                }
            }
            if (count < minimumCount) continue
            val x = sx / count; val y = sy / count
            val a = max(0.0, sxx / count - x * x); val b = max(0.0, syy / count - y * y)
            val c = sxy / count - x * y; val disc = sqrt((a - b) * (a - b) + 4.0 * c * c)
            val angle = atan2(2.0 * c, a - b) * 0.5; var dx = cos(angle); var dy = sin(angle)
            if (dx < 0.0 || (abs(dx) < 1e-8 && dy < 0.0)) { dx = -dx; dy = -dy }
            output.add(Blob(color.toInt(), count, x, y, dx, dy,
                sqrt(max(0.0, 6.0 * (a + b + disc))), sqrt(max(0.25, 6.0 * (a + b - disc)))))
            if (output.size >= limit) return output
        }
        return output
    }
}
