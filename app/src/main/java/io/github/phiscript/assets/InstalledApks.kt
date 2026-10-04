package io.github.phiscript.assets

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipFile

const val PHIGROS_PACKAGE = "com.PigeonGames.Phigros"
data class PackageSnapshot(val packageName: String, val versionName: String,
    val versionCode: Long, val lastUpdateTime: Long, val paths: List<String>)
class ApkAccessException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Blocking IO worker APIs. Accessibility grants touch dispatch, not file access. */
class InstalledApks(context: Context) {
    private val context = context.applicationContext
    fun inspect(): PackageSnapshot = packageSnapshot(context)
    fun entries(snapshot: PackageSnapshot): List<String> {
        ensureCurrent(snapshot)
        return directOrShizuku(direct = { ApkFiles.entries(snapshot) }, privileged = { ApkAccess.entries(snapshot) })
    }
    fun read(snapshot: PackageSnapshot, entry: String, maxBytes: Int = ApkFiles.MAX_ASSET_BYTES): ByteArray {
        ApkFiles.validateRead(entry, maxBytes); ensureCurrent(snapshot)
        return directOrShizuku(direct = {
            val out = ByteArrayOutputStream()
            ApkFiles.copyAsset(snapshot, entry, maxBytes, out); out.toByteArray()
        }, privileged = { ApkAccess.read(snapshot, entry, maxBytes) })
    }
    private fun ensureCurrent(snapshot: PackageSnapshot) {
        if (snapshot != inspect()) throw ApkAccessException("Phigros 已更新或安装来源发生变化，请重新扫描。")
    }
    private fun <T> directOrShizuku(direct: () -> T, privileged: () -> T): T {
        try { return direct() }
        catch (e: ApkAccessException) { throw e }
        catch (e: IOException) { return fallback(e, privileged) }
        catch (e: SecurityException) { return fallback(e, privileged) }
    }
    private fun <T> fallback(original: Exception, privileged: () -> T): T {
        if (!ApkAccess.isConnected) throw ApkAccessException(
            "系统不允许直接读取 Phigros 安装包。请启动 Shizuku、授权并等待连接后重试。", original)
        try { return privileged() }
        catch (e: Exception) { throw ApkAccessException("通过 Shizuku 读取安装包失败：" + (e.message ?: e.javaClass.simpleName), e) }
    }
}
@Suppress("DEPRECATION")
internal fun packageSnapshot(context: Context): PackageSnapshot {
    val info = try { context.packageManager.getPackageInfo(PHIGROS_PACKAGE, 0) }
    catch (e: PackageManager.NameNotFoundException) { throw ApkAccessException("未找到已安装的 Phigros（" + PHIGROS_PACKAGE + "）。", e) }
    catch (e: SecurityException) { throw ApkAccessException("系统拒绝查询 Phigros 安装信息。", e) }
    val application = info.applicationInfo ?: throw ApkAccessException("Phigros 安装信息缺少 APK 路径。")
    val paths = (listOf(application.sourceDir) + (application.splitSourceDirs?.toList() ?: emptyList())).distinct()
    if (paths.isEmpty() || paths.any { it.isNullOrBlank() }) throw ApkAccessException("Phigros 安装包路径无效。")
    return PackageSnapshot(PHIGROS_PACKAGE, info.versionName ?: "未知",
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
        info.lastUpdateTime, paths)
}
internal object ApkFiles {
    const val MAX_ASSET_BYTES = 128 * 1024 * 1024
    const val MAX_LIST_BYTES = 16 * 1024 * 1024
    const val MAX_ENTRIES = 50_000
    fun entries(snapshot: PackageSnapshot): List<String> {
        val result = linkedSetOf<String>()
        var chars = 0L
        for (path in snapshot.paths) ZipFile(path).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                if (!entry.isDirectory && result.add(entry.name)) {
                    chars += entry.name.length
                    if (result.size > MAX_ENTRIES || chars > MAX_LIST_BYTES / 4)
                        throw ApkAccessException("安装包目录过大，已停止扫描。")
                }
            }
        }
        return result.toList()
    }
    fun validateRead(entry: String, maxBytes: Int) {
        if (!entry.startsWith("assets/") || entry.length > 4096 || entry.indexOf('\u0000') >= 0 ||
            entry.split('/').any { it == ".." } || entry.endsWith('/'))
            throw ApkAccessException("只能读取 Phigros APK 内 assets/ 下的资源文件。")
        if (maxBytes !in 1..MAX_ASSET_BYTES) throw ApkAccessException("资源读取上限必须为 1 到 128 MiB。")
    }
    fun copyAsset(snapshot: PackageSnapshot, entry: String, maxBytes: Int, output: OutputStream) {
        validateRead(entry, maxBytes)
        for (path in snapshot.paths) ZipFile(path).use { zip ->
            val asset = zip.getEntry(entry)
            if (asset != null && !asset.isDirectory) {
                if (asset.size > maxBytes) throw ApkAccessException("资源超过读取上限：" + entry)
                zip.getInputStream(asset).use { input ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw ApkAccessException("资源解压后超过读取上限：" + entry)
                        output.write(buffer, 0, n)
                    }
                }
                return
            }
        }
        throw ApkAccessException("安装包内没有此资源：" + entry)
    }
}
