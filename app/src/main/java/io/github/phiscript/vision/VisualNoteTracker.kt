package io.github.phiscript.vision

import io.github.phiscript.input.VisualTouch
import io.github.phiscript.input.VisualTouchKind
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class VisualJudgeLine(val x: Double, val y: Double, val dx: Double, val dy: Double,
    val length: Double, val thickness: Double)
data class VisualNoteBlob(val kind: Int, val x: Double, val y: Double, val lineIndex: Int,
    val extentX: Double = 0.0, val extentY: Double = 0.0, val confidence: Double = 1.0)
data class VisualScene(val width: Int, val height: Int, val lines: List<VisualJudgeLine>,
    val notes: List<VisualNoteBlob>, val reliable: Boolean = true)

/** Tracks approaching notes using images only. No chart or song clock is consulted. */
class VisualNoteTracker(private val maximumFrameAgeMs: Long = 180,
                        private val maximumFrameGapMs: Long = 180) {
    private data class Track(
        val id: Long, val kind: Int, val side: Double, val startedAway: Boolean,
        var note: VisualNoteBlob, var line: VisualJudgeLine, var seenAt: Long,
        var headDistance: Double, var tailDistance: Double, var seen: Int = 1,
        var approaching: Int = 0, var velocity: Double = 0.0,
        var vx: Double = 0.0, var vy: Double = 0.0,
        var lineVx: Double = 0.0, var lineVy: Double = 0.0,
        var fired: Boolean = false, var activeUntil: Long = 0, var firedAt: Long = 0,
        var finished: Boolean = false, var previousDistance: Double = 0.0,
        var previousAt: Long = 0
    )
    private data class Endpoint(val x: Double, val y: Double, val near: Double, val far: Double)
    private val tracks = ArrayList<Track>()
    private var nextId = 1L
    private var lastFrame = -1L
    private var width = 0
    private var height = 0
    var lastDiagnostic = "等待清晰判定线与移动音符"
        private set
    init { require(maximumFrameAgeMs in 30..500 && maximumFrameGapMs in 30..500) }

    /** IDs remain unique after a reset, because the touch helper deduplicates them. */
    fun reset() {
        tracks.clear(); lastFrame = -1L; width = 0; height = 0
        lastDiagnostic = "等待清晰判定线与移动音符"
    }

    fun observe(scene: VisualScene, capturedAtMs: Long, nowMs: Long,
                predictionLeadMs: Int = 0): List<VisualTouch> {
        if (capturedAtMs < 0 || nowMs < capturedAtMs ||
            nowMs - capturedAtMs > maximumFrameAgeMs) return reject("视觉截图过期，释放触点")
        if (capturedAtMs <= lastFrame) return emptyList()
        if (!valid(scene)) return reject("判定线不可见或画面复杂，释放触点")
        if (width != 0 && (width != scene.width || height != scene.height)) reset()
        if (lastFrame >= 0 && capturedAtMs - lastFrame > maximumFrameGapMs) tracks.clear()
        width = scene.width; height = scene.height; lastFrame = capturedAtMs
        val matched = HashSet<Long>()
        val candidates = scene.notes.filter { it.lineIndex in scene.lines.indices && validNote(it) }
            .sortedByDescending { it.confidence }
        for (note in candidates) {
            val line = scene.lines[note.lineIndex]
            val chosen = tracks.asSequence().filter {
                it.id !in matched && it.kind == note.kind && !it.finished &&
                    capturedAtMs - it.seenAt <= maximumFrameGapMs && compatible(it.line, line)
            }.mapNotNull { track ->
                val dt = (capturedAtMs - track.seenAt) / 1000.0
                if (dt <= 0.0) return@mapNotNull null
                val error = hypot(note.x - track.note.x - track.vx * dt,
                    note.y - track.note.y - track.vy * dt)
                val allowed = max(24.0, height * (if (track.seen == 1) 0.40 else 0.16) +
                    hypot(track.vx, track.vy) * dt * 0.4)
                val endpoint = endpoints(note, oriented(line, track.line), track.side)
                if (error > allowed || (track.fired && track.kind != 3 &&
                    endpoint.near > track.headDistance + max(12.0, height * 0.03))) null
                else track to error
            }.minByOrNull { it.second }?.first
            if (chosen == null) {
                if (tracks.size >= 128) continue
                val side = if (signedDistance(note.x, note.y, line) >= 0.0) 1.0 else -1.0
                val endpoint = endpoints(note, line, side)
                tracks.add(Track(nextId++, note.kind, side,
                    endpoint.near > max(6.0, line.thickness * 2.0),
                    note, line, capturedAtMs, endpoint.near, endpoint.far))
            } else { matched.add(chosen.id); update(chosen, note, line, capturedAtMs) }
        }
        val output = ArrayList<VisualTouch>()
        val iterator = tracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            val missing = capturedAtMs - track.seenAt
            if (missing > maximumFrameGapMs || track.finished) { iterator.remove(); continue }
            val observed = missing == 0L
            if (nowMs - track.seenAt > maximumFrameAgeMs) continue
            val ageSeconds = (nowMs - track.seenAt) / 1000.0
            val predictionSeconds = (nowMs - track.seenAt + predictionLeadMs.coerceIn(-80, 120)) / 1000.0
            val predicted = track.headDistance + track.velocity * predictionSeconds
            val lateAllowance = max(4.0, abs(track.velocity) * 0.080)
            // A fast note may appear in only two frames before crossing the line.
            val twoFrameCrossing = track.seen == 2 && track.approaching == 1 &&
                track.note.confidence >= 0.8 && track.seenAt - track.previousAt in 10..100 &&
                track.previousDistance > max(6.0, track.line.thickness * 2.0) &&
                predicted <= max(2.0, track.line.thickness * 0.7) &&
                track.velocity < -height * 0.15
            val established = track.seen >= 3 && track.approaching >= 2
            if (!track.fired && observed && track.startedAway && (established || twoFrameCrossing) && track.velocity < -height * 0.05 &&
                abs(track.velocity) <= height * 12.0 &&
                predicted <= max(2.0, track.line.thickness * 0.7) && predicted >= -lateAllowance) {
                track.fired = true; track.firedAt = nowMs; track.activeUntil = nowMs + 100
            }
            if (!track.fired || scene.lines.none { compatible(track.line, it) }) continue
            val active = if (track.kind == 3) {
                if (missing > 90 || track.tailDistance <= max(2.0, track.line.thickness * 0.7) ||
                    nowMs - track.firedAt > 60_000) { track.finished = true; false } else true
            } else nowMs <= track.activeUntil
            if (!active) continue
            val endpoint = endpoints(track.note, track.line, track.side)
            val nx = -track.line.dy; val ny = track.line.dx
            val distance = signedDistance(endpoint.x, endpoint.y, track.line)
            val tangentVelocity = (track.vx - track.lineVx) * track.line.dx +
                (track.vy - track.lineVy) * track.line.dy
            val x = endpoint.x - nx * distance +
                (track.lineVx + track.line.dx * tangentVelocity) * ageSeconds
            val y = endpoint.y - ny * distance +
                (track.lineVy + track.line.dy * tangentVelocity) * ageSeconds
            val normalizedX = (x / width).toFloat(); val normalizedY = (y / height).toFloat()
            if (!normalizedX.isFinite() || !normalizedY.isFinite() ||
                normalizedX !in 0.005f..0.995f || normalizedY !in 0.005f..0.995f) continue
            val kind = when (track.kind) {
                1 -> VisualTouchKind.TAP; 2 -> VisualTouchKind.DRAG
                3 -> VisualTouchKind.HOLD; else -> VisualTouchKind.FLICK
            }
            output.add(VisualTouch(track.id, kind, normalizedX, normalizedY,
                (nx * track.side * height * 0.06 / width).toFloat(),
                (ny * track.side * 0.06).toFloat()))
        }
        lastDiagnostic = "视觉跟踪 " + tracks.size + " 个音符；触点 " + output.size + " 个"
        return output
    }

    private fun update(track: Track, note: VisualNoteBlob, incoming: VisualJudgeLine, at: Long) {
        val line = oriented(incoming, track.line)
        val dt = (at - track.seenAt) / 1000.0
        val endpoint = endpoints(note, line, track.side)
        val velocity = (endpoint.near - track.headDistance) / dt
        val plausible = dt >= 0.010 && abs(velocity) <= height * 12.0
        if (!plausible && track.fired) track.finished = true
        track.approaching = if (plausible && velocity < -height * 0.05) track.approaching + 1 else 0
        track.velocity = if (!plausible) 0.0 else if (track.seen == 1) velocity
            else 0.65 * velocity + 0.35 * track.velocity
        track.vx = (note.x - track.note.x) / dt; track.vy = (note.y - track.note.y) / dt
        track.lineVx = (line.x - track.line.x) / dt; track.lineVy = (line.y - track.line.y) / dt
        track.previousDistance = track.headDistance; track.previousAt = track.seenAt
        track.note = note; track.line = line; track.seenAt = at
        track.headDistance = endpoint.near; track.tailDistance = endpoint.far; track.seen++
    }
    private fun endpoints(note: VisualNoteBlob, line: VisualJudgeLine, side: Double): Endpoint {
        val ax = note.x - note.extentX; val ay = note.y - note.extentY
        val bx = note.x + note.extentX; val by = note.y + note.extentY
        val a = signedDistance(ax, ay, line) * side; val b = signedDistance(bx, by, line) * side
        return if (a <= b) Endpoint(ax, ay, a, b) else Endpoint(bx, by, b, a)
    }
    private fun signedDistance(x: Double, y: Double, line: VisualJudgeLine) =
        (x - line.x) * -line.dy + (y - line.y) * line.dx
    private fun compatible(a: VisualJudgeLine, b: VisualJudgeLine) =
        abs(a.dx * b.dx + a.dy * b.dy) > 0.96 &&
            hypot(a.x - b.x, a.y - b.y) <= max(15.0, width * 0.15)
    private fun oriented(line: VisualJudgeLine, previous: VisualJudgeLine): VisualJudgeLine =
        if (line.dx * previous.dx + line.dy * previous.dy < 0.0)
            line.copy(dx = -line.dx, dy = -line.dy) else line
    private fun valid(scene: VisualScene) =
        scene.reliable && scene.width in 32..1920 && scene.height in 16..1920 &&
            scene.width > scene.height && scene.lines.size in 1..12 && scene.notes.size <= 128 &&
            scene.lines.all {
                listOf(it.x, it.y, it.dx, it.dy, it.length, it.thickness).all(Double::isFinite) &&
                    abs(hypot(it.dx, it.dy) - 1.0) < 0.01 &&
                    it.length >= 20.0 && it.thickness in 0.1..20.0
            }
    private fun validNote(note: VisualNoteBlob) =
        note.kind in 1..4 && note.lineIndex in 0 until 12 && note.confidence in 0.6..1.0 &&
            listOf(note.x, note.y, note.extentX, note.extentY).all(Double::isFinite) &&
            note.x in 0.0..width.toDouble() && note.y in 0.0..height.toDouble() &&
            (note.kind == 3 || (note.extentX == 0.0 && note.extentY == 0.0))
    private fun reject(message: String): List<VisualTouch> {
        reset(); lastDiagnostic = message; return emptyList()
    }
}
