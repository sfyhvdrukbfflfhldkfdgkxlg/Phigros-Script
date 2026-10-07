package io.github.phiscript.engine
import kotlin.math.abs
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
                // Move for 60 ms, then retain contact for one more game frame.
                4 -> TouchEvent(note, hit - 28, hit + 68)
                else -> error("未知音符类型")
            }
        }.sortedWith(compareBy<TouchEvent> { it.startMs }.thenBy { it.note.id })
    }
}
fun Chart.touchPosition(event: TouchEvent, timeMs: Long, viewport: Viewport): Point {
    val seconds = timeMs.coerceIn(event.startMs, event.endMs) / 1000.0
    val point = requireNotNull(position(event.note, seconds, viewport))
    if (event.note.type != 4) return point
    val normal = judgeNormal(event.note, seconds, viewport)
    var lower = Double.NEGATIVE_INFINITY; var upper = Double.POSITIVE_INFINITY
    fun clip(origin: Double, direction: Double, a: Double, b: Double): Boolean {
        if (abs(direction) < 1e-12) return origin in a..b
        val u = (a - origin) / direction; val v = (b - origin) / direction
        lower = maxOf(lower, minOf(u, v)); upper = minOf(upper, maxOf(u, v))
        return lower <= upper
    }
    check(clip(point.x.toDouble(), normal.x.toDouble(), viewport.left.toDouble(), Math.nextDown(viewport.right).toDouble()) &&
        clip(point.y.toDouble(), normal.y.toDouble(), viewport.top.toDouble(), Math.nextDown(viewport.bottom).toDouble())) {
        "Flick 判定位置不在玩法视口内"
    }
    val distance = minOf(viewport.height * 0.10, upper - lower)
    check(distance.isFinite() && distance > 1e-3) { "Flick 没有可用的滑动空间" }
    val start = (-distance / 2.0).coerceIn(lower, maxOf(lower, upper - distance))
    val progress = ((timeMs - event.startMs).toDouble() / 60.0).coerceIn(0.0, 1.0)
    val displacement = start + distance * progress
    return Point((point.x + normal.x * displacement).toFloat(), (point.y + normal.y * displacement).toFloat())
}
