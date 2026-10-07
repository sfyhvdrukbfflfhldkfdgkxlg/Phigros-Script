package io.github.phiscript.assets
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal object RpeNativeNormalizer {
    private const val MAX_EVENTS = 100_000
    private val easingMap = intArrayOf(2, 2, 4, 3, 7, 6, 5, 8, 10, 9,
        13, 12, 11, 14, 16, 15, 19, 18, 22, 21, 25, 24, 23, 26, 28, 27, 31, 30, 32, 29)
    fun normalize(root: JSONObject): JSONObject {
        val clock = BeatClock(root.getJSONArray("BPMList"))
        val input = root.getJSONArray("judgeLineList")
        require(input.length() in 1..4096) { "RPE 判定线数量无效" }
        val output = JSONArray(); var notesTotal = 0; var eventsTotal = 0
        for (lineIndex in 0 until input.length()) {
            val source = input.getJSONObject(lineIndex)
            val layers = source.optJSONArray("eventLayers") ?: JSONArray()
            require(layers.length() <= 64) { "RPE 事件层超过 64 层" }
            val obj = JSONObject()
            for ((field, key, factor) in listOf(Triple("x", "moveXEvents", 2.0 / 1350.0),
                Triple("y", "moveYEvents", 2.0 / 900.0), Triple("rotation", "rotateEvents", -1.0))) {
                val tracks = JSONArray()
                for (i in 0 until layers.length()) {
                    if (layers.isNull(i)) continue
                    val events = layers.getJSONObject(i).optJSONArray(key) ?: continue
                    eventsTotal += events.length()
                    require(eventsTotal <= MAX_EVENTS) { "RPE 判定线事件总数过多" }
                    if (events.length() == 0) continue
                    val ordered = (0 until events.length()).map { events.getJSONObject(it) }
                        .sortedBy { clock.seconds(it.getJSONArray("startTime")) }
                    val frames = JSONArray(); var previousEnd = Double.NEGATIVE_INFINITY
                    for (event in ordered) {
                        val start = clock.seconds(event.getJSONArray("startTime"))
                        val end = clock.seconds(event.getJSONArray("endTime"))
                        require(end >= start && start >= previousEnd) { "RPE 同层事件重叠或时间无效" }
                        previousEnd = end
                        frames.put(keyframe(start, number(event, "start") * factor, tween(event)))
                        frames.put(keyframe(end, number(event, "end") * factor, JSONObject().put("id", 0)))
                    }
                    tracks.put(frames)
                }
                obj.put(field, tracks)
            }
            val notes = JSONArray(); val original = source.optJSONArray("notes") ?: JSONArray()
            require(original.length() <= MAX_EVENTS) { "RPE 音符数量过多" }
            for (i in 0 until original.length()) {
                val note = original.getJSONObject(i)
                if (note.optInt("isFake", 0) != 0) continue
                require(++notesTotal <= MAX_EVENTS) { "RPE 音符总数过多" }
                val type = when (note.getInt("type")) {
                    1 -> 1; 2 -> 3; 3 -> 4; 4 -> 2; else -> error("RPE 未知音符类型")
                }
                val start = clock.seconds(note.getJSONArray("startTime"))
                val end = if (type == 3) clock.seconds(note.getJSONArray("endTime")) else start
                require(start >= 0.0 && end >= start && end <= 7200.0) { "RPE 音符时间无效" }
                val above = note.optInt("above", 1); require(above in 1..2) { "RPE 音符 above 字段无效" }
                val x = JSONArray().put(JSONArray().put(keyframe(0.0, number(note, "positionX") * 2.0 / 1350.0,
                    JSONObject().put("id", 0))))
                notes.put(JSONObject().put("type", type).put("time", start).put("endTime", end)
                    .put("object", JSONObject().put("x", x)).put("above", above == 1)
                    .put("speed", number(note, "speed", 1.0)).put("height", 0.0).put("fake", false))
            }
            output.put(JSONObject().put("object", obj).put("notes", notes)
                .put("parent", source.optInt("father", -1))
                .put("rotWithParent", source.optBoolean("rotateWithFather", false)))
        }
        require(notesTotal > 0) { "RPE 谱面没有可演奏音符" }
        return JSONObject().put("phiscriptFormat", 1)
            .put("offset", root.optJSONObject("META")?.let { number(it, "offset", 0.0) / 1000.0 } ?: 0.0)
            .put("lines", output)
    }
    private fun tween(event: JSONObject): JSONObject {
        val index = event.optInt("easingType", 1).coerceAtLeast(1)
        val id = easingMap.getOrElse(index) { easingMap[0] }
        val result = JSONObject().put("id", id)
        if (event.optInt("bezier", 0) != 0) {
            val points = event.getJSONArray("bezierPoints"); require(points.length() == 4) { "RPE 贝塞尔参数无效" }
            val copy = JSONArray()
            for (i in 0..3) copy.put(points.getDouble(i).also { require(it.isFinite()) { "RPE 贝塞尔参数无效" } })
            return result.put("bezier", copy)
        }
        val left = number(event, "easingLeft", 0.0).coerceIn(0.0, 1.0)
        val right = number(event, "easingRight", 1.0).coerceIn(0.0, 1.0)
        if (id > 2 && left < right && (left != 0.0 || right != 1.0)) result.put("left", left).put("right", right)
        return result
    }
    private fun keyframe(time: Double, value: Double, tween: JSONObject) =
        JSONObject().put("time", time).put("value", value).put("tween", tween)
    private class BeatClock(input: JSONArray) {
        private data class Tempo(val beat: Double, val bpm: Double, val time: Double)
        private val tempos: List<Tempo>
        init {
            require(input.length() in 1..10_000) { "RPE BPM 数量无效" }
            val raw = (0 until input.length()).map { i ->
                val item = input.getJSONObject(i); val bpm = number(item, "bpm")
                require(bpm > 0.0 && bpm <= 10_000.0) { "RPE BPM 无效" }
                beat(item.getJSONArray("startTime")) to bpm
            }.sortedBy { it.first }
            var time = 0.0
            tempos = raw.mapIndexed { i, pair ->
                if (i > 0) {
                    require(pair.first > raw[i - 1].first) { "RPE BPM 起始拍重复" }
                    time += (pair.first - raw[i - 1].first) * 60.0 / raw[i - 1].second
                }
                Tempo(pair.first, pair.second, time)
            }
        }
        fun seconds(triple: JSONArray): Double {
            val beat = beat(triple); var low = 0; var high = tempos.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (tempos[mid].beat <= beat) low = mid + 1 else high = mid
            }
            val tempo = tempos[(low - 1).coerceAtLeast(0)]
            val time = tempo.time + (beat - tempo.beat) * 60.0 / tempo.bpm
            require(time.isFinite() && abs(time) <= 1_000_000_000.0) { "RPE 时间超出范围" }
            return time
        }
    }
    private fun beat(array: JSONArray): Double {
        require(array.length() == 3) { "RPE 拍数必须是三元数组" }
        val values = DoubleArray(3) { array.getDouble(it) }
        require(values.all { it.isFinite() } && values[2] > 0.0) { "RPE 拍数分母无效" }
        val result = values[0] + values[1] / values[2]
        require(result.isFinite() && abs(result) <= 1_000_000.0) { "RPE 拍数超出范围" }
        return result
    }
    private fun number(root: JSONObject, key: String, fallback: Double? = null): Double {
        val result = if (root.has(key) && !root.isNull(key)) root.getDouble(key) else fallback ?: error("谱面缺少字段：" + key)
        require(result.isFinite()) { "谱面数字无效：" + key }; return result
    }
}
