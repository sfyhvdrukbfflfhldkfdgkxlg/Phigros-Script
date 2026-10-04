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
    private val anchors = chart.notes.filter { it.type != 3 }.take(20)
    private val consensus = EpochConsensus(requiredFrames = config.requiredFrames,
        maximumSpreadMs = config.maxEpochSpreadMs)
    private var lastFrame = -1L
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
        lastFrame = -1L
        dimensions = 0L
        lastMatchCount = 0
        lastDiagnostic = "等待演奏画面"
    }

    /** frameUptimeMs is capture time. Do not add Chart.offsetSeconds to the result. */
    fun observe(bitmap: Bitmap, frameUptimeMs: Long): Long? {
        require(!bitmap.isRecycled)
        if (bitmap.width <= bitmap.height) return reject("等待横屏演奏画面")
        if (anchors.size < config.minMatches) return reject("谱面开头没有足够的非长按音符")
        val age = SystemClock.uptimeMillis() - frameUptimeMs
        if (age > config.maximumFrameAgeMs || age < -100) return reject("截图时间戳过期或时钟不一致")
        if (lastFrame >= 0L && frameUptimeMs <= lastFrame) return null
        if (lastFrame >= 0L && frameUptimeMs - lastFrame < 60) return null
        lastFrame = frameUptimeMs
        val key = bitmap.width.toLong() shl 32 or bitmap.height.toLong()
        if (dimensions != 0L && dimensions != key) {
            dimensions = key
            return reject("分辨率发生变化；重新等待对齐")
        }
        dimensions = key
        val width = minOf(480, bitmap.width)
        val height = (bitmap.height.toDouble() * width / bitmap.width).roundToInt().coerceAtLeast(1)
        val small = if (width == bitmap.width) bitmap
            else Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val detected = detectBlobs(small)
            if (detected.size < config.minMatches) return reject("等待至少两个清晰的彩色音符")
            if (detected.size > 80) return reject("背景颜色干扰较多，未对齐")
            val viewport = Viewport(roi.left * width, roi.top * height,
                roi.width() * width, roi.height() * height)
            val start = anchors.first().timeSeconds - 5.0
            val finish = anchors.first().timeSeconds + 8.0
            val candidates = ArrayList<Fit>()
            var best: Fit? = null
            var time = start
            while (time <= finish) {
                val result = fit(time, detected, viewport, width, height)
                if (result != null) {
                    candidates.add(result)
                    if (best == null || result.score > best.score) best = result
                }
                time += 0.025
            }
            val coarse = best ?: return reject("未匹配谱面位置；检查视口、难度或重新开始")
            time = max(start, coarse.seconds - 0.025)
            while (time <= minOf(finish, coarse.seconds + 0.025)) {
                val result = fit(time, detected, viewport, width, height)
                if (result != null && result.score > best!!.score) best = result
                time += 0.005
            }
            val chosen = best!!
            val alternate = candidates.filter { abs(it.seconds - chosen.seconds) > 0.12 }
                .maxOfOrNull { it.score }
            if (alternate != null && chosen.score - alternate < 1.5)
                return reject("存在多个可能的谱面时间；继续等待")
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
    private data class Blob(val x: Double, val y: Double, val type: Int)
    private data class Fit(val seconds: Double, val score: Double, val matches: Int)
    private data class Edge(val note: Int, val blob: Int, val distance: Double)

    private fun fit(seconds: Double, detected: List<Blob>, viewport: Viewport,
                    width: Int, height: Int): Fit? {
        val expected = ArrayList<Blob>()
        for (note in anchors) {
            if (note.timeSeconds <= seconds + 0.04) continue
            val point = chart.position(note, seconds, viewport, visual = true) ?: continue
            if (point.x < 8 || point.x > width - 8 || point.y < 5 || point.y > height - 5) continue
            expected.add(Blob(point.x.toDouble(), point.y.toDouble(), note.type))
        }
        if (expected.size < config.minMatches) return null
        val edges = ArrayList<Edge>()
        for ((i, prediction) in expected.withIndex()) for ((j, detection) in detected.withIndex()) {
            if (prediction.type != detection.type) continue
            val dx = prediction.x - detection.x
            val dy = prediction.y - detection.y
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < 64.0) edges.add(Edge(i, j, sqrt(distanceSquared)))
        }
        edges.sortBy { it.distance }
        val usedNotes = BooleanArray(expected.size)
        val usedBlobs = BooleanArray(detected.size)
        var count = 0
        var error = 0.0
        for (edge in edges) {
            if (usedNotes[edge.note] || usedBlobs[edge.blob]) continue
            usedNotes[edge.note] = true
            usedBlobs[edge.blob] = true
            count++
            error += edge.distance
        }
        if (count < config.minMatches || count < expected.size * 0.6 || error / count > 4.0) return null
        return Fit(seconds, count * 3.0 - (expected.size - count) * 1.2 - error / 8.0, count)
    }

    private fun detectBlobs(bitmap: Bitmap): List<Blob> {
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        if (pixels.size != size) {
            pixels = IntArray(size)
            mask = ByteArray(size)
            queue = IntArray(size)
        }
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in 0 until size) {
            val color = pixels[i]
            val red = (color ushr 16) and 255
            val green = (color ushr 8) and 255
            val blue = color and 255
            val high = maxOf(red, green, blue)
            val low = minOf(red, green, blue)
            val delta = high - low
            mask[i] = 0
            if (high < 140 || delta < high * 0.28) continue
            var hue = when (high) {
                red -> 60.0 * (green - blue) / delta
                green -> 120.0 + 60.0 * (blue - red) / delta
                else -> 240.0 + 60.0 * (red - green) / delta
            }
            if (hue < 0) hue += 360.0
            mask[i] = when {
                hue < 18 || hue > 335 -> 4
                hue > 35 && hue < 78 -> 2
                hue > 170 && hue < 235 -> 1
                else -> 0
            }.toByte()
        }
        val output = ArrayList<Blob>()
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
                if (x > 0) tail = enqueue(index - 1, type, tail)
                if (x + 1 < width) tail = enqueue(index + 1, type, tail)
                if (y > 0) tail = enqueue(index - width, type, tail)
                if (y + 1 < height) tail = enqueue(index + width, type, tail)
            }
            if (count < 6) continue
            val centerX = sumX / count
            val centerY = sumY / count
            val a = sumXX / count - centerX * centerX
            val b = sumYY / count - centerY * centerY
            val c = sumXY / count - centerX * centerY
            val discriminant = sqrt((a - b) * (a - b) + 4.0 * c * c)
            val major = sqrt(max(0.0, 6.0 * (a + b + discriminant)))
            val minor = sqrt(max(1.0, 6.0 * (a + b - discriminant)))
            if (major < 10 || major > 95 || minor > 18 ||
                major / minor < 2.3 || count / (major * minor) < 0.15) continue
            output.add(Blob(centerX, centerY, type.toInt()))
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
