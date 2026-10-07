package io.github.phiscript.assets
import io.github.phiscript.engine.Chart
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

object PhiraChartParser {
    const val MAX_JSON_BYTES = 8 * 1024 * 1024
    private const val MAX_EVENTS = 100_000
    fun parse(text: String, infoOffsetSeconds: Double = 0.0): Chart = Chart.parse(normalizeJson(text, infoOffsetSeconds))
    fun normalizeJson(text: String, infoOffsetSeconds: Double = 0.0): String {
        require(infoOffsetSeconds.isFinite() && abs(infoOffsetSeconds) <= 3600.0) { "Phira 信息偏移无效" }
        val root = jsonObject(text)
        val normalized = when {
            root.has("phiscriptFormat") -> root
            root.has("formatVersion") -> normalizePgr(root)
            root.has("BPMList") && root.has("judgeLineList") -> RpeNativeNormalizer.normalize(root)
            else -> error("未识别的 Phira JSON 谱面格式")
        }
        val offset = number(normalized, "offset", 0.0) + infoOffsetSeconds
        require(offset.isFinite() && abs(offset) <= 3600.0) { "谱面总偏移无效" }
        normalized.put("offset", offset)
        val result = normalized.toString()
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "转换后的谱面超过 8 MiB" }
        val chart = Chart.parse(result)
        require(chart.firstNoteSeconds >= 0.0 && chart.durationSeconds <= 7200.0) { "音符时间不在 0～7200 秒内" }
        return result
    }
    private fun normalizePgr(root: JSONObject): JSONObject {
        val version = root.getInt("formatVersion")
        require(version == 1 || version == 3) { "Phira PGR 仅支持 formatVersion 1/3" }
        val lines = root.getJSONArray("judgeLineList")
        require(lines.length() in 1..4096) { "PGR 判定线数量无效" }
        for (i in 0 until lines.length()) {
            val line = lines.getJSONObject(i); val moves = line.optJSONArray("judgeLineMoveEvents") ?: JSONArray()
            require(moves.length() <= MAX_EVENTS) { "判定线事件过多" }
            if (version == 1) for (j in 0 until moves.length()) {
                val event = moves.getJSONObject(j); val start = number(event, "start"); val end = number(event, "end")
                event.put("start", (start - start % 1000.0) / 1000.0 / 880.0)
                event.put("end", (end - end % 1000.0) / 1000.0 / 880.0)
                event.put("start2", start % 1000.0 / 520.0); event.put("end2", end % 1000.0 / 520.0)
            }
            for (key in listOf("notesAbove", "notesBelow")) {
                val notes = line.optJSONArray(key) ?: JSONArray()
                require(notes.length() <= MAX_EVENTS) { "音符数量过多" }
                for (j in 0 until notes.length()) {
                    val note = notes.getJSONObject(j); if (!note.has("floorPosition")) note.put("floorPosition", 0.0)
                }
            }
        }
        root.put("formatVersion", 3); return root
    }
    internal fun jsonObject(text: String): JSONObject {
        require(text.length <= MAX_JSON_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "Phira JSON 超过 8 MiB" }
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
    private fun number(root: JSONObject, key: String, fallback: Double? = null): Double {
        val result = if (root.has(key) && !root.isNull(key)) root.getDouble(key) else fallback ?: error("谱面缺少字段：" + key)
        require(result.isFinite()) { "谱面数字无效：" + key }; return result
    }
}
