package io.github.phiscript.assets

import android.content.Context
import android.net.Uri
import io.github.phiscript.AppSettings
import io.github.phiscript.engine.Chart
import io.github.phiscript.vision.SongCandidate
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class ChartLibrary private constructor(
    private val context: Context,
    val snapshot: PackageSnapshot,
    val locations: List<ChartLocation>,
    private val reader: ChartSource,
    val sourceLabel: String
) {
    private val settings = AppSettings(context)
    private val cache = File(context.cacheDir,
        "charts/" + chartCacheKey(snapshot, sourceLabel)).apply { mkdirs() }

    fun songs(): List<SongCandidate> = locations.map { it.songId }.distinct().sorted().map { id ->
        val title = id.substringBeforeLast('.', id)
        val alias = settings.alias(id)
        SongCandidate(id, title, if (alias.isBlank()) emptyList() else listOf(alias))
    }

    fun hasChart(songId: String, difficulty: String): Boolean =
        locations.any { it.songId == songId && it.difficulty == difficulty }

    @Synchronized fun load(songId: String, difficulty: String): Chart {
        // Revalidate the selected source before using even cached chart data.
        val current = reader.inspect()
        check(current == snapshot) { "APK 来源已变化，请停止并重新扫描或导入谱库" }
        val matches = locations.filter { it.songId == songId && it.difficulty == difficulty }
        require(matches.size == 1) { "该歌曲/难度不存在或存在多个资源，不能自动选择" }
        val location = matches.single()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest((songId + "/" + difficulty).toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = File(cache, hash + ".json")
        if (file.isFile) {
            try { return Chart.parse(file.readText()) }
            catch (_: Exception) { file.delete() }
        }

        val bundle = reader.read(snapshot, location.bundleEntry)
        val assets = UnityTextAssets().extract(bundle)
        val candidates = assets.mapNotNull { text ->
            val script = text.bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val json = runCatching { JSONObject(script) }.getOrNull() ?: return@mapNotNull null
            if (!json.has("judgeLineList") || !json.has("formatVersion")) return@mapNotNull null
            text.name to script
        }
        val named = candidates.filter { (name, _) ->
            name.substringAfterLast('/').substringBeforeLast('.') == "Chart_" + difficulty
        }
        val selected = when {
            named.size == 1 -> named.single().second
            candidates.size == 1 -> candidates.single().second
            else -> error("资源包里未找到唯一谱面 TextAsset：" + location.assetKey)
        }
        val chart = Chart.parse(selected)
        val temp = File(cache, hash + ".tmp")
        temp.writeText(selected)
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        return chart
    }

    companion object {
        fun scan(context: Context, progress: (String) -> Unit): ChartLibrary {
            progress("读取已安装的 Phigros…")
            val reader = InstalledChartSource(InstalledApks(context.applicationContext))
            return scanSource(context, reader, "已安装 APK", progress)
        }

        /** Blocking worker API for SAF documents; no installation, Shizuku, or server upload required. */
        fun importApks(context: Context, uris: List<Uri>, progress: (String) -> Unit): ChartLibrary =
            ImportedApks.importFiles(context.applicationContext, uris, progress) { reader ->
                scanSource(context, reader, "导入 APK", progress).also { library ->
                    val sample = library.locations.first()
                    progress("验证导入谱面的 Unity 资源解析…")
                    library.load(sample.songId, sample.difficulty)
                }
            }

        /** Null means no saved import. Invalid saved copies throw without removing the old import. */
        fun scanImported(context: Context, progress: (String) -> Unit): ChartLibrary? =
            ImportedApks.restore(context.applicationContext) { reader ->
                progress("读取上次导入的 APK…")
                scanSource(context, reader, "导入 APK", progress)
            }

        private fun scanSource(context: Context, reader: ChartSource, label: String,
            progress: (String) -> Unit): ChartLibrary {
            val snapshot = reader.inspect()
            progress("扫描 APK 资源索引…")
            val entries = reader.entries(snapshot)
            val catalogPath = "assets/aa/catalog.json"
            require(catalogPath in entries) {
                "APK 没有 assets/aa/catalog.json；请选完整基础 APK 和资源分包；此资源格式也可能暂不支持"
            }
            val catalog = reader.read(snapshot, catalogPath, 16 * 1024 * 1024)
                .toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val locations = AddressablesCatalog.parse(catalog, entries)
            require(locations.isNotEmpty()) { "没有发现可识别的歌曲谱面" }
            progress("已发现 " + locations.map { it.songId }.distinct().size +
                " 首歌曲、" + locations.size + " 个难度")
            return ChartLibrary(context.applicationContext, snapshot, locations, reader, label)
        }
    }
}

internal interface ChartSource {
    fun inspect(): PackageSnapshot
    fun entries(snapshot: PackageSnapshot): List<String>
    fun read(snapshot: PackageSnapshot, entry: String, maxBytes: Int = ApkFiles.MAX_ASSET_BYTES): ByteArray
}
private class InstalledChartSource(private val reader: InstalledApks) : ChartSource {
    override fun inspect() = reader.inspect()
    override fun entries(snapshot: PackageSnapshot) = reader.entries(snapshot)
    override fun read(snapshot: PackageSnapshot, entry: String, maxBytes: Int) = reader.read(snapshot, entry, maxBytes)
}

/** Includes origin and private copy generation so installed/imported or re-imported charts never share stale data. */
internal fun chartCacheKey(snapshot: PackageSnapshot, source: String): String {
    val identity = listOf(source, snapshot.packageName, snapshot.versionCode.toString(),
        snapshot.lastUpdateTime.toString()) + snapshot.paths
    val encoded = identity.joinToString("") { it.length.toString() + ":" + it }
    return MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
