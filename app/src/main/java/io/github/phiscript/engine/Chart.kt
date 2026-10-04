package io.github.phiscript.engine

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/** Coordinates use one explicitly selected space: capture pixels or real screen pixels. */
data class Viewport(val left: Float, val top: Float, val width: Float, val height: Float) {
    init {
        require(listOf(left, top, width, height).all { it.isFinite() })
        require(width > 0f && height > 0f) { "玩法视口不能为空" }
    }
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    fun scaleTo(sourceWidth: Int, sourceHeight: Int, destinationWidth: Int, destinationHeight: Int): Viewport {
        require(sourceWidth > 0 && sourceHeight > 0 && destinationWidth > 0 && destinationHeight > 0)
        val sx = destinationWidth.toFloat() / sourceWidth
        val sy = destinationHeight.toFloat() / sourceHeight
        return Viewport(left * sx, top * sy, width * sx, height * sy)
    }
    fun contains(point: Point): Boolean =
        point.x >= left && point.x < right && point.y >= top && point.y < bottom
}
data class Point(val x: Float, val y: Float)
data class Note internal constructor(
    val id: Int,
    /** 1 = tap, 2 = drag, 3 = hold, 4 = flick. */
    val type: Int,
    val timeSeconds: Double,
    val endSeconds: Double,
    val positionX: Double,
    val speed: Double,
    val floorPosition: Double,
    val above: Boolean,
    val lineIndex: Int
)

