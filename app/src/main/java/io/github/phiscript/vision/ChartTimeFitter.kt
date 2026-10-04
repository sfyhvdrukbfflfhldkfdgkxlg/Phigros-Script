package io.github.phiscript.vision

import io.github.phiscript.engine.Chart
import io.github.phiscript.engine.Viewport
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Geometry only, so synchronization can be tested without Android bitmap mocks. */
internal class ChartTimeFitter(private val chart: Chart, private val minMatches: Int = 2) {
    data class Detection(val x: Double, val y: Double, val type: Int)
    data class Fit(val seconds: Double, val score: Double, val matches: Int, val distanceFreeScore: Double)
    data class Result(val fit: Fit?, val ambiguous: Boolean = false)
    private data class Edge(val note: Int, val blob: Int, val distance: Double)
    private val anchors = chart.notes.filter { it.type != 3 }
    val anchorCount: Int get() = anchors.size
    val firstAnchorSeconds: Double get() = anchors.firstOrNull()?.timeSeconds ?: chart.firstNoteSeconds
    private var cachedViewport: Viewport? = null
    private val coarsePredictions = HashMap<Int, List<Detection>>()
    fun clear() { cachedViewport = null; coarsePredictions.clear() }
    fun search(detected: List<Detection>, viewport: Viewport, start: Double, finish: Double): Result {
        if (detected.size < minMatches || anchors.size < minMatches || finish < start) return Result(null)
        if (cachedViewport != viewport) { clear(); cachedViewport = viewport }
        val firstStep = ceil(start / COARSE_STEP).toInt()
        val lastStep = floor(finish / COARSE_STEP).toInt()
        coarsePredictions.keys.removeAll { it < firstStep || it > lastStep }
        val candidates = ArrayList<Fit>()
        var best: Fit? = null
        for (step in firstStep..lastStep) {
            val seconds = step * COARSE_STEP
            val expected = coarsePredictions.getOrPut(step) { predictions(seconds, viewport) }
            val result = match(seconds, expected, detected, viewport) ?: continue
            candidates.add(result)
            if (best == null || result.score > best.score) best = result
        }
        val coarse = best ?: return Result(null)
        for (offset in -5..5) {
            val seconds = coarse.seconds + offset * 0.005
            if (seconds < start || seconds > finish) continue
            val result = match(seconds, predictions(seconds, viewport), detected, viewport) ?: continue
            if (result.score > best!!.score) best = result
        }
        val chosen = best!!
        val alternate = candidates.asSequence().filter { abs(it.seconds - chosen.seconds) > 0.12 }
            .maxOfOrNull { it.distanceFreeScore }
        if (alternate != null && chosen.score - alternate < 1.5) return Result(null, ambiguous = true)
        return Result(chosen)
    }
    internal fun fitAt(seconds: Double, detected: List<Detection>, viewport: Viewport): Fit? =
        match(seconds, predictions(seconds, viewport), detected, viewport)
    private fun predictions(seconds: Double, viewport: Viewport): List<Detection> {
        var low = 0; var high = anchors.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (anchors[middle].timeSeconds <= seconds + 0.04) low = middle + 1 else high = middle
        }
        val expected = ArrayList<Detection>()
        var index = low
        val horizon = seconds + 16.0
        val margin = (viewport.width / 480.0).coerceAtLeast(0.5) * 5.0
        while (index < anchors.size && anchors[index].timeSeconds <= horizon) {
            val note = anchors[index++]
            val point = chart.position(note, seconds, viewport, visual = true) ?: continue
            if (point.x < viewport.left + margin || point.x > viewport.right - margin ||
                point.y < viewport.top + margin || point.y > viewport.bottom - margin) continue
            expected.add(Detection(point.x.toDouble(), point.y.toDouble(), note.type))
        }
        return expected
    }
    private fun match(seconds: Double, expected: List<Detection>, detected: List<Detection>, viewport: Viewport): Fit? {
        if (expected.size < minMatches || expected.size > 160) return null
        val scale = (viewport.width / 480.0).coerceAtLeast(0.5)
        val radius = 8.0 * scale
        val edges = ArrayList<Edge>()
        for ((i, prediction) in expected.withIndex()) for ((j, detection) in detected.withIndex()) {
            if (prediction.type != detection.type) continue
            val dx = prediction.x - detection.x; val dy = prediction.y - detection.y
            val squared = dx * dx + dy * dy
            if (squared < radius * radius) edges.add(Edge(i, j, sqrt(squared) / scale))
        }
        edges.sortBy { it.distance }
        val usedNotes = BooleanArray(expected.size); val usedBlobs = BooleanArray(detected.size)
        var count = 0; var error = 0.0
        for (edge in edges) {
            if (usedNotes[edge.note] || usedBlobs[edge.blob]) continue
            usedNotes[edge.note] = true; usedBlobs[edge.blob] = true
            count++; error += edge.distance
        }
        if (count < minMatches || count < expected.size * 0.6 || error / count > 4.0) return null
        val distanceFreeScore = count * 3.0 - (expected.size - count) * 1.2
        return Fit(seconds, distanceFreeScore - error / 8.0, count, distanceFreeScore)
    }
    private companion object { const val COARSE_STEP = 0.025 }
}
