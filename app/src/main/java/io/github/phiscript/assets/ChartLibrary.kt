package io.github.phiscript.assets

import android.content.Context
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
    private val reader: InstalledApks
) {
    private val settings = AppSettings(context)
    private val cache = File(context.cacheDir,
        "charts/" + snapshot.versionCode + "-" + snapshot.lastUpdateTime).apply { mkdirs() }

    fun songs(): List<SongCandidate> = locations.map { it.songId }.distinct().sorted().map { id ->
        val title = id.substringBeforeLast('.', id)
        val alias = settings.alias(id)
        SongCandidate(id, title, if (alias.isBlank()) emptyList() else listOf(alias))
    }

    fun hasChart(songId: String, difficulty: String): Boolean =
        locations.any { it.songId == songId && it.difficulty == difficulty }

    @Synchronized fun load(songId: String, difficulty: String): Chart {
        // Revalidate even cached data: an in-place game update invalidates the library.
        val current = reader.inspect()
        check(current.versionCode == snapshot.versionCode &&
            current.lastUpdateTime == snapshot.lastUpdateTime &&
            current.paths == snapshot.paths) { "游戏已更新，请停止并重新扫描谱库" }
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
            val app = context.applicationContext
            val reader = InstalledApks(app)
            progress("读取已安装的 Phigros…")
            val snapshot = reader.inspect()
            progress("扫描 APK 资源索引…")
            val entries = reader.entries(snapshot)
            val catalogPath = "assets/aa/catalog.json"
            require(catalogPath in entries) {
                "安装包没有 assets/aa/catalog.json；此资源格式暂不支持"
            }
            val catalog = reader.read(snapshot, catalogPath, 16 * 1024 * 1024)
                .toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val locations = AddressablesCatalog.parse(catalog, entries)
            require(locations.isNotEmpty()) { "没有发现可识别的歌曲谱面" }
            progress("已发现 " + locations.map { it.songId }.distinct().size +
                " 首歌曲、" + locations.size + " 个难度")
            return ChartLibrary(app, snapshot, locations, reader)
        }
    }
}