/** Format 3. All times are chart-local; a visual epoch already incorporates audio offset. */
class Chart private constructor(
    val notes: List<Note>,
    val offsetSeconds: Double,
    private val lines: List<JudgeLine>
) {
    val durationSeconds: Double = notes.maxOf { it.endSeconds }
    val firstNoteSeconds: Double = notes.minOf { it.timeSeconds }
    fun position(note: Note, seconds: Double, viewport: Viewport, visual: Boolean = false): Point? {
        require(seconds.isFinite())
        val line = lines[note.lineIndex]
        val ticks = seconds / line.secondsPerTick
        if (visual && line.opacity.value(ticks, 1.0) < 0.15) return null
        val angle = -line.rotation.value(ticks, 0.0) * Math.PI / 180.0
        val along = 0.05625 * viewport.width * note.positionX
        var x = viewport.left + viewport.width * line.moveX.value(ticks, 0.5) + along * cos(angle)
        var y = viewport.top + viewport.height * (1.0 - line.moveY.value(ticks, 0.5)) + along * sin(angle)
        if (visual) {
            val distance = 0.6 * viewport.height * (note.floorPosition - line.floorAt(seconds)) *
                (if (note.type == 3) 1.0 else note.speed) * (if (note.above) 1.0 else -1.0)
            x += distance * sin(angle)
            y -= distance * cos(angle)
        }
        return Point(x.toFloat(), y.toFloat())
    }
    companion object {
        fun parse(json: String): Chart {
            val root = JSONObject(json)
            require(root.optInt("formatVersion", -1) == 3) { "仅支持官方 formatVersion 3 JSON 谱面" }
            val offset = root.finite("offset", 0.0)
            val allNotes = ArrayList<Note>()
            val linesJson = root.getJSONArray("judgeLineList")
            require(linesJson.length() in 1..4096) { "判定线数量无效" }
            val lines = ArrayList<JudgeLine>()
            var id = 0
            for (lineIndex in 0 until linesJson.length()) {
                val objectLine = linesJson.getJSONObject(lineIndex)
                val bpm = objectLine.finite("bpm")
                require(bpm > 0.0) { "BPM 必须大于零" }
                val k = 1.875 / bpm
                val move = objectLine.optJSONArray("judgeLineMoveEvents")
                val line = JudgeLine(k,
                    readEvents(move, "start", "end"),
                    readEvents(move, "start2", "end2"),
                    readEvents(objectLine.optJSONArray("judgeLineRotateEvents"), "start", "end"),
                    readEvents(objectLine.optJSONArray("judgeLineDisappearEvents"), "start", "end"),
                    readSpeeds(objectLine.getJSONArray("speedEvents"), k))
                lines.add(line)
                for ((key, above) in listOf("notesAbove" to true, "notesBelow" to false)) {
                    val array = objectLine.optJSONArray(key) ?: JSONArray()
                    require(array.length() <= 100_000) { "音符数量过多" }
                    for (i in 0 until array.length()) {
                        val n = array.getJSONObject(i)
                        val type = n.getInt("type")
                        require(type in 1..4) { "未知音符类型：$type" }
                        val time = n.finite("time") * k
                        val hold = n.finite("holdTime", 0.0) * k
                        require(hold >= 0.0) { "长按时长不能小于零" }
                        val note = Note(id++, type, time, time + if (type == 3) hold else 0.0,
                            n.finite("positionX"), n.finite("speed", 1.0), n.finite("floorPosition"), above, lineIndex)
                        require(note.timeSeconds.isFinite() && note.endSeconds.isFinite())
                        allNotes.add(note)
                    }
                }
            }
            require(allNotes.isNotEmpty()) { "谱面没有音符" }
            require(allNotes.size <= 100_000) { "音符总数量过多" }
            return Chart(allNotes.sortedWith(compareBy<Note> { it.timeSeconds }.thenBy { it.id }), offset, lines)
        }
    }
}
private data class ValueEvent(val startTime: Double, val endTime: Double, val start: Double, val end: Double)
private class EventTrack(private val events: List<ValueEvent>) {
    fun value(tick: Double, fallback: Double): Double {
        if (events.isEmpty()) return fallback
        var low = 0
        var high = events.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (events[mid].startTime <= tick) low = mid + 1 else high = mid
        }
        val event = events[(low - 1).coerceAtLeast(0)]
        val fraction = if (event.endTime == event.startTime) {
            if (tick < event.startTime) 0.0 else 1.0
        } else ((tick - event.startTime) / (event.endTime - event.startTime)).coerceIn(0.0, 1.0)
        return event.start + (event.end - event.start) * fraction
    }
}
private data class SpeedSegment(val start: Double, val end: Double, val speed: Double, val floor: Double)
private class JudgeLine(
    val secondsPerTick: Double,
    val moveX: EventTrack,
    val moveY: EventTrack,
    val rotation: EventTrack,
    val opacity: EventTrack,
    private val speeds: List<SpeedSegment>
) {
    fun floorAt(seconds: Double): Double {
        if (speeds.isEmpty()) return 0.0
        var low = 0
        var high = speeds.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (speeds[mid].start <= seconds) low = mid + 1 else high = mid
        }
        if (low == 0) return 0.0
        val segment = speeds[low - 1]
        return segment.floor + (seconds.coerceAtMost(segment.end) - segment.start) * segment.speed
    }
}
private fun JSONObject.finite(key: String, fallback: Double? = null): Double {
    val value = if (has(key) && !isNull(key)) getDouble(key) else fallback ?: error("谱面缺少字段：$key")
    require(value.isFinite()) { "谱面字段不是有限数字：$key" }
    return value
}
private fun readEvents(array: JSONArray?, startKey: String, endKey: String): EventTrack {
    if (array == null) return EventTrack(emptyList())
    require(array.length() <= 100_000) { "判定线事件过多" }
    val events = (0 until array.length()).map { i ->
        val e = array.getJSONObject(i)
        val start = e.finite("startTime")
        val end = e.finite("endTime")
        require(end >= start) { "判定线事件结束时间早于开始时间" }
        ValueEvent(start, end, e.finite(startKey), e.finite(endKey))
    }.sortedBy { it.startTime }
    return EventTrack(events)
}
private fun readSpeeds(array: JSONArray, secondsPerTick: Double): List<SpeedSegment> {
    require(array.length() in 1..100_000) { "速度事件数量无效" }
    data class RawSpeed(val start: Double, val end: Double, val speed: Double)
    val raw = (0 until array.length()).map { i ->
        val e = array.getJSONObject(i)
        val start = e.finite("startTime") * secondsPerTick
        val end = e.finite("endTime") * secondsPerTick
        require(start.isFinite() && end.isFinite() && end >= start) { "速度事件时间无效" }
        RawSpeed(start.coerceAtLeast(0.0), end, e.finite("value"))
    }.filter { it.end > it.start }.sortedBy { it.start }
    var floor = 0.0
    var previousEnd = 0.0
    return raw.map { event ->
        require(event.start >= previousEnd - 1e-9) { "速度事件重叠，无法可靠解释该谱面" }
        val result = SpeedSegment(event.start, event.end, event.speed, floor)
        floor += (event.end - event.start) * event.speed
        require(floor.isFinite()) { "累计速度超出范围" }
        previousEnd = event.end
        result
    }
}
