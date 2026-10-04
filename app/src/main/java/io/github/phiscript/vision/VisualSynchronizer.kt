package io.github.phiscript.vision

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import io.github.phiscript.engine.Chart
import io.github.phiscript.engine.Viewport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SyncConfig(
    val minMatches: Int = 2,
    val requiredFrames: Int = 4,
    val maxEpochSpreadMs: Double = 40.0,
    val maximumFrameAgeMs: Long = 700
)

/** Conservative chart-local clock fitting; no lock means no touch. */
class VisualSynchronizer(
    private val chart: Chart,
    normalizedViewport: RectF = RectF(0f, 0f, 1f, 1f),
    private val config: SyncConfig = SyncConfig()
) {
    private val roi = SongIdentifier.validatedRoi(normalizedViewport)
    private val fitter = ChartTimeFitter(chart, config.minMatches)
    private val consensus = EpochConsensus(requiredFrames = config.requiredFrames,
        maximumSpreadMs = config.maxEpochSpreadMs)
    private var lastFrame = -1L
    private var firstFrame = -1L
    private var previousDetections: List<ChartTimeFitter.Detection>? = null
    private var dimensions = 0L
    private var pixels = IntArray(0)
    private var mask = ByteArray(0)
    private var queue = IntArray(0)
    var lastDiagnostic: String = "等待演奏画面"
        private set
    var lastMatchCount: Int = 0
        private set
    init {
        require(config.minMatches in 2..20)
        require(config.maximumFrameAgeMs > 0)
    }
    fun reset() {
        consensus.clear()
        fitter.clear()
        lastFrame = -1L
        firstFrame = -1L
        previousDetections = null
        dimensions = 0L
        lastMatchCount = 0
        lastDiagnostic = "等待演奏画面"
    }

    /** frameUptimeMs is capture time. Do not add Chart.offsetSeconds to the result. */
    fun observe(bitmap: Bitmap, frameUptimeMs: Long): Long? {
        require(!bitmap.isRecycled)
        if (bitmap.width <= bitmap.height) return reject("等待横屏演奏画面")
        if (fitter.anchorCount < config.minMatches) return reject("谱面没有足够的非长按音符")
        val age = SystemClock.uptimeMillis() - frameUptimeMs
        if (age > config.maximumFrameAgeMs || age < -100) return reject("截图时间戳过期或时钟不一致")
        if (lastFrame >= 0L && frameUptimeMs <= lastFrame) return null
        if (lastFrame >= 0L && frameUptimeMs - lastFrame < 60) return null
        val key = bitmap.width.toLong() shl 32 or bitmap.height.toLong()
        if (dimensions != 0L && dimensions != key) {
            reset()
            dimensions = key
            return reject("分辨率发生变化；重新等待对齐")
        }
        dimensions = key
        lastFrame = frameUptimeMs
        if (firstFrame < 0L) firstFrame = frameUptimeMs
        val width = minOf(480, bitmap.width)
        val height = (bitmap.height.toDouble() * width / bitmap.width).roundToInt().coerceAtLeast(1)
        val small = if (width == bitmap.width) bitmap
            else Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val viewport = Viewport(roi.left * width, roi.top * height,
                roi.width() * width, roi.height() * height)
            val detected = detectBlobs(small, viewport)
            val previous = previousDetections
            previousDetections = detected
            if (detected.size < config.minMatches) return reject("等待至少两个清晰的彩色音符")
            if (detected.size > 80) return reject("背景颜色干扰较多，未对齐")
            if (previous != null && unchanged(previous, detected))
                return reject("音符尚未移动；等待恢复或倒计时结束")
            val elapsed = (frameUptimeMs - firstFrame) / 1000.0
            val baseStart = maxOf(minOf(0.0, fitter.firstAnchorSeconds - 8.0), fitter.firstAnchorSeconds - 60.0)
            val start = baseStart + max(0.0, elapsed - 20.0)
            val finish = minOf(chart.durationSeconds - 0.04,
                max(45.0, fitter.firstAnchorSeconds + 20.0) + elapsed)
            val result = fitter.search(detected, viewport, start, finish)
            val chosen = result.fit ?: return reject(if (result.ambiguous)
                "存在多个可能的谱面时间；继续等待"
                else "未匹配谱面位置；检查视口、难度或重新开始")
            if (SystemClock.uptimeMillis() - frameUptimeMs > config.maximumFrameAgeMs)
                return reject("分析期间截图已过期；等待下一张新帧")
            lastMatchCount = chosen.matches
            val epoch = frameUptimeMs - chosen.seconds * 1000.0
            val accepted = consensus.observe(epoch, frameUptimeMs)
            lastDiagnostic = if (accepted == null)
                "匹配 " + chosen.matches + " 个音符，验证时钟 " + consensus.size + "/" + config.requiredFrames
            else "已对齐，匹配 " + chosen.matches + " 个音符"
            return accepted
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }

    private fun reject(reason: String): Long? {
        consensus.clear()
        lastMatchCount = 0
        lastDiagnostic = reason
        return null
    }
    private fun unchanged(previous: List<ChartTimeFitter.Detection>, current: List<ChartTimeFitter.Detection>): Boolean {
        if (previous.size != current.size) return false
        return previous.indices.all { i ->
            previous[i].type == current[i].type &&
                abs(previous[i].x - current[i].x) < 0.1 && abs(previous[i].y - current[i].y) < 0.1
        }
    }
    private fun detectBlobs(bitmap: Bitmap, viewport: Viewport): List<ChartTimeFitter.Detection> {
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        if (pixels.size != size) {
            pixels = IntArray(size)
            mask = ByteArray(size)
            queue = IntArray(size)
        }
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        mask.fill(0)
        val left = viewport.left.toInt().coerceIn(0, width - 1)
        val top = viewport.top.toInt().coerceIn(0, height - 1)
        val right = viewport.right.toInt().coerceIn(left + 1, width)
        val bottom = viewport.bottom.toInt().coerceIn(top + 1, height)
        for (y in top until bottom) for (x in left until right) {
            val i = y * width + x
            val color = pixels[i]
            val red = (color ushr 16) and 255
            val green = (color ushr 8) and 255
            val blue = color and 255
            val high = maxOf(red, green, blue)
            val low = minOf(red, green, blue)
            val delta = high - low
            mask[i] = 0
            if (high < 125 || delta < high * 0.22) continue
            var hue = when (high) {
                red -> 60.0 * (green - blue) / delta
                green -> 120.0 + 60.0 * (blue - red) / delta
                else -> 240.0 + 60.0 * (red - green) / delta
            }
            if (hue < 0) hue += 360.0
            mask[i] = when {
                hue < 20 || hue > 315 -> 4
                hue > 32 && hue < 82 -> 2
                hue > 165 && hue < 240 -> 1
                else -> 0
            }.toByte()
        }
        val scale = (viewport.width / 480.0).coerceAtLeast(0.5)
        val output = ArrayList<ChartTimeFitter.Detection>()
        for (i in 0 until size) {
            val type = mask[i]
            if (type.toInt() == 0) continue
            var head = 0
            var tail = 1
            queue[0] = i
            mask[i] = 0
            var count = 0
            var sumX = 0.0
            var sumY = 0.0
            var sumXX = 0.0
            var sumYY = 0.0
            var sumXY = 0.0
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                count++
                sumX += x
                sumY += y
                sumXX += x.toDouble() * x
                sumYY += y.toDouble() * y
                sumXY += x.toDouble() * y
                for (dy in -1..1) for (dx in -1..1) {
                    if ((dx == 0 && dy == 0) || x + dx !in 0 until width || y + dy !in 0 until height) continue
                    tail = enqueue(index + dy * width + dx, type, tail)
                }
            }
            if (count < 4) continue
            val centerX = sumX / count
            val centerY = sumY / count
            val a = sumXX / count - centerX * centerX
            val b = sumYY / count - centerY * centerY
            val c = sumXY / count - centerX * centerY
            val discriminant = sqrt((a - b) * (a - b) + 4.0 * c * c)
            val major = sqrt(max(0.0, 6.0 * (a + b + discriminant)))
            val minor = sqrt(max(1.0, 6.0 * (a + b - discriminant)))
            if (major < 8 * scale || major > 95 * scale || minor > 18 * scale ||
                major / minor < 2.3 || count / (major * minor) < 0.15) continue
            output.add(ChartTimeFitter.Detection(centerX, centerY, type.toInt()))
            if (output.size > 80) return output
        }
        return output
    }
    private fun enqueue(index: Int, type: Byte, tail: Int): Int {
        if (mask[index] != type) return tail
        mask[index] = 0
        queue[tail] = index
        return tail + 1
    }
}
