package io.github.phiscript.assets

import io.github.phiscript.engine.Chart
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** PGR and linear RPE normalization. Unsupported coordinate controls fail explicitly. */
object PhiraChartParser {
    const val MAX_JSON_BYTES = 8 * 1024 * 1024
    private const val MAX_EVENTS = 100_000
    private const val MAX_SECONDS = 7200.0
    fun parse(text: String, infoOffsetSeconds: Double = 0.0): Chart =
        Chart.parse(normalizeJson(text, infoOffsetSeconds))
    fun normalizeJson(text: String, infoOffsetSeconds: Double = 0.0): String {
        require(text.length <= MAX_JSON_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) {
            "Phira 谱面 JSON 超过 8 MiB"
        }
        require(infoOffsetSeconds.isFinite() && abs(infoOffsetSeconds) <= 3600.0) { "Phira 信息偏移无效" }
        val root = jsonObject(text)
        rejectNonEmpty(root, "blockAreaList", "噪域")
        val normalized = when {
            root.has("formatVersion") -> normalizePgr(root)
            root.has("BPMList") && root.has("META") -> normalizeRpe(root)
            else -> error("暂只支持 Phira 的 PGR v1/v3 JSON 和基础 RPE JSON；PEC/PBC 尚不支持")
        }
        val offset = number(normalized, "offset", 0.0) + infoOffsetSeconds
        require(offset.isFinite() && abs(offset) <= 3600.0) { "谱面总偏移无效" }
        normalized.put("offset", offset)
        val result = normalized.toString()
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "转换后的谱面超过 8 MiB" }
        val chart = Chart.parse(result)
        require(chart.firstNoteSeconds >= 0.0 && chart.durationSeconds <= MAX_SECONDS) { "音符时间不在 0～7200 秒内" }
        return result
    }
    private fun normalizePgr(root: JSONObject): JSONObject {
        val version = root.getInt("formatVersion")
        require(version == 1 || version == 3) { "Phira PGR 仅支持 formatVersion 1/3" }
        val lines = boundedArray(root, "judgeLineList", 4096)
        for (i in 0 until lines.length()) {
            val line = lines.getJSONObject(i)
            val moves = line.optJSONArray("judgeLineMoveEvents") ?: JSONArray()
            require(moves.length() <= MAX_EVENTS) { "判定线事件过多" }
            if (version == 1) for (j in 0 until moves.length()) {
                val event = moves.getJSONObject(j)
                val start = number(event, "start"); val end = number(event, "end")
                event.put("start", (start - start % 1000.0) / 1000.0 / 880.0)
                event.put("end", (end - end % 1000.0) / 1000.0 / 880.0)
                event.put("start2", start % 1000.0 / 520.0)
                event.put("end2", end % 1000.0 / 520.0)
            }
            for (key in listOf("notesAbove", "notesBelow")) {
                val notes = line.optJSONArray(key) ?: JSONArray()
                require(notes.length() <= MAX_EVENTS) { "音符数量过多" }
                for (j in 0 until notes.length()) {
                    val note = notes.getJSONObject(j)
                    if (!note.has("floorPosition")) note.put("floorPosition", 0.0)
                }
            }
        }
        root.put("formatVersion", 3)
        return root
    }
    private data class Event(val start: Double, val end: Double, val from: Double, val to: Double)
    private fun normalizeRpe(root: JSONObject): JSONObject {
        val bpm = BeatClock(boundedArray(root, "BPMList", 10_000))
        val originalLines = boundedArray(root, "judgeLineList", 4096)
        val outputLines = JSONArray()
        var count = 0; var eventCount = 0
        for (i in 0 until originalLines.length()) {
            val source = originalLines.getJSONObject(i)
            require(source.optInt("father", -1) == -1) { "RPE 父子判定线当前谱面模式暂不支持" }
            require(source.isNull("attachUI") || !source.has("attachUI")) { "RPE UI 附着线尚不支持" }
            for ((key, field) in listOf("posControl" to "pos", "sizeControl" to "size",
                "alphaControl" to "alpha", "yControl" to "y")) rejectNonIdentityControl(source, key, field)
            source.optJSONObject("extended")?.let { extended ->
                for (key in listOf("scaleXEvents", "scaleYEvents")) rejectNonIdentityEvents(extended, key, 1.0)
                rejectNonIdentityEvents(extended, "inclineEvents", 0.0)
            }
            val layers = source.optJSONArray("eventLayers") ?: JSONArray()
            require(layers.length() <= 16) { "RPE 事件层超过 16 层" }
            val tracks = linkedMapOf<String, List<List<Event>>>()
            for (key in listOf("moveXEvents", "moveYEvents", "rotateEvents", "alphaEvents")) {
                val grouped = ArrayList<List<Event>>()
                for (layerIndex in 0 until layers.length()) {
                    if (layers.isNull(layerIndex)) continue
                    val array = layers.getJSONObject(layerIndex).optJSONArray(key) ?: continue
                    require(array.length() <= MAX_EVENTS) { "RPE 判定线事件过多" }
                    eventCount += array.length()
                    require(eventCount <= MAX_EVENTS) { "RPE 判定线事件总数过多" }
                    val events = (0 until array.length()).map { eventIndex ->
                        val e = array.getJSONObject(eventIndex)
                        val start = bpm.seconds(e.getJSONArray("startTime"))
                        val end = bpm.seconds(e.getJSONArray("endTime"))
                        require(end >= start) { "RPE 判定线事件结束早于开始" }
                        val from = number(e, "start"); val to = number(e, "end")
                        require(from == to || (e.optInt("easingType", 1) == 1 && e.optInt("bezier", 0) == 0)) {
                            "RPE 非线性/贝塞尔事件当前谱面模式暂不支持"
                        }
                        Event(start, end, from, to)
                    }.sortedBy { it.start }
                    for (j in 1 until events.size) require(events[j].start >= events[j - 1].end) {
                        "RPE 同层事件重叠，无法可靠解释"
                    }
                    if (events.isNotEmpty()) grouped.add(events)
                }
                tracks[key] = grouped
            }
            val above = JSONArray(); val below = JSONArray()
            val notes = source.optJSONArray("notes") ?: JSONArray()
            require(notes.length() <= MAX_EVENTS) { "RPE 音符过多" }
            var lineEnd = 1.0
            for (j in 0 until notes.length()) {
                val n = notes.getJSONObject(j)
                if (n.optInt("isFake", 0) != 0) continue
                require(++count <= MAX_EVENTS) { "RPE 音符总数过多" }
                val type = when (n.getInt("type")) {
                    1 -> 1; 2 -> 3; 3 -> 4; 4 -> 2; else -> error("RPE 未知音符类型")
                }
                val start = bpm.seconds(n.getJSONArray("startTime"))
                val end = if (type == 3) bpm.seconds(n.getJSONArray("endTime")) else start
                require(start >= 0.0 && end >= start && end <= MAX_SECONDS) { "RPE 音符时间无效" }
                require(abs(number(n, "yOffset", 0.0)) < 1e-9) { "RPE 音符 yOffset 尚不支持" }
                require(n.getInt("above") in 1..2) { "RPE 音符 above 字段无效" }
                lineEnd = maxOf(lineEnd, end + 1.0)
                val output = JSONObject().put("type", type).put("time", start)
                    .put("holdTime", if (type == 3) end - start else 0.0)
                    .put("positionX", number(n, "positionX") / (1350.0 * 0.05625))
                    .put("speed", 1.0).put("floorPosition", start)
                if (n.getInt("above") == 1) above.put(output) else below.put(output)
            }
            val move = combine(tracks.getValue("moveXEvents"), tracks.getValue("moveYEvents")) { x, y ->
                doubleArrayOf(0.5 + x / 1350.0, 0.5 + y / 900.0)
            }
            val rotation = combine(tracks.getValue("rotateEvents"), emptyList()) { x, _ -> doubleArrayOf(-x) }
            val alpha = combine(tracks.getValue("alphaEvents"), emptyList()) { x, _ -> doubleArrayOf(x / 255.0) }
            outputLines.put(JSONObject().put("bpm", 1.875).put("notesAbove", above).put("notesBelow", below)
                .put("judgeLineMoveEvents", move).put("judgeLineRotateEvents", rotation)
                .put("judgeLineDisappearEvents", alpha).put("speedEvents", JSONArray().put(JSONObject()
                    .put("startTime", 0.0).put("endTime", lineEnd).put("value", 1.0))))
        }
        require(count > 0) { "RPE 谱面没有可演奏音符" }
        return JSONObject().put("formatVersion", 3)
            .put("offset", number(root.getJSONObject("META"), "offset", 0.0) / 1000.0)
            .put("judgeLineList", outputLines)
    }
    /** Split at every event-layer boundary, preserving instantaneous jumps. */
    private fun combine(x: List<List<Event>>, y: List<List<Event>>,
                        convert: (Double, Double) -> DoubleArray): JSONArray {
        val boundaries = (x + y).flatten().flatMap { listOf(it.start, it.end) }.plus(0.0).distinct().sorted()
        require(boundaries.size <= MAX_EVENTS) { "合并后的 RPE 判定线事件过多" }
        val result = JSONArray()
        if (boundaries.size == 1) {
            val v = convert(value(x, boundaries.single()), value(y, boundaries.single()))
            result.put(valueEvent(boundaries.single(), boundaries.single(), v, v))
        } else for (i in 0 until boundaries.lastIndex) {
            val a = boundaries[i]; val b = boundaries[i + 1]
            val from = convert(value(x, a), value(y, a))
            val to = convert(value(x, b, leftLimit = true), value(y, b, leftLimit = true))
            result.put(valueEvent(a, b, from, to))
        }
        if (boundaries.size > 1) {
            val last = boundaries.last(); val v = convert(value(x, last), value(y, last))
            result.put(valueEvent(last, last, v, v))
        }
        return result
    }
    private fun value(tracks: List<List<Event>>, seconds: Double, leftLimit: Boolean = false): Double =
        tracks.sumOf { events ->
            var low = 0; var high = events.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (events[mid].start < seconds || (!leftLimit && events[mid].start == seconds)) low = mid + 1
                else high = mid
            }
            val event = events[(low - 1).coerceAtLeast(0)]
            val fraction = if (event.end == event.start) {
                if (seconds < event.start || (leftLimit && seconds == event.start)) 0.0 else 1.0
            } else ((seconds - event.start) / (event.end - event.start)).coerceIn(0.0, 1.0)
            event.from + (event.to - event.from) * fraction
        }
    private fun valueEvent(a: Double, b: Double, from: DoubleArray, to: DoubleArray): JSONObject {
        val result = JSONObject().put("startTime", a).put("endTime", b).put("start", from[0]).put("end", to[0])
        if (from.size > 1) result.put("start2", from[1]).put("end2", to[1])
        return result
    }
    private class BeatClock(array: JSONArray) {
        private data class Tempo(val beat: Double, val bpm: Double, val seconds: Double)
        private val tempos: List<Tempo>
        init {
            require(array.length() > 0) { "RPE 缺少 BPM" }
            val raw = (0 until array.length()).map { i ->
                val e = array.getJSONObject(i); val beats = beat(e.getJSONArray("startTime"))
                val bpm = number(e, "bpm")
                require(bpm > 0.0 && bpm <= 10_000.0) { "RPE BPM 无效" }; beats to bpm
            }.sortedBy { it.first }
            require(raw.first().first == 0.0) { "RPE 首个 BPM 必须从第 0 拍开始" }
            var seconds = 0.0
            tempos = raw.mapIndexed { i, pair ->
                if (i > 0) {
                    require(pair.first > raw[i - 1].first) { "RPE BPM 起始拍重复" }
                    seconds += (pair.first - raw[i - 1].first) * 60.0 / raw[i - 1].second
                }
                Tempo(pair.first, pair.second, seconds)
            }
        }
        fun seconds(triple: JSONArray): Double {
            val beats = beat(triple); var low = 0; var high = tempos.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (tempos[mid].beat <= beats) low = mid + 1 else high = mid
            }
            val tempo = tempos[(low - 1).coerceAtLeast(0)]
            val time = tempo.seconds + (beats - tempo.beat) * 60.0 / tempo.bpm
            require(time.isFinite() && abs(time) <= 1_000_000_000.0) { "RPE 时间超出范围" }; return time
        }
    }
    private fun beat(array: JSONArray): Double {
        require(array.length() == 3) { "RPE 拍数必须是三元数组" }
        val values = (0..2).map { array.getDouble(it) }
        require(values.all { it.isFinite() } && values[2] > 0.0) { "RPE 拍数分母无效" }
        val value = values[0] + values[1] / values[2]
        require(value.isFinite() && abs(value) <= 1_000_000.0) { "RPE 拍数超出范围" }; return value
    }
    private fun boundedArray(root: JSONObject, key: String, max: Int): JSONArray =
        root.getJSONArray(key).also { require(it.length() in 1..max) { "谱面数组数量无效：" + key } }
    private fun rejectNonEmpty(root: JSONObject, key: String, label: String) {
        if (!root.has(key) || root.isNull(key)) return
        val array = root.optJSONArray(key)
        require(array != null && array.length() == 0) { label + "尚不支持" }
    }
    internal fun jsonObject(text: String): JSONObject {
        require(text.length <= MAX_JSON_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) {
            "Phira JSON 超过 8 MiB"
        }
        var depth = 0; var quoted = false; var escaped = false
        for (char in text) {
            if (quoted) {
                if (!escaped && char == '"') quoted = false
                if (!escaped && char == '\\') escaped = true else escaped = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> require(++depth <= 64) { "Phira JSON 嵌套超过 64 层" }
                '}', ']' -> depth--
            }
        }
        return JSONObject(text.removePrefix("\uFEFF"))
    }
    private fun rejectNonIdentityControl(root: JSONObject, key: String, field: String) {
        if (!root.has(key) || root.isNull(key)) return
        val array = root.getJSONArray(key)
        require(array.length() <= 2048) { "RPE 控制事件过多" }
        for (i in 0 until array.length()) require(abs(number(array.getJSONObject(i), field) - 1.0) < 1e-9) {
            "RPE 非默认音符控制尚不支持"
        }
    }
    private fun rejectNonIdentityEvents(root: JSONObject, key: String, identity: Double) {
        if (!root.has(key) || root.isNull(key)) return
        val array = root.getJSONArray(key); require(array.length() <= MAX_EVENTS) { "RPE 扩展事件过多" }
        for (i in 0 until array.length()) {
            val event = array.getJSONObject(i)
            require(abs(number(event, "start") - identity) < 1e-9 &&
                abs(number(event, "end") - identity) < 1e-9) { "RPE 非默认缩放/倾斜尚不支持" }
        }
    }
    private fun number(root: JSONObject, key: String, fallback: Double? = null): Double {
        val result = if (root.has(key) && !root.isNull(key)) root.getDouble(key)
            else fallback ?: error("谱面缺少字段：" + key)
        require(result.isFinite()) { "谱面数字无效：" + key }; return result
    }
}
