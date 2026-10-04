package io.github.phiscript.vision
import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

class GameplayDetector(pauseRegion: RectF = RectF(0f, 0f, 0.18f, 0.24f)) {
    data class Hit(val pauseX: Float, val pauseY: Float)
    private val region = RectF(pauseRegion).also {
        require(it.left.isFinite() && it.top.isFinite() && it.right.isFinite() && it.bottom.isFinite() &&
            it.left >= 0f && it.top >= 0f && it.right <= 1f && it.bottom <= 1f &&
            it.left < it.right && it.top < it.bottom) { "pauseRegion must be a nonempty normalized rectangle" }
    }
    private val gate = PauseEvidenceGate()
    private var sourcePixels = IntArray(0)
    private var sampledPixels = IntArray(0)
    private var lastFrameTime = Long.MIN_VALUE
    private var lastWidth = 0
    private var lastHeight = 0
    @Synchronized fun observe(bitmap: Bitmap, frameUptimeMs: Long): Hit? {
        if (frameUptimeMs < 0L || frameUptimeMs <= lastFrameTime) return null
        lastFrameTime = frameUptimeMs
        if (bitmap.isRecycled) return miss(frameUptimeMs)
        val frameWidth = bitmap.width; val frameHeight = bitmap.height
        if (frameWidth <= frameHeight || frameHeight <= 0) return miss(frameUptimeMs)
        if (frameWidth != lastWidth || frameHeight != lastHeight) {
            gate.reset(); lastWidth = frameWidth; lastHeight = frameHeight
        }
        val left = (region.left * frameWidth).toInt()
        val top = (region.top * frameHeight).toInt()
        val right = (region.right * frameWidth).roundToInt().coerceIn(left + 1, frameWidth)
        val bottom = (region.bottom * frameHeight).roundToInt().coerceIn(top + 1, frameHeight)
        val sourceWidth = right - left; val sourceHeight = bottom - top
        val sourceCount = sourceWidth * sourceHeight
        if (sourcePixels.size != sourceCount) sourcePixels = IntArray(sourceCount)
        try { bitmap.getPixels(sourcePixels, 0, sourceWidth, left, top, sourceWidth, sourceHeight) }
        catch (_: IllegalStateException) { return miss(frameUptimeMs) }
        catch (_: IllegalArgumentException) { return miss(frameUptimeMs) }
        val scale = min(1f, 540f / min(frameWidth, frameHeight))
        val sampleWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val sampleHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val pixels = if (sampleWidth == sourceWidth && sampleHeight == sourceHeight) sourcePixels
            else { downsample(sourceWidth, sourceHeight, sampleWidth, sampleHeight); sampledPixels }
        val glyph = PauseGlyphDetector.detect(pixels, sampleWidth, sampleHeight,
            (min(frameWidth, frameHeight) * scale).roundToInt())
        val stable = gate.observe(glyph, frameUptimeMs) ?: return null
        return Hit((left + stable.centerX * sourceWidth / sampleWidth) / frameWidth,
            (top + stable.centerY * sourceHeight / sampleHeight) / frameHeight)
    }
    @Synchronized fun reset() {
        gate.reset(); lastFrameTime = Long.MIN_VALUE; lastWidth = 0; lastHeight = 0
    }
    private fun miss(time: Long): Hit? { gate.observe(null, time); return null }
    private fun downsample(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int) {
        if (sampledPixels.size != width * height) sampledPixels = IntArray(width * height)
        for (y in 0 until height) {
            val y0 = y * sourceHeight / height; val y1 = (y + 1) * sourceHeight / height
            for (x in 0 until width) {
                val x0 = x * sourceWidth / width; val x1 = (x + 1) * sourceWidth / width
                var red = 0; var green = 0; var blue = 0; var count = 0
                for (sy in y0 until y1) for (sx in x0 until x1) {
                    val color = sourcePixels[sy * sourceWidth + sx]
                    red += (color ushr 16) and 255; green += (color ushr 8) and 255; blue += color and 255; count++
                }
                sampledPixels[y * width + x] = (255 shl 24) or ((red / count) shl 16) or
                    ((green / count) shl 8) or (blue / count)
            }
        }
    }
}
