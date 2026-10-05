package io.github.phiscript.assets

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Bounded streams and a single owner for every resource path across a base APK and its splits. */
internal class ImportedApkArchive(private val files: List<File>) {
    companion object {
        const val MAX_APKS = 32
        const val MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
        private const val MAX_MANIFESTS = 16 * 1024 * 1024
    }
    val manifest: ApkManifestInfo
    private val owners = linkedMapOf<String, File>()
    private val fingerprints = files.map { it.length() to it.lastModified() }
    init {
        require(files.size in 1..MAX_APKS) { "请选择 1 到 32 个 APK 文件" }
        require(files.distinct().size == files.size) { "APK 文件重复" }
        var bytes = 0L
        files.forEach {
            require(it.isFile && it.length() in 1..MAX_TOTAL_BYTES) { "APK 文件不存在或大小无效" }
            bytes += it.length()
            require(bytes <= MAX_TOTAL_BYTES) { "APK 总大小不能超过 8 GiB" }
        }
        var entryCount = 0
        var nameChars = 0L
        var manifestBytes = 0L
        val manifests = files.map { file ->
            interrupted()
            ZipFile(file).use { zip ->
                val seen = hashSetOf<String>()
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    interrupted()
                    val entry = iterator.nextElement()
                    entryCount++
                    nameChars += entry.name.length
                    require(entryCount <= ApkFiles.MAX_ENTRIES && nameChars <= ApkFiles.MAX_LIST_BYTES / 4) {
                        "APK 目录过大，已停止导入"
                    }
                    require(entry.name.length <= 4096 && '\u0000' !in entry.name &&
                        '\\' !in entry.name && !entry.name.startsWith("/") &&
                        entry.name.split('/').none { it == ".." || it == "." }) { "APK 资源路径无效" }
                    require(seen.add(entry.name)) { "同一 APK 内存在重复资源路径" }
                    if (entry.name.startsWith("assets/") && !entry.isDirectory) {
                        ApkFiles.validateRead(entry.name, ApkFiles.MAX_ASSET_BYTES)
                        require(owners.put(entry.name, file) == null) {
                            "多个 APK 包含相同资源路径，请勿混选不同安装包或版本：" + entry.name
                        }
                    }
                }
                val entry = zip.getEntry("AndroidManifest.xml") ?: error("文件不是 APK：缺少 AndroidManifest.xml")
                require(!entry.isDirectory && entry.size <= MAX_MANIFEST_BYTES) { "APK 清单大小无效" }
                val data = zip.getInputStream(entry).use { readBounded(it, MAX_MANIFEST_BYTES) }
                manifestBytes += data.size
                require(manifestBytes <= MAX_MANIFESTS) { "APK 清单总大小超出上限" }
                ApkManifestInfo.parse(data)
            }
        }
        val base = manifests.filter { it.split.isEmpty() }
        require(base.size == 1) { "请选择一个基础 APK 及同版本的资源分包；不能只选分包或多个基础 APK" }
        manifest = base.single()
        require(manifest.packageName == PHIGROS_PACKAGE) { "请选择 Phigros 的 APK 安装包" }
        require(manifests.all { it.packageName == manifest.packageName && it.versionCode == manifest.versionCode }) {
            "基础 APK 与分包的应用或版本不同，请选择同一次安装的完整 APK 集合"
        }
        require(manifests.map { it.split }.distinct().size == manifests.size) { "APK 分包标识重复" }
    }
    fun ensureCurrent() {
        files.forEachIndexed { index, file ->
            require(file.isFile && (file.length() to file.lastModified()) == fingerprints[index]) {
                "导入的 APK 副本已失效，请重新导入"
            }
        }
    }
    fun entries(): List<String> { ensureCurrent(); return owners.keys.toList() }
    fun read(entry: String, maxBytes: Int): ByteArray {
        ApkFiles.validateRead(entry, maxBytes); ensureCurrent(); interrupted()
        val file = owners[entry] ?: error("导入 APK 中缺少资源：" + entry)
        return ZipFile(file).use { zip ->
            val item = zip.getEntry(entry) ?: error("导入 APK 中的资源已失效")
            require(item.size <= maxBytes) { "资源超过读取上限：" + entry }
            zip.getInputStream(item).use { readBounded(it, maxBytes) }
        }
    }
}

internal data class ImportedCopy(val bytes: Long, val sha256: String)

internal fun copyImportedApk(input: InputStream, output: OutputStream, maxBytes: Long,
    checkSpace: () -> Unit = {}): ImportedCopy {
    require(maxBytes in 1..ImportedApkArchive.MAX_TOTAL_BYTES) { "APK 剩余导入额度无效" }
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(128 * 1024)
    var total = 0L
    var nextSpaceCheck = 0L
    while (true) {
        interrupted()
        if (total >= nextSpaceCheck) { checkSpace(); nextSpaceCheck = total + 8 * 1024 * 1024 }
        val count = input.read(buffer)
        if (count < 0) break
        require(count > 0) { "APK 读取流未返回数据" }
        total += count
        require(total <= maxBytes) { "APK 总大小不能超过 8 GiB" }
        output.write(buffer, 0, count)
        digest.update(buffer, 0, count)
    }
    require(total > 0) { "选择的 APK 文件为空" }
    return ImportedCopy(total, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
}
private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(64 * 1024)
    var count = 0L
    while (true) {
        interrupted()
        val size = input.read(buffer)
        if (size < 0) break
        require(size > 0) { "APK 资源流未返回数据" }
        count += size
        require(count <= maxBytes) { "APK 资源解压后超过读取上限" }
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}
internal fun interrupted() {
    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("APK 导入或解析已取消")
}
