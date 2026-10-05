package io.github.phiscript.assets

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Only reads the manifest root from bounded Android binary XML; never loads APK code. */
internal data class ApkManifestInfo(val packageName: String, val versionCode: Long, val split: String) {
    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024
        fun parse(bytes: ByteArray): ApkManifestInfo {
            require(bytes.size in 8..MAX_BYTES) { "APK 清单大小无效" }
            fun u16(at: Int): Int {
                require(at >= 0 && at <= bytes.size - 2) { "APK 清单已截断" }
                return (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
            }
            fun i32(at: Int): Int = u16(at) or (u16(at + 2) shl 16)
            require(u16(0) == 3 && u16(2) == 8 && i32(4) == bytes.size) { "不是 Android 二进制 APK 清单" }
            var strings: List<String>? = null
            var at = 8
            while (at < bytes.size) {
                val type = u16(at)
                val header = u16(at + 2)
                val size = i32(at + 4)
                require(header >= 8 && size >= header && size <= bytes.size - at) { "APK 清单块无效" }
                if (type == 1) {
                    require(strings == null && header >= 28) { "APK 清单字符串池无效" }
                    val count = i32(at + 8)
                    val styleCount = i32(at + 12)
                    val flags = i32(at + 16)
                    val start = i32(at + 20)
                    val styleStart = i32(at + 24)
                    require(count in 0..50_000 && styleCount in 0..50_000 &&
                        header.toLong() + (count.toLong() + styleCount) * 4 <= size &&
                        start.toLong() >= header.toLong() + (count.toLong() + styleCount) * 4 &&
                        start <= size && (styleStart == 0 || styleStart in start..size)) {
                        "APK 清单字符串索引无效"
                    }
                    val end = at + if (styleStart == 0) size else styleStart
                    var decodedCharacters = 0L
                    strings = List(count) { index ->
                        val offset = i32(at + header + index * 4)
                        require(offset >= 0 && offset < end - at - start) { "APK 清单字符串偏移无效" }
                        var pos = at + start + offset
                        fun byte(): Int {
                            require(pos < end) { "APK 清单字符串已截断" }
                            return bytes[pos++].toInt() and 255
                        }
                        fun length8(): Int {
                            val first = byte()
                            return if (first and 0x80 != 0) ((first and 0x7f) shl 8) or byte() else first
                        }
                        fun length16(): Int {
                            require(pos <= end - 2) { "APK 清单字符串已截断" }
                            val first = u16(pos); pos += 2
                            if (first and 0x8000 == 0) return first
                            require(pos <= end - 2) { "APK 清单字符串已截断" }
                            val second = u16(pos); pos += 2
                            return ((first and 0x7fff) shl 16) or second
                        }
                        val utf8 = flags and 0x100 != 0
                        val characterLength = if (utf8) length8() else length16()
                        decodedCharacters += characterLength
                        require(decodedCharacters <= MAX_BYTES) { "APK 清单字符串总量过大" }
                        val length = if (utf8) length8().toLong() else characterLength.toLong() * 2
                        val terminator = if (utf8) 1 else 2
                        require(length <= end.toLong() - pos - terminator) { "APK 清单字符串越界" }
                        val charset = if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE
                        val value = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, pos, length.toInt())).toString()
                        require(value.length == characterLength) { "APK 清单字符串长度不一致" }
                        pos += length.toInt()
                        require(byte() == 0 && (utf8 || byte() == 0)) { "APK 清单字符串未结束" }
                        value
                    }
                } else if (type == 0x0102) {
                    val pool = strings ?: error("APK 清单缺少字符串池")
                    fun string(index: Int): String {
                        require(index in pool.indices) { "APK 清单字符串引用无效" }
                        return pool[index]
                    }
                    require(header >= 16 && size >= header + 20) { "APK 清单标签已截断" }
                    val ext = at + header
                    require(string(i32(ext + 4)) == "manifest") { "APK 清单根标签无效" }
                    val attrStart = u16(ext + 8)
                    val attrSize = u16(ext + 10)
                    val count = u16(ext + 12)
                    require(attrStart >= 20 && attrSize >= 20 &&
                        header.toLong() + attrStart + count.toLong() * attrSize <= size) {
                        "APK 清单属性越界"
                    }
                    val values = linkedMapOf<Pair<String, String>, Pair<Int, Int>>()
                    repeat(count) { index ->
                        val row = ext + attrStart + index * attrSize
                        val nsIndex = i32(row)
                        val ns = if (nsIndex == -1) "" else string(nsIndex)
                        val name = string(i32(row + 4))
                        require(u16(row + 12) == 8) { "APK 清单属性值无效" }
                        val dataType = bytes[row + 15].toInt() and 255
                        val value = i32(row + 16)
                        require(values.put(ns to name, dataType to value) == null) { "APK 清单属性重复" }
                    }
                    fun text(name: String, required: Boolean): String {
                        val value = values["" to name]
                        if (value == null) { require(!required) { "APK 清单缺少 " + name }; return "" }
                        require(value.first == 3) { "APK 清单文本属性无效" }
                        return string(value.second)
                    }
                    fun version(name: String, required: Boolean): Long {
                        val value = values["http://schemas.android.com/apk/res/android" to name]
                        if (value == null) { require(!required) { "APK 清单缺少版本号" }; return 0L }
                        require(value.first == 0x10 || value.first == 0x11) { "APK 清单版本号无效" }
                        return value.second.toLong() and 0xffffffffL
                    }
                    val major = version("versionCodeMajor", false)
                    require(major <= Int.MAX_VALUE) { "APK 版本号超出范围" }
                    return ApkManifestInfo(text("package", true), (major shl 32) or version("versionCode", true),
                        text("split", false))
                }
                at += size
            }
            error("APK 清单缺少 manifest 标签")
        }
    }
}
