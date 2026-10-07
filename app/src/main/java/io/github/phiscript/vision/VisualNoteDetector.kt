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
        val width = minOf(960, bitmap.width)
        val height = (bitmap.height.toDouble() * width / bitmap.width).roundToInt().coerceAtLeast(1)
        if (height >= width || height > 960 || width < 32 || height < 16)
            return VisualScene(width, height, emptyList(), emptyList(), false)
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

/** Local color growth uses original seeds only; motion evidence is required by the tracker. */
class VisualPixelDetector {
    private data class Blob(val color: Int, val count: Int, val strong: Int,
        val x: Double, val y: Double, val dx: Double, val dy: Double,
        val major: Double, val minor: Double, val points: IntArray)
    private data class Projection(val alongMin: Double, val alongMax: Double,
        val acrossMin: Double, val acrossMax: Double) {
        val alongCenter get() = (alongMin + alongMax) * 0.5
        val acrossCenter get() = (acrossMin + acrossMax) * 0.5
        val alongWidth get() = alongMax - alongMin + 1.0
        val acrossHeight get() = acrossMax - acrossMin + 1.0
    }
    private data class Association(val index: Int, val hold: Boolean, val score: Double, val projection: Projection)
    private var lineMask = ByteArray(0)
    private var noteMask = ByteArray(0)
    private var strongMask = ByteArray(0)
    private var weakMask = ByteArray(0)
    private var neighbors = IntArray(0)
    private var directions = IntArray(0)
    private var queue = IntArray(0)

