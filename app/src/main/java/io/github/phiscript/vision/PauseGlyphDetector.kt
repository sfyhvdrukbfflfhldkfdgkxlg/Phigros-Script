package io.github.phiscript.vision
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object PauseGlyphDetector {
    data class Glyph(val centerX: Float, val centerY: Float, val width: Float, val height: Float, val score: Float = 1f)
    private data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val area: Int = 0) {
        val width get() = x1 - x0
        val height get() = y1 - y0
        val fill get() = area.toFloat() / (width * height)
    }
    private data class Stats(val mean: Float, val brightFraction: Float, val maxRowMean: Float)
    /** Coordinates are relative to the sampled ROI; the reference size belongs to the whole frame. */
    fun detect(pixels: IntArray, width: Int, height: Int, referenceShortSide: Int): Glyph? {
        if (width < 8 || height < 8 || referenceShortSide <= 0 || pixels.size < width * height) return null
        val count = width * height
        val luma = IntArray(count); val neutral = BooleanArray(count)
        for (i in 0 until count) {
            val color = pixels[i]
            val red = (color ushr 16) and 255; val green = (color ushr 8) and 255; val blue = color and 255
            luma[i] = (77 * red + 150 * green + 29 * blue) ushr 8
            neutral[i] = (color ushr 24) >= 192 && max(red, max(green, blue)) - min(red, min(green, blue)) <= 68
        }
        val minHeight = max(8, (referenceShortSide * 0.018f).roundToInt())
        val maxHeight = max(minHeight, (referenceShortSide * 0.095f).roundToInt())
        val maxWidth = max(3, (referenceShortSide * 0.030f).roundToInt())
        var best: Glyph? = null
        for (threshold in intArrayOf(152, 176, 200, 224)) {
            val mask = BooleanArray(count) { neutral[it] && luma[it] >= threshold }
            val bars = components(mask, width, height).filter {
                it.x0 > 0 && it.y0 > 0 && it.x1 < width && it.y1 < height &&
                    it.width in 2..maxWidth && it.height in minHeight..maxHeight &&
                    it.height.toFloat() / it.width in 2.1f..7f && it.fill >= 0.84f
            }
            if (bars.size !in 2..64) continue
            for (i in 0 until bars.lastIndex) for (j in i + 1 until bars.size) {
                val first = bars[i]; val second = bars[j]
                val left = if (first.x0 <= second.x0) first else second
                val right = if (first.x0 <= second.x0) second else first
                val candidate = evaluate(left, right, luma, width, height) ?: continue
                val previous = best
                if (previous == null) best = candidate
                else {
                    if (!samePlace(previous, candidate)) return null
                    if (candidate.score > previous.score) best = candidate
                }
            }
        }
        return best
    }
    private fun components(mask: BooleanArray, width: Int, height: Int): List<Box> {
        val queue = IntArray(mask.size); val result = ArrayList<Box>()
        for (start in mask.indices) {
            if (!mask[start]) continue
            var head = 0; var tail = 1
            queue[0] = start; mask[start] = false
            var minX = start % width; var maxX = minX
            var minY = start / width; var maxY = minY; var area = 0
            while (head < tail) {
                val index = queue[head++]; val x = index % width; val y = index / width
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y); area++
                for (ny in max(0, y - 1)..min(height - 1, y + 1)) for (nx in max(0, x - 1)..min(width - 1, x + 1)) {
                    val neighbor = ny * width + nx
                    if (mask[neighbor]) { mask[neighbor] = false; queue[tail++] = neighbor }
                }
            }
            result += Box(minX, minY, maxX + 1, maxY + 1, area)
        }
        return result
    }
    private fun evaluate(left: Box, right: Box, luma: IntArray, imageWidth: Int, imageHeight: Int): Glyph? {
        val smallerHeight = min(left.height, right.height).toFloat()
        val smallerWidth = min(left.width, right.width).toFloat()
        val averageWidth = (left.width + right.width) * 0.5f
        if (abs(left.height - right.height) > max(2f, smallerHeight * 0.15f)) return null
        if (abs(left.width - right.width) > max(1.5f, smallerWidth * 0.35f)) return null
        val alignmentTolerance = max(2f, smallerHeight * 0.09f)
        if (abs(left.y0 - right.y0) > alignmentTolerance || abs(left.y1 - right.y1) > alignmentTolerance) return null
        val gap = right.x0 - left.x1
        if (gap < max(2f, averageWidth * 0.45f) || gap > averageWidth * 2.6f) return null
        val x0 = left.x0; val x1 = right.x1; val y0 = min(left.y0, right.y0); val y1 = max(left.y1, right.y1)
        val glyphWidth = x1 - x0; val glyphHeight = y1 - y0
        if (glyphWidth.toFloat() / glyphHeight !in 0.4f..1.45f) return null
        fun core(box: Box): Box {
            val dx = box.width / 5; val dy = box.height / 8
            return Box(box.x0 + dx, box.y0 + dy, box.x1 - dx, box.y1 - dy)
        }
        val leftMean = sample(luma, imageWidth, core(left), 256f).mean
        val rightMean = sample(luma, imageWidth, core(right), 256f).mean
        val barMean = (leftMean + rightMean) * 0.5f
        if (barMean < 170f || abs(leftMean - rightMean) > 30f) return null
        val guard = max(1, (averageWidth * 0.18f).roundToInt())
        val gapBox = Box(left.x1 + guard, max(left.y0, right.y0) + 1, right.x0 - guard, min(left.y1, right.y1) - 1)
        if (gapBox.width <= 0 || gapBox.height <= 0) return null
        val brightCutoff = barMean - 55f
        val gapStats = sample(luma, imageWidth, gapBox, brightCutoff)
        if (barMean - gapStats.mean < 60f || gapStats.brightFraction > 0.07f || gapStats.maxRowMean > brightCutoff) return null
        val sideMargin = max(3, (glyphHeight * 0.35f).roundToInt())
        val verticalMargin = max(2, (glyphHeight * 0.18f).roundToInt())
        val backgrounds = listOf(Box(x0 - guard - sideMargin, y0, x0 - guard, y1),
            Box(x1 + guard, y0, x1 + guard + sideMargin, y1),
            Box(x0, y0 - guard - verticalMargin, x1, y0 - guard),
            Box(x0, y1 + guard, x1, y1 + guard + verticalMargin))
        if (backgrounds.any { it.x0 < 0 || it.y0 < 0 || it.x1 > imageWidth || it.y1 > imageHeight }) return null
        var highestBackgroundMean = gapStats.mean
        for (box in backgrounds) {
            val stats = sample(luma, imageWidth, box, brightCutoff)
            if (barMean - stats.mean < 45f || stats.brightFraction > 0.18f) return null
            highestBackgroundMean = max(highestBackgroundMean, stats.mean)
        }
        val contrast = (barMean - highestBackgroundMean) / 255f
        val rectangularity = (left.fill + right.fill) * 0.5f
        val similarity = 1f - abs(left.height - right.height).toFloat() / max(left.height, right.height)
        return Glyph((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, glyphWidth.toFloat(), glyphHeight.toFloat(),
            contrast * 0.6f + rectangularity * 0.25f + similarity * 0.15f)
    }
    private fun sample(luma: IntArray, stride: Int, box: Box, brightCutoff: Float): Stats {
        var sum = 0L; var bright = 0; var maxRowMean = 0f
        for (y in box.y0 until box.y1) {
            var rowSum = 0
            for (x in box.x0 until box.x1) {
                val value = luma[y * stride + x]; rowSum += value
                if (value >= brightCutoff) bright++
            }
            sum += rowSum; maxRowMean = max(maxRowMean, rowSum.toFloat() / box.width)
        }
        val count = box.width * box.height
        return Stats(sum.toFloat() / count, bright.toFloat() / count, maxRowMean)
    }
    private fun samePlace(a: Glyph, b: Glyph): Boolean {
        val tolerance = max(2.5f, min(a.height, b.height) * 0.12f)
        return abs(a.centerX - b.centerX) <= tolerance && abs(a.centerY - b.centerY) <= tolerance &&
            abs(a.width - b.width) <= max(3f, min(a.width, b.width) * 0.30f) &&
            abs(a.height - b.height) <= max(3f, min(a.height, b.height) * 0.25f)
    }
}
internal class PauseEvidenceGate {
    private var anchor: PauseGlyphDetector.Glyph? = null
    private var firstSeenMs = 0L
    private var lastCountedMs = 0L
    private var lastFreshMs = Long.MIN_VALUE
    private var count = 0
    fun observe(glyph: PauseGlyphDetector.Glyph?, frameUptimeMs: Long): PauseGlyphDetector.Glyph? {
        if (frameUptimeMs < 0L || frameUptimeMs <= lastFreshMs) return null
        if (lastFreshMs != Long.MIN_VALUE && frameUptimeMs - lastFreshMs > 650L) clearEvidence()
        lastFreshMs = frameUptimeMs
        if (glyph == null) { clearEvidence(); return null }
        val previous = anchor
        if (previous == null || !consistent(previous, glyph)) {
            anchor = glyph; firstSeenMs = frameUptimeMs; lastCountedMs = frameUptimeMs; count = 1
            return null
        }
        if (frameUptimeMs - lastCountedMs >= 60L) { count = min(3, count + 1); lastCountedMs = frameUptimeMs }
        return glyph.takeIf { count >= 3 && frameUptimeMs - firstSeenMs >= 180L }
    }
    fun reset() { clearEvidence(); lastFreshMs = Long.MIN_VALUE }
    private fun clearEvidence() { anchor = null; count = 0; firstSeenMs = 0L; lastCountedMs = 0L }
    private fun consistent(a: PauseGlyphDetector.Glyph, b: PauseGlyphDetector.Glyph): Boolean {
        val tolerance = max(2f, min(a.height, b.height) * 0.12f)
        return abs(a.centerX - b.centerX) <= tolerance && abs(a.centerY - b.centerY) <= tolerance &&
            abs(a.width - b.width) <= max(2f, min(a.width, b.width) * 0.22f) &&
            abs(a.height - b.height) <= max(2f, min(a.height, b.height) * 0.18f)
    }
}
