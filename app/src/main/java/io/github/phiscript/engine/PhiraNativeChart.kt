package io.github.phiscript.engine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Phira judges line-local x; rendering controls do not alter that coordinate. */
internal class PhiraNativeChart private constructor(
    private val lines: List<NativeLine>, private val noteX: Map<Int, NativeAnim>, private val order: List<Int>
) {
    private var cacheTime = Double.NaN
    private var cacheWidth = Float.NaN
    private var cacheHeight = Float.NaN
    private val px = DoubleArray(lines.size)
    private val py = DoubleArray(lines.size)
    private val rotation = DoubleArray(lines.size)
    @Synchronized fun position(note: Note, seconds: Double, viewport: Viewport): Point? {
        require(seconds.isFinite())
        if (cacheTime != seconds || cacheWidth != viewport.width || cacheHeight != viewport.height) {
            for (index in order) {
                val line = lines[index]
                val x = line.x.value(seconds) * viewport.width / 2.0
                val y = line.y.value(seconds) * viewport.height / 2.0
                val own = line.rotation.value(seconds) * PI / 180.0
                if (line.parent < 0) {
                    px[index] = x; py[index] = y; rotation[index] = own
                } else {
                    val p = line.parent; val c = cos(rotation[p]); val s = sin(rotation[p])
                    px[index] = px[p] + c * x - s * y
                    py[index] = py[p] + s * x + c * y
                    rotation[index] = own + if (line.rotWithParent) rotation[p] else 0.0
                }
            }
            cacheTime = seconds; cacheWidth = viewport.width; cacheHeight = viewport.height
        }
        val index = note.lineIndex; val angle = rotation[index]
        val along = noteX.getValue(note.id).value(seconds) * viewport.width / 2.0
        val x = viewport.left + viewport.width / 2.0 + px[index] + along * cos(angle)
        val y = viewport.top + viewport.height / 2.0 - py[index] - along * sin(angle)
        if (!x.isFinite() || !y.isFinite()) return null
        val point = Point(x.toFloat(), y.toFloat())
        if (viewport.contains(point)) return point
        // Local y is unrestricted; preserve local x and find a visible point.
        val nx = -sin(angle); val ny = -cos(angle)
        var lower = Double.NEGATIVE_INFINITY; var upper = Double.POSITIVE_INFINITY
        val margin = minOf(0.5, viewport.width / 8.0, viewport.height / 8.0)
        fun clip(origin: Double, direction: Double, a: Double, b: Double): Boolean {
            if (abs(direction) < 1e-12) return origin in a..b
            val u = (a - origin) / direction; val v = (b - origin) / direction
            lower = maxOf(lower, minOf(u, v)); upper = minOf(upper, maxOf(u, v))
            return lower <= upper
        }
        if (!clip(x, nx, viewport.left + margin, viewport.right - margin) ||
            !clip(y, ny, viewport.top + margin, viewport.bottom - margin)) return point
        val shift = 0.0.coerceIn(lower, upper)
        return Point((x + nx * shift).toFloat(), (y + ny * shift).toFloat())
    }
    @Synchronized fun normal(note: Note, seconds: Double, viewport: Viewport): Point {
        position(note, seconds, viewport)
        val angle = rotation[note.lineIndex]
        require(angle.isFinite()) { "Phira 判定线旋转无效" }
        return Point((-sin(angle)).toFloat(), (-cos(angle)).toFloat())
    }
    companion object {
        fun parse(root: JSONObject): Chart {
            require(root.getInt("phiscriptFormat") == 1) { "未知 Phira 内部谱面版本" }
            val offset = root.nativeNumber("offset", 0.0)
            require(abs(offset) <= 3600.0) { "Phira 谱面偏移无效" }
            val source = root.getJSONArray("lines")
            require(source.length() in 1..4096) { "Phira 判定线数量无效" }
            val budget = NativeBudget(); val lines = ArrayList<NativeLine>()
            val notes = ArrayList<Note>(); val noteX = HashMap<Int, NativeAnim>(); var id = 0
            for (index in 0 until source.length()) {
                val line = source.getJSONObject(index); val obj = line.optJSONObject("object") ?: JSONObject()
                val parent = line.optInt("parent", -1)
                require(parent in -1 until source.length()) { "Phira 父线索引无效" }
                lines.add(NativeLine(parent, line.optBoolean("rotWithParent", false),
                    NativeAnim.read(obj.optJSONArray("x"), budget), NativeAnim.read(obj.optJSONArray("y"), budget),
                    NativeAnim.read(obj.optJSONArray("rotation"), budget)))
                val array = line.optJSONArray("notes") ?: JSONArray()
                require(array.length() <= 100_000) { "Phira 音符数量过多" }
                for (n in 0 until array.length()) {
                    val raw = array.getJSONObject(n)
                    if (raw.optBoolean("fake", false)) continue
                    require(id < 100_000) { "Phira 音符总数过多" }
                    val type = raw.getInt("type"); require(type in 1..4) { "Phira 音符类型无效" }
                    val start = raw.nativeNumber("time")
                    val end = if (type == 3) raw.nativeNumber("endTime") else start
                    require(start >= 0.0 && end >= start && end <= 7200.0) { "Phira 音符时间无效" }
                    val x = NativeAnim.read(raw.optJSONObject("object")?.optJSONArray("x"), budget)
                    notes.add(Note(id, type, start, end, x.value(start) / 0.1125,
                        raw.nativeNumber("speed", 1.0), raw.nativeNumber("height", 0.0),
                        raw.optBoolean("above", true), index))
                    noteX[id++] = x
                }
            }
            require(notes.isNotEmpty()) { "Phira 谱面没有可演奏音符" }
            val order = ArrayList<Int>(); val state = IntArray(lines.size)
            for (start in lines.indices) {
                if (state[start] == 2) continue
                val path = ArrayList<Int>(); var cursor = start
                while (cursor >= 0 && state[cursor] == 0) {
                    state[cursor] = 1; path.add(cursor); cursor = lines[cursor].parent
                }
                require(cursor < 0 || state[cursor] != 1) { "Phira 父子判定线存在循环" }
                for (p in path.asReversed()) { state[p] = 2; order.add(p) }
            }
            val runtime = PhiraNativeChart(lines, noteX, order)
            return Chart.fromNative(notes.sortedWith(compareBy<Note> { it.timeSeconds }.thenBy { it.id }),
                offset, runtime::position, runtime::normal)
        }
    }
}
private data class NativeLine(val parent: Int, val rotWithParent: Boolean,
    val x: NativeAnim, val y: NativeAnim, val rotation: NativeAnim)
