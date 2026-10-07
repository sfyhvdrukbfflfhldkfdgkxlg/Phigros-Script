package io.github.phiscript.assets
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import kotlin.math.abs

/** Bounded decoder of the official unversioned prpr PBC layout (prpr/src/bin.rs). */
internal object PhiraPbcDecoder {
    fun toJson(bytes: ByteArray): String {
        require(bytes.size in 1..PhiraChartParser.MAX_JSON_BYTES) { "PBC 文件大小无效" }
        return Reader(bytes).chart().toString()
    }
    private class Reader(bytes: ByteArray) {
        private val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private var clockMs = 0L; private var keyframeCount = 0; private var noteCount = 0
        private fun byte(): Int { require(input.hasRemaining()) { "PBC 数据截断" }; return input.get().toInt() and 255 }
        private fun bool(): Boolean { val value = byte(); require(value in 0..1) { "PBC 布尔字段无效" }; return value == 1 }
        private fun float(): Double {
            require(input.remaining() >= 4) { "PBC 数据截断" }
            val value = input.float.toDouble(); require(value.isFinite()) { "PBC 包含非有限数值" }; return value
        }
        private fun uleb(limit: Long): Long {
            var value = 0L
            for (index in 0 until 9) {
                val next = byte(); val part = (next and 127).toLong()
                require(part <= (limit ushr (index * 7))) { "PBC ULEB 字段超出范围" }
                value = value or (part shl (index * 7)); require(value <= limit) { "PBC ULEB 字段超出范围" }
                if (next and 128 == 0) return value
            }
            error("PBC ULEB 字段过长")
        }
        private fun time(): Double { clockMs += uleb(4_294_967_295L - clockMs); return clockMs / 1000.0 }
        private fun text(): String {
            val count = uleb(64L * 1024L).toInt(); require(input.remaining() >= count) { "PBC 字符串截断" }
            val bytes = ByteArray(count); input.get(bytes)
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }
        private fun tween(): JSONObject {
            val tag = byte()
            return when (tag and 192) {
                0 -> { require(tag <= 32) { "PBC 缓动类型无效" }; JSONObject().put("id", tag) }
                128 -> {
                    val id = tag and 127; require(id <= 32) { "PBC 缓动类型无效" }
                    JSONObject().put("id", id).put("left", float()).put("right", float())
                }
                192 -> JSONObject().put("id", 2).put("bezier", JSONArray().put(float()).put(float()).put(float()).put(float()))
                else -> error("PBC 缓动类型无效")
            }
        }
        private fun anim(kind: Int = 0, retain: Boolean = true): JSONArray {
            val layers = JSONArray(); var layerCount = 0; var firstEmpty = false
            while (true) {
                val tag = byte()
                if (tag == 0) { require(layerCount > 0) { "PBC 缺少动画字段" }; return if (firstEmpty) JSONArray() else layers }
                require(tag in 1..2 && ++layerCount <= 64) { "PBC 动画层无效" }
                require(!(tag == 1 && layerCount > 1 && !firstEmpty)) { "PBC 动画包含非首空层，Phira 无法播放" }
                if (tag == 1 && layerCount == 1) firstEmpty = true
                val frames = JSONArray()
                if (tag == 2) {
                    clockMs = 0; val count = uleb(100_000).toInt()
                    require(count > 0) { "PBC 动画关键帧为空" }; keyframeCount += count
                    require(keyframeCount <= 300_000) { "PBC 动画关键帧过多" }
                    repeat(count) {
                        val at = time()
                        val value: Any = when (kind) {
                            0 -> float()
                            1 -> JSONArray().put(byte()).put(byte()).put(byte()).put(byte())
                            2 -> text()
                            else -> error("PBC 内部字段类型无效")
                        }
                        val easing = tween()
                        if (retain) frames.put(JSONObject().put("time", at).put("value", value).put("tween", easing))
                    }
                }
                if (retain && frames.length() > 0) layers.put(frames)
            }
        }
        private fun objectData(): JSONObject {
            anim(retain = false); anim(retain = false); anim(retain = false)
            val rotation = anim(); val x = anim(); val y = anim()
            return JSONObject().put("rotation", rotation).put("x", x).put("y", y)
        }
        private fun note(): JSONObject {
            require(++noteCount <= 100_000) { "PBC 音符过多" }
            val objectData = objectData(); val kind = byte(); require(kind in 0..3) { "PBC 音符类型无效" }
            val end = if (kind == 1) float() else null; val endHeight = if (kind == 1) float() else null
            val start = time(); val height = float(); val speed = if (bool()) float() else 1.0
            val above = bool(); val fake = bool()
            require(start >= 0.0 && start <= 7200.0 && (end == null || end >= start && end <= 7200.0)) { "PBC 音符时间无效" }
            val type = when (kind) { 0 -> 1; 1 -> 3; 2 -> 4; else -> 2 }
            return JSONObject().put("object", objectData).put("type", type).put("time", start)
                .put("endTime", end ?: start).put("height", height).put("endHeight", endHeight ?: height)
                .put("speed", speed).put("above", above).put("fake", fake)
        }
        private fun line(): JSONObject {
            clockMs = 0; val objectData = objectData()
            when (byte()) {
                0 -> Unit
                1 -> text()
                2 -> anim(kind = 2, retain = false)
                3 -> anim(retain = false)
                else -> error("PBC 判定线类型不受当前官方格式支持")
            }
            anim(retain = false); val notes = JSONArray()
            repeat(uleb(100_000).toInt()) { notes.put(note()) }
            anim(kind = 1, retain = false)
            val parent = uleb(4096).toInt() - 1; val flags = byte()
            require(flags and 252 == 0) { "PBC 判定线标志无效" }
            val attach = byte(); require(attach in 0..7 && byte() == 8) { "PBC 判定线控制字段无效" }
            repeat(4) { anim(retain = false) }; anim(retain = false)
            require(input.remaining() >= 4) { "PBC 数据截断" }; input.int
            return JSONObject().put("object", objectData).put("notes", notes).put("parent", parent)
                .put("rotWithParent", flags and 2 != 0).put("attachUI", attach)
        }
        fun chart(): JSONObject {
            val offset = float(); require(abs(offset) <= 3600.0) { "PBC 偏移无效" }
            val lines = JSONArray(); val count = uleb(4096).toInt(); require(count > 0) { "PBC 没有判定线" }
            repeat(count) { lines.put(line()) }; bool(); bool()
            require(!input.hasRemaining()) { "PBC 末尾有未知数据，可能是不同版本" }; require(noteCount > 0) { "PBC 没有音符" }
            return JSONObject().put("phiscriptFormat", 1).put("offset", offset).put("lines", lines)
        }
    }
}
