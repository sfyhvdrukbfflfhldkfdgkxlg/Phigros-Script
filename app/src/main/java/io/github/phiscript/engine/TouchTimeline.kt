package io.github.phiscript.engine
import kotlin.math.roundToLong

/** Absolute times in milliseconds from the selected playback epoch. */
data class TouchEvent(val note: Note, val startMs: Long, val endMs: Long) {
    companion object {
        fun forChart(chart: Chart): List<TouchEvent> = chart.notes.map { note ->
            val hit = (note.timeSeconds * 1000.0).roundToLong()
            when (note.type) {
                1 -> TouchEvent(note, hit, hit + 24)
                2 -> TouchEvent(note, hit - 30, hit + 40)
                3 -> TouchEvent(note, hit, (note.endSeconds * 1000.0).roundToLong() + 24)
                4 -> TouchEvent(note, hit - 28, hit + 32)
                else -> error("未知音符类型")
            }
        }.sortedWith(compareBy<TouchEvent> { it.startMs }.thenBy { it.note.id })
    }
}
fun Chart.touchPosition(event: TouchEvent, timeMs: Long, viewport: Viewport): Point {
    val seconds = timeMs.coerceIn(event.startMs, event.endMs) / 1000.0
    val point = requireNotNull(position(event.note, seconds, viewport))
    if (event.note.type != 4) return point
    val distance = viewport.height * 0.10f
    val progress = ((timeMs - event.startMs).toFloat() / (event.endMs - event.startMs)).coerceIn(0f, 1f)
    val displacement = (progress - 0.5f) * distance
    val useHorizontal = point.x - distance / 2 >= viewport.left && point.x + distance / 2 < viewport.right
    return if (useHorizontal) Point(point.x + displacement, point.y) else Point(point.x, point.y + displacement)
}
