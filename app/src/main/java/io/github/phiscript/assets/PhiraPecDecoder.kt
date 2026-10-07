package io.github.phiscript.assets
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** PEC semantics follow TeamFlos/phira prpr/src/parse/pec.rs. */
internal object PhiraPecDecoder {
    private const val MAX_COMMANDS = 100_000
    private val tweenMap = intArrayOf(2, 2, 4, 3, 7, 6, 5, 8, 10, 9, 13, 12, 11, 14,
        16, 15, 19, 18, 22, 21, 25, 24, 23, 26, 28, 27, 31, 30, 32, 29)
    private data class Event(val start: Double, val end: Double, val value: Double, val tween: Int)
    private class Line {
        val x = ArrayList<Event>(); val y = ArrayList<Event>(); val rotation = ArrayList<Event>()
        val notes = ArrayList<JSONObject>()
    }
    private class Tokens(text: String) {
        private val values = text.trim().split(Regex("\\s+")); private var index = 0
        fun next(): String { require(index < values.size) { "PEC 命令参数不足" }; return values[index++] }
        fun number(): Double = next().toDoubleOrNull()?.also {
            require(it.isFinite() && abs(it) <= 1e9) { "PEC 数值无效" }
        } ?: error("PEC 参数不是数字")
        fun integer(): Int = next().toIntOrNull() ?: error("PEC 参数不是整数")
        fun hasNext() = index < values.size
        fun end() { require(!hasNext()) { "PEC 命令有多余参数" } }
    }
    fun toJson(source: String): String {
        require(source.toByteArray(Charsets.UTF_8).size <= PhiraChartParser.MAX_JSON_BYTES) { "PEC 谱面超过 8 MiB" }
        val records = source.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(records.size in 2..MAX_COMMANDS + 1) { "PEC 命令数量无效" }
        val offset = records.first().toDoubleOrNull() ?: error("PEC 第一行必须是毫秒偏移")
        require(offset.isFinite() && abs(offset / 1000.0 - 0.15) <= 3600.0) { "PEC 偏移无效" }
        val bpms = ArrayList<Pair<Double, Double>>(); val bpmTimes = ArrayList<Double>(); var clockUsed = false
        fun seconds(beat: Double): Double {
            require(bpms.isNotEmpty()) { "PEC 缺少 bp BPM 命令" }; clockUsed = true
            var low = 0; var high = bpms.lastIndex
            while (low < high) {
                val middle = (low + high + 1) ushr 1
                if (bpms[middle].first <= beat) low = middle else high = middle - 1
            }
            return bpmTimes[low] + (beat - bpms[low].first) * 60.0 / bpms[low].second
        }
        val lines = ArrayList<Line>()
        fun line(id: Int): Line {
            require(id in 0..4095) { "PEC 判定线编号超出范围" }
            while (lines.size <= id) lines.add(Line())
            return lines[id]
        }
        var lastNote: JSONObject? = null; var noteCount = 0
        for ((recordIndex, text) in records.drop(1).withIndex()) {
            try {
                val token = Tokens(text)
                when (val command = token.next()) {
                    "bp" -> {
                        require(!clockUsed && bpms.size < 10_000) { "PEC bp 必须在所有音符和判定线命令之前" }
                        val beat = token.number(); val bpm = token.number()
                        require(bpm > 0.0 && bpm <= 10000.0 && (bpms.isEmpty() || beat >= bpms.last().first)) {
                            "PEC BPM 顺序或数值无效"
                        }
                        if (bpms.isNotEmpty() && beat == bpms.last().first) bpms[bpms.lastIndex] = beat to bpm
                        else {
                            bpmTimes.add(if (bpms.isEmpty()) 0.0 else bpmTimes.last() +
                                (beat - bpms.last().first) * 60.0 / bpms.last().second)
                            bpms.add(beat to bpm)
                        }
                    }
                    "n1", "n2", "n3", "n4" -> {
                        val target = line(token.integer()); val start = seconds(token.number())
                        val end = if (command == "n2") seconds(token.number()) else start
                        require(start >= 0.0 && end >= start && end <= 7200.0) { "PEC 音符时间无效" }
                        val x = token.number() / 1024.0; val above = token.integer(); val fake = token.integer()
                        require(above >= 0 && fake in 0..1 && ++noteCount <= MAX_COMMANDS) { "PEC 音符字段无效" }
                        val type = when (command) { "n1" -> 1; "n2" -> 3; "n3" -> 4; else -> 2 }
                        val note = JSONObject().put("type", type).put("time", start).put("endTime", end)
                            .put("above", above == 1).put("fake", fake != 0).put("speed", 1.0)
                            .put("object", JSONObject().put("x", fixed(x)))
                        target.notes.add(note); lastNote = note
                        while (token.hasNext()) when (token.next()) {
                            "#" -> note.put("speed", token.number())
                            "&" -> token.number()
                            else -> error("PEC 音符附加命令无效")
                        }
                    }
                    "#" -> { require(lastNote != null) { "PEC # 前没有音符" }; lastNote!!.put("speed", token.number()) }
                    "&" -> { require(lastNote != null) { "PEC & 前没有音符" }; token.number() }
                    "cv", "cp", "cd", "ca", "cm", "cr", "cf" -> {
                        val target = line(token.integer()); val start = seconds(token.number())
                        when (command) {
                            "cv", "ca" -> token.number()
                            "cp" -> {
                                target.x.add(Event(start, start, token.number() / 1024.0 - 1.0, 0))
                                target.y.add(Event(start, start, token.number() / 700.0 - 1.0, 0))
                            }
                            "cd" -> target.rotation.add(Event(start, start, -token.number(), 0))
                            "cm" -> {
                                val end = seconds(token.number()); val x = token.number() / 1024.0 - 1.0
                                val y = token.number() / 700.0 - 1.0; val easing = token.integer()
                                require(end >= start && easing >= 0) { "PEC 移动事件无效" }
                                val tween = tweenMap.getOrElse(easing) { 2 }
                                target.x.add(Event(start, end, x, tween)); target.y.add(Event(start, end, y, tween))
                            }
                            "cr" -> {
                                val end = seconds(token.number()); val rotation = -token.number(); val easing = token.integer()
                                require(end >= start && easing >= 0) { "PEC 旋转事件无效" }
                                target.rotation.add(Event(start, end, rotation, tweenMap.getOrElse(easing) { 2 }))
                            }
                            "cf" -> { val end = seconds(token.number()); require(end >= start) { "PEC 透明度事件无效" }; token.number() }
                        }
                    }
                    else -> error("PEC 未知命令：" + command)
                }
                token.end()
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("PEC 第 " + (recordIndex + 2) + " 行：" + error.message, error)
            } catch (error: IllegalStateException) {
                throw IllegalArgumentException("PEC 第 " + (recordIndex + 2) + " 行：" + error.message, error)
            }
        }
        require(noteCount > 0 && bpms.isNotEmpty()) { "PEC 谱面没有音符或 BPM" }
        val output = JSONArray()
        for (target in lines) output.put(JSONObject().put("parent", -1).put("rotWithParent", false)
            .put("object", JSONObject().put("x", events(target.x)).put("y", events(target.y))
                .put("rotation", events(target.rotation))).put("notes", JSONArray(target.notes)))
        return JSONObject().put("phiscriptFormat", 1).put("offset", offset / 1000.0 - 0.15).put("lines", output).toString()
    }
    private fun fixed(value: Double) = JSONArray().put(JSONArray().put(JSONObject()
        .put("time", 0.0).put("value", value).put("tween", JSONObject().put("id", 0))))
    private fun events(source: List<Event>): JSONArray {
        if (source.isEmpty()) return JSONArray()
        val frames = JSONArray(); var lastEnd = Double.NEGATIVE_INFINITY; var value: Double? = null
        for (event in source.sortedWith(compareBy<Event> { it.end }.thenBy { it.start })) {
            val start = maxOf(event.start, lastEnd)
            if (start != event.end) {
                require(value != null) { "PEC 缓动事件前没有 cp/cd 初始值" }
                frames.put(JSONObject().put("time", start).put("value", value)
                    .put("tween", JSONObject().put("id", event.tween)))
            }
            frames.put(JSONObject().put("time", event.end).put("value", event.value).put("tween", JSONObject().put("id", 0)))
            value = event.value; lastEnd = event.end
        }
        return JSONArray().put(frames)
    }
}
