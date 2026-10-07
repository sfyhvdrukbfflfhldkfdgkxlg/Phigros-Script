package io.github.phiscript.assets
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipInputStream

internal data class PhiraImportData(val title: String, val level: String, val normalizedJson: String,
    val forceAspectRatio: Boolean = false, val aspectRatio: Double = 16.0 / 9.0)

/** Stream-only archive decoder; no archive paths are extracted. */
internal object PhiraImportDecoder {
    private const val MAX_INFLATED = 256L * 1024L * 1024L
    private const val MAX_RETAINED = 16 * 1024 * 1024
    private const val MAX_METADATA = 64 * 1024
    fun decode(input: InputStream, displayName: String): PhiraImportData {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(4)
        val signature = ByteArray(4); var read = 0
        while (read < 4) { val n = buffered.read(signature, read, 4 - read); if (n < 0) break; read += n }
        buffered.reset()
        val isZip = read == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte() &&
            signature[2] == 0x03.toByte() && signature[3] == 0x04.toByte()
        if (!isZip) return fromSource(readBounded(buffered, PhiraChartParser.MAX_JSON_BYTES), emptyMap(), displayName)
        val files = linkedMapOf<String, ByteArray>(); val names = HashSet<String>()
        var inflated = 0L; var retained = 0; var entryCount = 0
        ZipInputStream(buffered).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entryCount <= 2048) { "Phira 压缩包条目超过 2048" }
                val name = safeName(entry.name, entry.isDirectory); require(names.add(name)) { "Phira 压缩包有重复路径" }
                val lower = name.lowercase(Locale.ROOT); val base = lower.substringAfterLast('/')
                val metadata = base in setOf("info.yml", "info.yaml", "info.txt")
                val chart = base.endsWith(".json") || base.endsWith(".pec") || base.endsWith(".pbc") ||
                    base.endsWith(".txt") || base.endsWith(".bin") || base.endsWith(".dat") || !base.contains('.')
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
                        retained += n; require(retained <= MAX_RETAINED) { "Phira 包内谱面数据总量超过 16 MiB" }
                        output!!.write(buffer, 0, n)
                    }
                }
                if (capture) files[name] = output!!.toByteArray()
                zip.closeEntry()
            }
        }
        require(files.isNotEmpty()) { "Phira 包没有可读取谱面" }
        val metadataFiles = files.keys.filter { it.substringAfterLast('/').lowercase(Locale.ROOT) in
            setOf("info.yml", "info.yaml", "info.txt") }
        val yaml = metadataFiles.filter { it.endsWith(".yml", true) || it.endsWith(".yaml", true) }
        val chosenMetadata = (if (yaml.isNotEmpty()) yaml else metadataFiles).singleOrNull()
        require(metadataFiles.isEmpty() || chosenMetadata != null) { "包内有多个信息文件，无法唯一选择谱面" }
        val metadata = chosenMetadata?.let {
            val bytes = files.getValue(it); readMetadata(utf8(bytes) ?: bytes.toString(Charset.forName("GB18030")))
        } ?: emptyMap()
        val reference = metadata["chart"]?.takeIf { it.isNotBlank() }
        val chosenChart = if (reference != null) {
            val parent = chosenMetadata!!.substringBeforeLast('/', "")
            val path = safeName(if (parent.isBlank()) reference else parent + "/" + reference, false)
            require(files.containsKey(path)) { "Phira 信息文件指定的谱面不存在或后缀不可读取：" + reference }; path
        } else {
            val candidates = files.keys.filter { it !in metadataFiles && looksLikeChart(files.getValue(it), it) }
            require(candidates.size == 1) { "未找到唯一的 Phira 谱面，请在 info.yml 的 chart 中指定" }; candidates.single()
        }
        return fromSource(files.getValue(chosenChart), metadata, chosenChart)
    }
    private fun utf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) { null }
    private fun looksLikeChart(bytes: ByteArray, name: String): Boolean {
        if (name.endsWith(".pbc", true)) return true
        val decoded = utf8(bytes)
        if (decoded == null) return runCatching { PhiraPbcDecoder.toJson(bytes) }.isSuccess
        val text = decoded.removePrefix("\uFEFF").trimStart()
        if (text.startsWith('{')) return runCatching {
            val root = PhiraChartParser.jsonObject(text)
            root.has("phiscriptFormat") || root.has("judgeLineList") && (root.has("formatVersion") || root.has("BPMList"))
        }.getOrDefault(false)
        return text.lineSequence().firstOrNull()?.trim()?.toDoubleOrNull()?.isFinite() == true &&
            text.lineSequence().drop(1).any { it.trimStart().startsWith("bp ") || it.trimStart().startsWith("bp\t") }
    }
    private fun fromSource(bytes: ByteArray, metadata: Map<String, String>, displayName: String): PhiraImportData {
        val format = metadata["format"]?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() && it != "null" && it != "~" }
        require(format == null || format in setOf("pgr", "rpe", "pec", "pbc")) { "Phira info.format 无效" }
        val decoded = utf8(bytes)
        val source = when {
            format == "pbc" || displayName.endsWith(".pbc", true) -> PhiraPbcDecoder.toJson(bytes)
            decoded == null -> {
                require(format == null) { "指定的 Phira 文本谱面不是 UTF-8" }; PhiraPbcDecoder.toJson(bytes)
            }
            else -> {
                val text = decoded.removePrefix("\uFEFF").trimStart()
                if (format == "pec" || !text.startsWith('{')) PhiraPecDecoder.toJson(text) else text
            }
        }
        val root = PhiraChartParser.jsonObject(source); val meta = root.optJSONObject("META")
        val fallbackTitle = displayName.substringAfterLast('/').substringBeforeLast('.').trim().ifEmpty { "未命名谱面" }
        val title = metadata["name"]?.takeIf { it.isNotBlank() } ?: meta?.optString("name")?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val level = metadata["level"]?.takeIf { it.isNotBlank() } ?: meta?.optString("level")?.takeIf { it.isNotBlank() } ?: "未知难度"
        require(title.length <= 200 && title.none { it.isISOControl() }) { "Phira 曲名长度或字符无效" }
        require(level.length <= 80 && level.none { it.isISOControl() }) { "Phira 难度文字长度或字符无效" }
        val offset = metadata["offset"]?.toDoubleOrNull() ?: run {
            require(metadata["offset"].isNullOrBlank()) { "Phira info.offset 不是数字" }; 0.0
        }
        val aspect = metadata["aspectratio"]?.let {
            it.toDoubleOrNull()?.also { value -> require(value.isFinite() && value in 0.25..8.0) {
                "Phira aspectRatio 必须在 0.25～8 之间"
            } } ?: error("Phira aspectRatio 不是数字")
        } ?: 16.0 / 9.0
        val force = when (metadata["forceaspectratio"]?.lowercase(Locale.ROOT)) {
            null, "false" -> false; "true" -> true; else -> error("Phira forceAspectRatio 必须为 true/false")
        }
        return PhiraImportData(title.trim(), level.trim(), PhiraChartParser.normalizeJson(source, offset), force, aspect)
    }
    internal fun readMetadata(text: String): Map<String, String> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_METADATA) { "Phira 信息文件过大" }
        val wanted = setOf("name", "level", "chart", "offset", "aspectratio", "forceaspectratio", "format")
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
        require(name.isNotEmpty() && name.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "Phira 包包含不安全路径" }
        return name
    }
    internal fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024); var total = 0
        while (true) {
            val n = input.read(buffer); if (n < 0) break
            total += n; require(total <= limit) { "Phira 文件超过大小限制" }; output.write(buffer, 0, n)
        }; return output.toByteArray()
    }
}
