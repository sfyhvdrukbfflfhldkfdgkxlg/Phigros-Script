package io.github.phiscript.assets

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

internal data class PhiraImportData(val title: String, val level: String, val normalizedJson: String,
    val forceAspectRatio: Boolean = false)

/** Stream-only archive decoder; no archive paths are extracted. */
internal object PhiraImportDecoder {
    private const val MAX_INFLATED = 256L * 1024L * 1024L
    private const val MAX_RETAINED = 16 * 1024 * 1024
    private const val MAX_METADATA = 64 * 1024
    fun decode(input: InputStream, displayName: String): PhiraImportData {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(4)
        val signature = ByteArray(4); var read = 0
        while (read < 4) {
            val n = buffered.read(signature, read, 4 - read); if (n < 0) break; read += n
        }
        buffered.reset()
        val isZip = read == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte() &&
            signature[2] == 0x03.toByte() && signature[3] == 0x04.toByte()
        if (!isZip) return fromJson(readBounded(buffered, PhiraChartParser.MAX_JSON_BYTES)
            .toString(Charsets.UTF_8), emptyMap(), displayName)
        val files = linkedMapOf<String, ByteArray>(); val names = HashSet<String>()
        var inflated = 0L; var retained = 0; var entryCount = 0
        ZipInputStream(buffered).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entryCount <= 2048) { "Phira 压缩包条目超过 2048" }
                val name = safeName(entry.name, entry.isDirectory)
                require(names.add(name)) { "Phira 压缩包有重复路径" }
                val lower = name.lowercase(Locale.ROOT)
                val metadata = lower.substringAfterLast('/') in setOf("info.yml", "info.yaml", "info.txt")
                val chart = lower.endsWith(".json") || lower.endsWith(".pec") || lower.endsWith(".pbc")
                val limit = if (metadata) MAX_METADATA else PhiraChartParser.MAX_JSON_BYTES
                val capture = !entry.isDirectory && (metadata || chart)
                val output = if (capture) ByteArrayOutputStream() else null
                var entryBytes = 0L; val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = zip.read(buffer); if (n < 0) break
                    entryBytes += n; inflated += n
                    require(inflated <= MAX_INFLATED) { "Phira 包解压后超过 256 MiB" }
                    if (capture) {
                        require(entryBytes <= limit) { "Phira 谱面或信息文件过大" }
                        retained += n
                        require(retained <= MAX_RETAINED) { "Phira 包内谱面 JSON 总量超过 16 MiB" }
                        output!!.write(buffer, 0, n)
                    }
                }
                if (capture) files[name] = output!!.toByteArray()
                zip.closeEntry()
            }
        }
        require(files.isNotEmpty()) { "Phira 包没有 JSON 谱面" }
        val metadataFiles = files.keys.filter { it.substringAfterLast('/').lowercase(Locale.ROOT) in
            setOf("info.yml", "info.yaml", "info.txt") }
        val yaml = metadataFiles.filter { it.endsWith(".yml", true) || it.endsWith(".yaml", true) }
        val chosenMetadata = (if (yaml.isNotEmpty()) yaml else metadataFiles).singleOrNull()
        require(metadataFiles.isEmpty() || chosenMetadata != null) { "包内有多个信息文件，无法唯一选择谱面" }
        val metadata = chosenMetadata?.let { readMetadata(files.getValue(it).toString(Charsets.UTF_8)) } ?: emptyMap()
        val reference = metadata["chart"]?.takeIf { it.isNotBlank() }
        val chosenChart = if (reference != null) {
            val parent = chosenMetadata!!.substringBeforeLast('/', "")
            val path = safeName(if (parent.isBlank()) reference else parent + "/" + reference, false)
            require(files.containsKey(path)) { "Phira 信息文件指定的谱面不存在：" + reference }; path
        } else {
            val json = files.keys.filter { it.endsWith(".json", true) }.filter { key ->
                runCatching {
                    val root = PhiraChartParser.jsonObject(files.getValue(key).toString(Charsets.UTF_8))
                    root.has("judgeLineList") && (root.has("formatVersion") || root.has("BPMList"))
                }.getOrDefault(false)
            }
            require(json.size == 1) { "未找到唯一的 Phira JSON 谱面；PEC/PBC 暂不支持" }; json.single()
        }
        require(chosenChart.endsWith(".json", true)) { "Phira PEC/PBC 暂不支持，请使用 JSON 谱面" }
        return fromJson(files.getValue(chosenChart).toString(Charsets.UTF_8), metadata, displayName)
    }
    private fun fromJson(text: String, metadata: Map<String, String>, displayName: String): PhiraImportData {
        val root = runCatching { PhiraChartParser.jsonObject(text) }.getOrElse {
            error("不是有效的 Phira JSON：" + displayName + "；PEC/PBC 暂不支持")
        }
        val meta = root.optJSONObject("META")
        val title = metadata["name"]?.takeIf { it.isNotBlank() } ?: meta?.optString("name")?.takeIf { it.isNotBlank() }
        val level = metadata["level"]?.takeIf { it.isNotBlank() } ?: meta?.optString("level")?.takeIf { it.isNotBlank() }
        require(title != null && title.length <= 200 && title.none { it.isISOControl() }) {
            "Phira 谱面缺少有效名称；请导出含 info.yml 的 ZIP/PEZ，或填写 JSON 的 META.name"
        }
        require(level != null && level.length <= 80 && level.none { it.isISOControl() }) {
            "Phira 谱面缺少难度文字；请填写 info.yml 的 level 或 JSON 的 META.level"
        }
        val offset = metadata["offset"]?.toDoubleOrNull() ?: run {
            require(metadata["offset"].isNullOrBlank()) { "Phira info.offset 不是数字" }; 0.0
        }
        metadata["aspectratio"]?.let {
            val value = it.toDoubleOrNull()
            require(value != null && value.isFinite() && kotlin.math.abs(value - 16.0 / 9.0) < 0.002) {
                "当前 Phira 谱面模式仅支持 16:9 aspectRatio"
            }
        }
        val force = when (metadata["forceaspectratio"]?.lowercase(Locale.ROOT)) {
            null, "false" -> false; "true" -> true; else -> error("Phira forceAspectRatio 必须为 true/false")
        }
        return PhiraImportData(title.trim(), level.trim(), PhiraChartParser.normalizeJson(text, offset), force)
    }
    internal fun readMetadata(text: String): Map<String, String> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_METADATA) { "Phira 信息文件过大" }
        val wanted = setOf("name", "level", "chart", "offset", "aspectratio", "forceaspectratio")
        val result = linkedMapOf<String, String>()
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            if (raw.isBlank() || raw.trimStart().startsWith("#") || raw.first().isWhitespace()) continue
            val colon = raw.indexOf(':'); if (colon < 0) continue
            val key = raw.substring(0, colon).trim().lowercase(Locale.ROOT)
            if (key !in wanted) continue
            require(!result.containsKey(key)) { "Phira 信息文件重复字段：" + key }
            val value = raw.substring(colon + 1).trim()
            require(value !in setOf("|", ">", "|-", ">-", "|+", ">+")) { "Phira 信息字段暂不支持多行 YAML" }
            result[key] = when {
                value.startsWith("\"") -> {
                    val end = quoteEnd(value)
                    require(end >= 1 && value.substring(end + 1).trim().let { it.isEmpty() || it.startsWith("#") }) {
                        "Phira 信息字符串格式无效"
                    }
                    JSONObject("{\"value\":" + value.substring(0, end + 1) + "}").getString("value")
                }
                value.startsWith("'") -> {
                    var end = 1; val decoded = StringBuilder(); var closed = false
                    while (end < value.length) {
                        if (value[end] == '\'') {
                            if (end + 1 < value.length && value[end + 1] == '\'') { decoded.append('\''); end += 2 }
                            else { closed = true; end++; break }
                        } else decoded.append(value[end++])
                    }
                    require(closed && value.substring(end).trim().let { it.isEmpty() || it.startsWith("#") }) {
                        "Phira 信息字符串格式无效"
                    }; decoded.toString()
                }
                else -> value.substringBefore(" #").trim()
            }
        }
        return result
    }
    private fun quoteEnd(value: String): Int {
        var escaped = false
        for (i in 1 until value.length) {
            if (!escaped && value[i] == '"') return i
            if (!escaped && value[i] == '\\') escaped = true else escaped = false
        }; return -1
    }
    internal fun safeName(raw: String, directory: Boolean = false): String {
        require(raw.length in 1..512 && !raw.startsWith('/') && !raw.contains('\\') &&
            !Regex("^[A-Za-z]:").containsMatchIn(raw) && raw.none { it.isISOControl() }) { "Phira 包包含不安全路径" }
        val name = raw.removePrefix("./").let { if (directory) it.removeSuffix("/") else it }
        require(name.isNotEmpty() && name.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
            "Phira 包包含不安全路径"
        }; return name
    }
    internal fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024); var total = 0
        while (true) {
            val n = input.read(buffer); if (n < 0) break
            total += n; require(total <= limit) { "Phira 文件超过大小限制" }; output.write(buffer, 0, n)
        }; return output.toByteArray()
    }
}