    fun detect(pixels: IntArray, width: Int, height: Int,
        left: Int = 0, top: Int = 0, right: Int = width, bottom: Int = height): VisualScene {
        require(width in 32..960 && height in 16..960 && pixels.size == width * height)
        require(left in 0 until right && right <= width && top in 0 until bottom && bottom <= height)
        prepare(pixels.size)
        for (y in top until bottom) for (x in left until right) {
            val index = y * width + x
            val color = pixels[index]
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
            if (high >= 170 && (delta <= 55 || (hue in 32.0..88.0 && green >= 100))) lineMask[index] = 1
            val kind = when {
                hue < 28.0 || hue > 300.0 -> 4
                hue in 28.0..92.0 -> 2
                hue in 150.0..270.0 -> 1
                else -> 0
            }
            if (kind == 0 || high < 100 || delta < max(8.0, high * 0.04)) continue
            weakMask[index] = kind.toByte()
            if (high >= 112 && delta >= max(18.0, high * 0.10)) strongMask[index] = kind.toByte()
        }
        val rawLineBlobs = components(lineMask, width, 256, 32)
        val roiWidth = right - left
        if (rawLineBlobs.size >= 256) return VisualScene(width, height, emptyList(), emptyList(), false)
        val lineBlobs = mergeLineFragments(rawLineBlobs, roiWidth, width)
        val lines = lineBlobs.filter {
            it.count >= 40 && it.y > top + (bottom - top) * 0.06 &&
                it.y < bottom - (bottom - top) * 0.04 && it.major >= roiWidth * 0.28 &&
                it.minor <= max(7.0, width * 0.014) && it.major / it.minor >= 18.0 &&
                it.count / (it.major * it.minor) >= 0.30
        }.sortedWith(compareBy<Blob> { it.y }.thenBy { it.x }).map {
            VisualJudgeLine(it.x, it.y, it.dx, it.dy, it.major, it.minor)
        }
        if (lines.isEmpty() || lines.size > 12) return VisualScene(width, height, lines.take(12), emptyList(), false)
        growColors(pixels, width, left, top, right, bottom)
        val rawBlobs = components(noteMask, width, 512, 3)
        if (rawBlobs.size >= 512) return VisualScene(width, height, lines, emptyList(), false)
        val blobs = mergeHoldRails(rawBlobs, lines, width)
        val notes = ArrayList<VisualNoteBlob>()
        for (blob in blobs) {
            if (blob.count < 4 || blob.strong < 3) continue
            val associations = ArrayList<Association>()
            for ((index, line) in lines.withIndex()) {
                val p = project(blob, line, width) ?: continue
                val lineAlong = line.x * line.dx + line.y * line.dy
                if (abs(p.alongCenter - lineAlong) > line.length * 0.5 + 12.0) continue
                val lineAcross = -line.dy * line.x + line.dx * line.y
                val along = p.alongWidth; val across = p.acrossHeight
                val hold = blob.color == 1 && along in 3.0..(width * 0.12) &&
                    across >= max(18.0, along * 1.6) && across <= height * 1.8 &&
                    blob.count / (along * across) >= 0.035
                val ordinary = along in 6.0..(width * 0.20) && across >= 1.0 &&
                    (if (blob.color == 4) {
                        across <= max(24.0, width * 0.06) && along / across >= 0.40
                    } else {
                        across <= max(18.0, width * 0.04) && along / across >= 1.35
                    })
                if (!hold && !ordinary) continue
                if (!hold && blob.count / (along * across) < 0.08) continue
                val distance = if (hold) minOf(abs(p.acrossMin - lineAcross), abs(p.acrossMax - lineAcross))
                    else abs(p.acrossCenter - lineAcross)
                val parallel = abs(blob.dx * line.dx + blob.dy * line.dy)
                val normal = abs(blob.dx * -line.dy + blob.dy * line.dx)
                val cost = when { hold -> (1.0 - normal) * 20.0; blob.color == 4 -> 0.0
                    else -> (1.0 - parallel) * 20.0 }
                associations.add(Association(index, hold, distance + cost, p))
            }
            val ranked = associations.sortedBy { it.score }
            val chosen = ranked.firstOrNull() ?: continue
            if (ranked.size > 1 && ranked[1].score - chosen.score < 8.0) continue
            val line = lines[chosen.index]
            // Growth joins pieces; only original colored pixels define contact geometry.
            val p = project(blob, line, width, true) ?: chosen.projection
            val x = line.dx * p.alongCenter - line.dy * p.acrossCenter
            val y = line.dy * p.alongCenter + line.dx * p.acrossCenter
            val half = if (chosen.hold) (p.acrossMax - p.acrossMin) * 0.5 else 0.0
            val confidence = (0.80 + 0.15 * blob.strong / blob.count.toDouble()).coerceAtMost(0.95)
            notes.add(VisualNoteBlob(if (chosen.hold) 3 else blob.color, x, y, chosen.index,
                -line.dy * half, line.dx * half, confidence))
            if (notes.size > 128) return VisualScene(width, height, lines, emptyList(), false)
        }
        return VisualScene(width, height, lines, notes)
    }
    private fun prepare(size: Int) {
        if (lineMask.size != size) {
            lineMask = ByteArray(size); noteMask = ByteArray(size)
            strongMask = ByteArray(size); weakMask = ByteArray(size)
            neighbors = IntArray(size); directions = IntArray(size); queue = IntArray(size)
        } else {
            lineMask.fill(0); noteMask.fill(0); strongMask.fill(0); weakMask.fill(0)
            neighbors.fill(0); directions.fill(0)
        }
    }
    private fun mergeLineFragments(blobs: List<Blob>, roiWidth: Int, width: Int): List<Blob> {
        val fragments = blobs.filter {
            it.count >= 32 && it.major >= roiWidth * 0.06 &&
                it.minor <= max(7.0, width * 0.014) && it.major / it.minor >= 8.0
        }.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            search@ for (a in fragments.indices) for (b in a + 1 until fragments.size) {
                val first = fragments[a]; val second = fragments[b]
                if (first.dx * second.dx + first.dy * second.dy < 0.995) continue
                val x = second.x - first.x; val y = second.y - first.y
                if (abs(-x * first.dy + y * first.dx) > max(3.0, (first.minor + second.minor) * 0.4)) continue
                val gap = abs(x * first.dx + y * first.dy) - (first.major + second.major) * 0.5
                if (gap < -minOf(first.major, second.major) * 0.15 || gap > roiWidth * 0.20) continue
                val merged = measure(first.color, first.points + second.points, width)
                if (merged.minor > max(7.0, width * 0.014)) continue
                fragments[a] = merged; fragments.removeAt(b); changed = true
                break@search
            }
        }
        return fragments
    }
    private fun growColors(pixels: IntArray, width: Int, left: Int, top: Int, right: Int, bottom: Int) {
        for (y in top until bottom) for (x in left until right) {
            val kind = strongMask[y * width + x].toInt()
            if (kind == 0) continue
            val channel = when (kind) { 1 -> 0; 2 -> 1; else -> 2 }
            val vote = 1 shl (channel * 5)
            val shift = channel * 4
            for (dy in -2..2) for (dx in -2..2) {
                val xx = x + dx; val yy = y + dy
                if (xx !in left until right || yy !in top until bottom) continue
                val index = yy * width + xx
                neighbors[index] += vote
                var bits = 0
                if (dx < 0) bits = bits or 1
                if (dx > 0) bits = bits or 2
                if (dy < 0) bits = bits or 4
                if (dy > 0) bits = bits or 8
                directions[index] = directions[index] or (bits shl shift)
            }
        }
        for (y in top until bottom) for (x in left until right) {
            val index = y * width + x
            val strong = strongMask[index]
            if (strong.toInt() != 0) { noteMask[index] = strong; continue }
            val votes = neighbors[index]
            if (votes == 0) continue
            val blueVotes = votes and 31; val yellowVotes = votes ushr 5 and 31; val redVotes = votes ushr 10 and 31
            val weak = weakMask[index].toInt()
            val weakVotes = when (weak) { 1 -> blueVotes; 2 -> yellowVotes; 4 -> redVotes; else -> 0 }
            if (weak != 0 && weakVotes >= 1) { noteMask[index] = weak.toByte(); continue }
            val present = (if (blueVotes > 0) 1 else 0) + (if (yellowVotes > 0) 1 else 0) +
                (if (redVotes > 0) 1 else 0)
            if (present != 1 || weak != 0) continue
            val channel = when { blueVotes > 0 -> 0; yellowVotes > 0 -> 1; else -> 2 }
            val count = votes ushr (channel * 5) and 31
            val color = pixels[index]
            val red = color ushr 16 and 255; val green = color ushr 8 and 255; val blue = color and 255
            val high = maxOf(red, green, blue); val delta = high - minOf(red, green, blue)
            val brightCore = high >= 155 && delta <= 55 && count >= 2
            val bits = directions[index] ushr (channel * 4) and 15
            val bridge = count >= 4 && ((bits and 3) == 3 || (bits and 12) == 12)
            if (brightCore || bridge) noteMask[index] = (when (channel) { 0 -> 1; 1 -> 2; else -> 4 }).toByte()
        }
    }
    private fun project(blob: Blob, line: VisualJudgeLine, width: Int, strongOnly: Boolean = false): Projection? {
        var amin = Double.POSITIVE_INFINITY; var amax = Double.NEGATIVE_INFINITY
        var nmin = Double.POSITIVE_INFINITY; var nmax = Double.NEGATIVE_INFINITY
        for (index in blob.points) {
            if (strongOnly && strongMask[index].toInt() != blob.color) continue
            val x = (index % width).toDouble(); val y = (index / width).toDouble()
            val a = x * line.dx + y * line.dy; val n = -x * line.dy + y * line.dx
            amin = minOf(amin, a); amax = maxOf(amax, a); nmin = minOf(nmin, n); nmax = maxOf(nmax, n)
        }
        return if (amin.isFinite()) Projection(amin, amax, nmin, nmax) else null
    }
    private fun mergeHoldRails(blobs: List<Blob>, lines: List<VisualJudgeLine>, width: Int): List<Blob> {
        data class Rail(val index: Int, val projection: Projection)
        data class Pairing(val first: Int, val second: Int, val gap: Double)
        val pairings = ArrayList<Pairing>()
        for (line in lines) {
            val rails = blobs.mapIndexedNotNull { index, blob ->
                if (blob.color != 1 || blob.strong < 3) return@mapIndexedNotNull null
                val p = project(blob, line, width, true) ?: return@mapIndexedNotNull null
                if (p.alongWidth > max(3.0, width * 0.0065) || p.acrossHeight < 18.0 ||
                    p.acrossHeight / p.alongWidth < 4.0) null else Rail(index, p)
            }
            if (rails.size > 64) continue
            for (a in rails.indices) for (b in a + 1 until rails.size) {
                val first = rails[a]; val second = rails[b]; val p = first.projection; val q = second.projection
                val overlap = minOf(p.acrossMax, q.acrossMax) - maxOf(p.acrossMin, q.acrossMin)
                if (overlap < minOf(p.acrossHeight, q.acrossHeight) * 0.55) continue
                val gap = abs(p.alongCenter - q.alongCenter)
                val combined = maxOf(p.alongMax, q.alongMax) - minOf(p.alongMin, q.alongMin) + 1.0
                if (gap < 3.0 || combined > width * 0.09 || minOf(p.acrossHeight, q.acrossHeight) < combined * 1.4) continue
                pairings.add(Pairing(first.index, second.index, gap))
            }
        }
        val used = HashSet<Int>(); val merged = ArrayList<Blob>()
        for (pair in pairings.sortedBy { it.gap }) {
            if (pair.first in used || pair.second in used) continue
            used.add(pair.first); used.add(pair.second)
            val a = blobs[pair.first]; val b = blobs[pair.second]
            merged.add(measure(a.color, a.points + b.points, width))
        }
        for ((index, blob) in blobs.withIndex()) if (index !in used) merged.add(blob)
        return merged
    }
    private fun measure(color: Int, points: IntArray, width: Int): Blob {
        var strong = 0; var sx = 0.0; var sy = 0.0
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (index in points) {
            val x = index % width; val y = index / width
            if (strongMask[index].toInt() == color) strong++
            sx += x; sy += y; sxx += x.toDouble() * x; syy += y.toDouble() * y; sxy += x.toDouble() * y
        }
        val count = points.size; val x = sx / count; val y = sy / count
        val a = max(0.0, sxx / count - x * x); val b = max(0.0, syy / count - y * y); val c = sxy / count - x * y
        val disc = sqrt((a - b) * (a - b) + 4.0 * c * c); val angle = atan2(2.0 * c, a - b) * 0.5
        var dx = cos(angle); var dy = sin(angle)
        if (dx < 0.0 || (abs(dx) < 1e-8 && dy < 0.0)) { dx = -dx; dy = -dy }
        return Blob(color, count, strong, x, y, dx, dy,
            sqrt(max(0.0, 6.0 * (a + b + disc))), sqrt(max(0.25, 6.0 * (a + b - disc))), points)
    }
    private fun components(mask: ByteArray, width: Int, limit: Int, minimumCount: Int): List<Blob> {
        val height = mask.size / width; val output = ArrayList<Blob>()
        for (first in mask.indices) {
            val color = mask[first]
            if (color.toInt() == 0) continue
            var head = 0; var tail = 1; queue[0] = first; mask[first] = 0
            while (head < tail) {
                val index = queue[head++]; val x = index % width; val y = index / width
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until width || yy !in 0 until height) continue
                    val next = yy * width + xx
                    if (mask[next] != color) continue
                    mask[next] = 0; queue[tail++] = next
                }
            }
            if (tail < minimumCount) continue
            output.add(measure(color.toInt(), queue.copyOf(tail), width))
            if (output.size >= limit) return output
        }
        return output
    }
}