private class NativeBudget { var frames = 0 }
private data class NativeKeyframe(val time: Double, val value: Double, val tween: NativeTween)
private class NativeAnim(private val layers: List<List<NativeKeyframe>>) {
    fun value(time: Double): Double = layers.sumOf { frames ->
        var low = 0; var high = frames.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (frames[mid].time <= time) low = mid + 1 else high = mid
        }
        val index = (low - 1).coerceAtLeast(0); val a = frames[index]
        if (index == frames.lastIndex) a.value else {
            val b = frames[index + 1]
            if (b.time == a.time) a.value else {
                val fraction = (time - a.time) / (b.time - a.time)
                a.value + (b.value - a.value) * a.tween.value(fraction)
            }
        }
    }
    companion object {
        fun read(array: JSONArray?, budget: NativeBudget): NativeAnim {
            if (array == null) return NativeAnim(emptyList())
            require(array.length() <= 64) { "Phira 动画层数量过多" }
            val ignoreLayers = array.length() > 0 && array.getJSONArray(0).length() == 0
            val layers = ArrayList<List<NativeKeyframe>>()
            for (i in 0 until array.length()) {
                val source = array.getJSONArray(i); budget.frames += source.length()
                require(budget.frames <= 400_000) { "Phira 动画关键帧总数过多" }
                if (source.length() == 0) {
                    require(i == 0 || ignoreLayers) { "Phira 动画包含非首空层，无法可靠播放" }
                    continue
                }
                // Keep incoming and outgoing endpoints at instantaneous jumps.
                val frames = ArrayList<NativeKeyframe>()
                for (j in 0 until source.length()) {
                    val f = source.getJSONObject(j); val time = f.nativeNumber("time")
                    require(abs(time) <= 1_000_000_000.0) { "Phira 动画时间超出范围" }
                    frames.add(NativeKeyframe(time, f.nativeNumber("value"), NativeTween.read(f.optJSONObject("tween"))))
                }
                if (!ignoreLayers) layers.add(frames.sortedBy { it.time })
            }
            return NativeAnim(layers)
        }
    }
}
private class NativeTween(private val id: Int, private val left: Double,
    private val right: Double, private val bezier: DoubleArray?) {
    private val bezierSamples = bezier?.let { p -> DoubleArray(21) { i -> sample(p[0], p[2], i / 20.0) } }
    private fun bezierTime(x: Double, p: DoubleArray): Double {
        if (x == 0.0 || x == 1.0) return x
        val table = requireNotNull(bezierSamples); val step = 0.05
        val index = (x / step).toInt().coerceIn(0, 19)
        var t = step * (index + (x - table[index]) / (table[index + 1] - table[index]))
        fun slope(at: Double): Double {
            val a = (p[0] - p[2]) * 3.0 + 1.0
            val b = p[2] * 3.0 - p[0] * 6.0; val c = p[0] * 3.0
            return (a * 3.0 * at + b * 2.0) * at + c
        }
        val initialSlope = slope(t)
        if (initialSlope <= 1e-7) return t
        if (initialSlope >= 1e-3) {
            repeat(4) {
                val d = slope(t); if (d <= 1e-7) return t
                t -= (sample(p[0], p[2], t) - x) / d
            }
            return t
        }
        var low = step * index; var high = step * (index + 1)
        t = (low + high) / 2.0
        repeat(10) {
            val difference = sample(p[0], p[2], t) - x
            if (abs(difference) <= 1e-7) return t
            if (difference > 0.0) high = t else low = t
            t = (low + high) / 2.0
        }
        return t
    }
    fun value(x: Double): Double {
        bezier?.let { points -> return sample(points[1], points[3], bezierTime(x, points)) }
        if (left == 0.0 && right == 1.0) return static(id, x)
        val a = static(id, left); val b = static(id, right); val range = b - a
        require(abs(range) > 1e-12) { "Phira 缓动区间输出范围为零" }
        return (static(id, left + (right - left) * x) - a) / range
    }
    companion object {
        fun read(root: JSONObject?): NativeTween {
            val id = root?.optInt("id", 0) ?: 0
            require(id in 0..32) { "Phira 缓动编号无效" }
            val left = root?.nativeNumber("left", 0.0) ?: 0.0
            val right = root?.nativeNumber("right", 1.0) ?: 1.0
            require(left in 0.0..1.0 && right in 0.0..1.0 && left < right) { "Phira 缓动区间无效" }
            val points = root?.optJSONArray("bezier")?.let {
                require(it.length() == 4) { "Phira 贝塞尔参数无效" }
                DoubleArray(4) { k -> it.getDouble(k).also { v -> require(v.isFinite()) } }
            }
            if (points != null) require(points[0] in 0.0..1.0 && points[2] in 0.0..1.0) {
                "Phira 贝塞尔 x 参数超出范围"
            }
            if (points == null && (left != 0.0 || right != 1.0))
                require(abs(static(id, right) - static(id, left)) > 1e-12) { "Phira 缓动区间输出范围为零" }
            return NativeTween(id, left, right, points)
        }
        private fun sample(a: Double, b: Double, t: Double): Double {
            val u = 1.0 - t
            return 3.0 * u * u * t * a + 3.0 * u * t * t * b + t * t * t
        }
        private fun static(id: Int, x: Double): Double {
            if (id == 0) return 0.0
            if (id == 1) return 1.0
            if (id == 2) return x
            val family = (id - 3) / 3
            fun inside(t: Double): Double = when (family) {
                0 -> 1.0 - cos(t * PI / 2.0)
                1 -> t * t
                2 -> t * t * t
                3 -> t.pow(4)
                4 -> t.pow(5)
                5 -> 2.0.pow(10.0 * (t - 1.0))
                6 -> 1.0 - sqrt(1.0 - t * t)
                7 -> (2.70158 * t - 1.70158) * t * t
                8 -> -2.0.pow(10.0 * t - 10.0) * sin((t * 10.0 - 10.75) * 2.0 * PI / 3.0)
                9 -> {
                    val u = 1.0 - t
                    val out = when {
                        u < 1.0 / 2.75 -> 7.5625 * u * u
                        u < 2.0 / 2.75 -> 7.5625 * (u - 1.5 / 2.75).pow(2) + 0.75
                        u < 2.5 / 2.75 -> 7.5625 * (u - 2.25 / 2.75).pow(2) + 0.9375
                        else -> 7.5625 * (u - 2.625 / 2.75).pow(2) + 0.984375
                    }
                    1.0 - out
                }
                else -> error("Phira 缓动编号无效")
            }
            return when ((id - 3) % 3) {
                0 -> inside(x); 1 -> 1.0 - inside(1.0 - x)
                else -> if (x * 2.0 < 1.0) inside(x * 2.0) / 2.0 else 1.0 - inside(2.0 - x * 2.0) / 2.0
            }
        }
    }
}
private fun JSONObject.nativeNumber(key: String, fallback: Double? = null): Double {
    val value = if (has(key) && !isNull(key)) getDouble(key) else fallback ?: error("Phira 谱面缺少字段：" + key)
    require(value.isFinite()) { "Phira 谱面数字无效：" + key }
    return value
}
