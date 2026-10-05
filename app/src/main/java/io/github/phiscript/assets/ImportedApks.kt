package io.github.phiscript.assets

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID

/** User-selected documents are copied into private storage. No URI is sent to Shizuku or a server. */
internal class ImportedApks private constructor(
    private val archive: ImportedApkArchive,
    private val snapshot: PackageSnapshot
) : ChartSource {
    override fun inspect(): PackageSnapshot { archive.ensureCurrent(); return snapshot }
    override fun entries(snapshot: PackageSnapshot): List<String> {
        require(snapshot == this.snapshot) { "导入谱库已变化，请重新读取" }
        return archive.entries()
    }
    override fun read(snapshot: PackageSnapshot, entry: String, maxBytes: Int): ByteArray {
        require(snapshot == this.snapshot) { "导入谱库已变化，请重新读取" }
        return archive.read(entry, maxBytes)
    }

    companion object {
        private const val RESERVE_BYTES = 64L * 1024 * 1024
        private const val MAX_METADATA_BYTES = 16 * 1024
        private val directoryName = Regex("[0-9a-f]{32}")
        private val fileName = Regex("part-[0-9]{2}\\.apk")
        private val hash = Regex("[0-9a-f]{64}")
        private val lock = Any()

        fun <T> importFiles(context: Context, uris: List<Uri>, progress: (String) -> Unit,
            validate: (ImportedApks) -> T): T = synchronized(lock) {
            interrupted()
            val selected = uris.distinct()
            require(selected.size in 1..ImportedApkArchive.MAX_APKS) { "请选择 1 到 32 个 APK 文件" }
            require(selected.all { it.scheme == "content" }) { "请通过系统文件选择器选择 APK" }
            val root = root(context)
            var previousKnown = true
            val previous = try { activeDirectory(root) }
            catch (_: IllegalArgumentException) {
                previousKnown = false
                progress("上次导入索引已损坏，正在建立新的 APK 副本…")
                null
            }
            if (previousKnown) cleanUnused(root, previous)
            val directory = File(root, UUID.randomUUID().toString().replace("-", ""))
            check(directory.mkdir()) { "无法创建 APK 导入目录" }
            var committed = false
            try {
                val records = JSONArray()
                var total = 0L
                selected.forEachIndexed { index, uri ->
                    interrupted()
                    progress("正在复制 APK " + (index + 1) + "/" + selected.size + " 到本机…")
                    val name = "part-" + index.toString().padStart(2, '0') + ".apk"
                    val target = File(directory, name)
                    val copied = (context.contentResolver.openInputStream(uri)
                        ?: error("无法打开选择的 APK 文件")).use { input ->
                        FileOutputStream(target).use { output ->
                            copyImportedApk(input, output, ImportedApkArchive.MAX_TOTAL_BYTES - total) {
                                check(directory.usableSpace >= RESERVE_BYTES) {
                                    "存储空间不足，请至少保留 64 MiB 并留出完整 APK 副本的空间"
                                }
                            }.also { output.fd.sync() }
                        }
                    }
                    total += copied.bytes
                    records.put(JSONObject().put("name", name).put("size", copied.bytes).put("sha256", copied.sha256))
                }
                interrupted()
                val metadata = JSONObject().put("schema", 1).put("createdAt", System.currentTimeMillis())
                    .put("files", records).toString()
                FileOutputStream(File(directory, "manifest.json")).use {
                    it.write(metadata.toByteArray(Charsets.UTF_8)); it.fd.sync()
                }
                progress("校验基础 APK、分包版本和资源索引…")
                val reader = open(directory)
                val result = validate(reader)
                interrupted()
                writeActive(root, directory.name)
                committed = true
                check(activeDirectory(root) == directory) { "无法保存导入索引，请重试" }
                // Retain one prior generation until the next import, so the UI can safely swap readers.
                // The next import reclaims older inactive generations before copying a new APK set.
                progress("导入完成，APK 和谱库仅保存在本机")
                result
            } finally {
                if (!committed) directory.deleteRecursively()
            }
        }

        /** Missing import is normal; a damaged saved import is surfaced and retained for inspection/retry. */
        fun <T> restore(context: Context, validate: (ImportedApks) -> T): T? = synchronized(lock) {
            interrupted()
            val directory = activeDirectory(root(context)) ?: return@synchronized null
            validate(open(directory))
        }

        private fun root(context: Context): File = File(context.filesDir, "imported-apks").apply {
            check(isDirectory || mkdirs()) { "无法访问导入 APK 存储目录" }
        }

        private fun activeDirectory(root: File): File? {
            val pointer = AtomicFile(File(root, "active"))
            val bytes = try {
                pointer.openRead().use { input ->
                    val data = ByteArray(64)
                    var length = 0
                    while (true) {
                        val count = input.read(data, length, data.size - length)
                        if (count < 0) break
                        require(count > 0 && length + count < data.size) { "已保存的 APK 索引无效" }
                        length += count
                    }
                    data.copyOf(length)
                }
            } catch (_: FileNotFoundException) { return null }
            val name = bytes.toString(Charsets.UTF_8)
            require(directoryName.matches(name)) { "已保存的 APK 索引无效，请重新导入" }
            return File(root, name)
        }

        private fun writeActive(root: File, name: String) {
            val pointer = AtomicFile(File(root, "active"))
            val stream = pointer.startWrite()
            try {
                stream.write(name.toByteArray(Charsets.UTF_8))
                pointer.finishWrite(stream)
            } catch (error: Throwable) {
                pointer.failWrite(stream)
                throw error
            }
        }

        private fun open(directory: File): ImportedApks {
            val metadata = File(directory, "manifest.json")
            require(metadata.isFile && metadata.length() in 1..MAX_METADATA_BYTES.toLong()) {
                "已保存的 APK 文件记录缺失或损坏，请重新导入"
            }
            val json = JSONObject(metadata.readText())
            require(json.getInt("schema") == 1) { "导入记录版本暂不支持，请重新导入" }
            val list = json.getJSONArray("files")
            require(list.length() in 1..ImportedApkArchive.MAX_APKS) { "已保存的 APK 文件数量无效" }
            val files = List(list.length()) { index ->
                val record = list.getJSONObject(index)
                val name = record.getString("name")
                require(fileName.matches(name) && hash.matches(record.getString("sha256"))) {
                    "已保存的 APK 文件记录无效"
                }
                File(directory, name).also {
                    require(it.isFile && it.length() == record.getLong("size")) { "APK 副本缺失或已改变，请重新导入" }
                }
            }
            val archive = ImportedApkArchive(files)
            val snapshot = PackageSnapshot(archive.manifest.packageName, "版本代码 " + archive.manifest.versionCode,
                archive.manifest.versionCode, json.getLong("createdAt"), files.map { it.absolutePath })
            return ImportedApks(archive, snapshot)
        }

        private fun cleanUnused(root: File, active: File?) {
            root.listFiles()?.filter { it.isDirectory && directoryName.matches(it.name) && it != active }
                ?.forEach { it.deleteRecursively() }
        }
    }
}
