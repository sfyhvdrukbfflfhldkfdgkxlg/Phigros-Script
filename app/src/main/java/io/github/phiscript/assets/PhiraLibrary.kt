package io.github.phiscript.assets

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import io.github.phiscript.AppSettings
import io.github.phiscript.engine.Chart
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class PhiraEntry(val id: String, val title: String, val level: String, val aliases: List<String> = emptyList(),
    val aspectRatio: Double = 16.0 / 9.0, val forceAspectRatio: Boolean = false)

internal fun <T> mergePhiraImports(old: List<T>, incoming: List<T>,
    identity: (T) -> Pair<String, String>, fingerprint: (T) -> String): List<T> {
    val unique = linkedMapOf<Pair<String, String>, T>()
    for (record in incoming) {
        val key = identity(record); val previous = unique[key]
        require(previous == null || fingerprint(previous) == fingerprint(record)) {
            "同批 Phira 文件含名称和难度相同的不同谱面，无法唯一选择"
        }; unique[key] = record
    }
    return old.filter { identity(it) !in unique } + unique.values
}

class PhiraLibrary private constructor(private val context: Context, private val records: List<Record>) {
    private val settings = AppSettings(context)
    val entries: List<PhiraEntry> get() = records.map { record ->
        val alias = settings.alias(record.id)
        PhiraEntry(record.id, record.title, record.level, if (alias.isBlank()) emptyList() else listOf(alias),
            forceAspectRatio = record.forceAspectRatio)
    }
    fun hasChart(id: String, level: String) = records.any { it.id == id && it.level == level }
    fun load(id: String, level: String): Chart {
        require(hasChart(id, level)) { "Phira 谱面名称/难度已变化，请重新识别" }; return load(id)
    }
    @Synchronized fun load(id: String): Chart {
        val record = records.singleOrNull { it.id == id } ?: error("未找到唯一的 Phira 谱面")
        val file = File(chartDirectory(context), record.id + ".json")
        require(file.isFile && file.length() == record.bytes.toLong() &&
            record.bytes in 1..PhiraChartParser.MAX_JSON_BYTES) { "Phira 导入谱面已丢失或变化，请重新导入" }
        val bytes = file.inputStream().use { PhiraImportDecoder.readBounded(it, PhiraChartParser.MAX_JSON_BYTES) }
        require(sha256(bytes) == record.sha256) { "Phira 导入谱面校验失败，请重新导入" }
        return Chart.parse(bytes.toString(Charsets.UTF_8))
    }
    private data class Record(val id: String, val title: String, val level: String, val sha256: String,
        val bytes: Int, val forceAspectRatio: Boolean = false)
    companion object {
        private const val MAX_INDEX_BYTES = 256 * 1024
        private const val MAX_LIBRARY_BYTES = 128L * 1024L * 1024L
        private val HEX = Regex("^[0-9a-f]{64}$")
        @Synchronized fun importFiles(context: Context, uris: List<Uri>, progress: (String) -> Unit): PhiraLibrary {
            val app = context.applicationContext
            require(uris.size in 1..16) { "一次最多导入 16 个 Phira 谱面文件" }
            val old = try { restore(app)?.records.orEmpty() } catch (_: Exception) {
                progress("旧 Phira 索引校验未通过；将验证新的导入文件后保存"); emptyList()
            }
            val imported = ArrayList<Pair<Record, ByteArray>>()
            for ((index, uri) in uris.withIndex()) {
                val display = displayName(app, uri)
                progress("解析 Phira 谱面 " + (index + 1) + "/" + uris.size + "：" + display)
                val data = app.contentResolver.openInputStream(uri)?.use { PhiraImportDecoder.decode(it, display) }
                    ?: error("无法打开所选 Phira 文档")
                val bytes = data.normalizedJson.toByteArray(Charsets.UTF_8); val digest = sha256(bytes)
                val id = sha256((data.title + "\u0000" + data.level + "\u0000" + digest +
                    "\u0000" + data.forceAspectRatio).toByteArray(Charsets.UTF_8))
                imported.add(Record(id, data.title, data.level, digest, bytes.size, data.forceAspectRatio) to bytes)
            }
            val merged = mergePhiraImports(old, imported.map { it.first }, { it.title to it.level }, { it.id })
            require(merged.size <= 64) { "Phira 谱库最多 64 个谱面" }
            require(merged.sumOf { it.bytes.toLong() } <= MAX_LIBRARY_BYTES) { "Phira 谱库 JSON 总量超过 128 MiB" }
            val directory = chartDirectory(app).apply { check(isDirectory || mkdirs()) }
            val created = ArrayList<File>(); var committed = false
            try {
                for ((record, bytes) in imported) {
                    val destination = File(directory, record.id + ".json")
                    if (destination.isFile) {
                        val existing = destination.inputStream().use {
                            PhiraImportDecoder.readBounded(it, PhiraChartParser.MAX_JSON_BYTES)
                        }
                        require(sha256(existing) == record.sha256) { "已存在的 Phira 谱面校验失败" }; continue
                    }
                    val temp = File.createTempFile("phira-", ".tmp", directory)
                    try {
                        temp.outputStream().use { it.write(bytes); it.flush() }
                        check(temp.renameTo(destination)) { "无法保存 Phira 谱面" }; created.add(destination)
                    } finally { temp.delete() }
                }
                val json = JSONObject().put("version", 1).put("entries", JSONArray().apply {
                    merged.forEach { r -> put(JSONObject().put("id", r.id).put("title", r.title).put("level", r.level)
                        .put("sha256", r.sha256).put("bytes", r.bytes).put("forceAspectRatio", r.forceAspectRatio)) }
                }).toString().toByteArray(Charsets.UTF_8)
                require(json.size <= MAX_INDEX_BYTES)
                val index = indexFile(app); val stream = index.startWrite()
                try { stream.write(json); index.finishWrite(stream) }
                catch (e: Exception) { index.failWrite(stream); throw e }
                committed = true
                progress("Phira 谱库已保存，共 " + merged.size + " 个谱面")
                return PhiraLibrary(app, merged)
            } finally { if (!committed) created.forEach { it.delete() } }
        }
        @Synchronized fun restore(context: Context): PhiraLibrary? {
            val app = context.applicationContext; val atomic = indexFile(app)
            if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
            val bytes = atomic.openRead().use { PhiraImportDecoder.readBounded(it, MAX_INDEX_BYTES) }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.getInt("version") == 1) { "Phira 谱库版本不支持" }
            val array = json.getJSONArray("entries")
            require(array.length() in 1..64) { "Phira 谱库条目数量无效" }
            val records = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val r = Record(item.getString("id"), item.getString("title"), item.getString("level"),
                    item.getString("sha256"), item.getInt("bytes"), item.optBoolean("forceAspectRatio", false))
                require(HEX.matches(r.id) && HEX.matches(r.sha256) && r.title.isNotBlank() && r.title.length <= 200 &&
                    r.level.isNotBlank() && r.level.length <= 80 && r.bytes in 1..PhiraChartParser.MAX_JSON_BYTES) {
                    "Phira 谱库元数据无效"
                }; r
            }
            require(records.map { it.id }.distinct().size == records.size) { "Phira 谱库 ID 重复" }
            require(records.sumOf { it.bytes.toLong() } <= MAX_LIBRARY_BYTES) { "Phira 谱库过大" }
            return PhiraLibrary(app, records)
        }
        private fun chartDirectory(context: Context) = File(context.filesDir, "phira/charts")
        private fun indexFile(context: Context): AtomicFile {
            val dir = File(context.filesDir, "phira").apply { check(isDirectory || mkdirs()) }
            return AtomicFile(File(dir, "index.json"))
        }
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun displayName(context: Context, uri: Uri): String =
            runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } }.getOrNull()?.take(200) ?: "所选文档"
    }
}
