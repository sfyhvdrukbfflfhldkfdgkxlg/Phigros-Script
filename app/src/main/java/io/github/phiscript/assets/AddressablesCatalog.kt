package io.github.phiscript.assets

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Base64

data class ChartLocation(val songId: String, val difficulty: String, val assetKey: String, val bundleEntry: String)

/** Unity compact JSON catalog: int32 offsets/counts and seven-int32 location rows. */
object AddressablesCatalog {
    private const val MAX_BYTES = 64 * 1024 * 1024
    private const val MAX_ITEMS = 1_000_000
    private val chartKey = Regex("^Assets/Tracks/([^/]+)\\.0/Chart_(EZ|HD|IN|AT)\\.json$")
    private const val RUNTIME_PATH = "{UnityEngine.AddressableAssets.Addressables.RuntimePath}"
    fun parse(json: String, availableEntries: Collection<String>): List<ChartLocation> {
        require(json.length <= MAX_BYTES) { "Addressables catalog is too large" }
        return try { parseCatalog(JSONObject(json), availableEntries) }
        catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IllegalArgumentException("Invalid Addressables catalog: " + e.message, e) }
    }
    private data class Bucket(val key: String?, val entries: IntArray)
    private data class Entry(val internalId: String, val provider: String, val dependency: Int)
    private fun parseCatalog(root: JSONObject, available: Collection<String>): List<ChartLocation> {
        val keyBytes = decode(root, "m_KeyDataString")
        val bucketBytes = decode(root, "m_BucketDataString")
        val entryBytes = decode(root, "m_EntryDataString")
        val keyCount = Reader(keyBytes).count("key", 1)
        val bucketReader = Reader(bucketBytes)
        val bucketCount = bucketReader.count("bucket", 8)
        require(keyCount == bucketCount) { "Addressables key and bucket counts disagree" }
        val entryReader = Reader(entryBytes)
        val entryCount = entryReader.count("entry", 28)
        require(entryReader.remaining.toLong() == entryCount.toLong() * 28) {
            "Addressables location rows must contain exactly seven int32 values"
        }
        val buckets = ArrayList<Bucket>(bucketCount)
        repeat(bucketCount) {
            val keyOffset = bucketReader.int()
            require(keyOffset in 4 until keyBytes.size) { "Invalid catalog key offset: $keyOffset" }
            val refs = bucketReader.count("bucket reference", 4)
            val indices = IntArray(refs) {
                bucketReader.int().also { index ->
                    require(index in 0 until entryCount) { "Invalid catalog entry reference: $index" }
                }
            }
            buckets += Bucket(readKey(Reader(keyBytes, keyOffset)), indices)
        }
        require(bucketReader.remaining == 0) { "Trailing Addressables bucket data" }
        val ids = strings(root, "m_InternalIds")
        val prefixes = if (root.has("m_InternalIdPrefixes") && !root.isNull("m_InternalIdPrefixes"))
            strings(root, "m_InternalIdPrefixes") else emptyList()
        val providers = strings(root, "m_ProviderIds")
        val resourceTypeCount = root.optJSONArray("m_resourceTypes")?.length()
        val entries = ArrayList<Entry>(entryCount)
        repeat(entryCount) {
            val internalId = entryReader.int()
            val provider = entryReader.int()
            val dependency = entryReader.int()
            entryReader.int() // Dependency hash, not an index.
            entryReader.int() // Extra-data offset.
            val primaryKey = entryReader.int()
            val resourceType = entryReader.int()
            require(internalId in ids.indices) { "Invalid internal ID index: $internalId" }
            require(provider in providers.indices) { "Invalid provider index: $provider" }
            require(dependency < bucketCount) { "Invalid dependency-key index: $dependency" }
            require(primaryKey in buckets.indices) { "Invalid primary-key index: $primaryKey" }
            require(resourceType >= 0 && (resourceTypeCount == null || resourceType < resourceTypeCount)) {
                "Invalid resource-type index: $resourceType"
            }
            entries += Entry(expand(ids[internalId], prefixes), providers[provider], dependency)
        }
        val resolver = BundleResolver(available)
        val result = linkedMapOf<String, ChartLocation>()
        for (bucket in buckets) {
            val assetKey = bucket.key ?: continue
            val match = chartKey.matchEntire(assetKey) ?: continue
            require(bucket.entries.isNotEmpty()) { "Chart has no catalog location: $assetKey" }
            val resolved = linkedSetOf<String>()
            for (index in bucket.entries) {
                val bundles = linkedSetOf<String>()
                val visiting = hashSetOf<Int>()
                val visited = hashSetOf<Int>()
                fun visit(entryIndex: Int, depth: Int) {
                    require(depth <= 64) { "Catalog dependency nesting exceeds 64 levels" }
                    require(entryIndex !in visiting) { "Catalog dependency cycle for $assetKey" }
                    if (!visited.add(entryIndex)) return
                    visiting += entryIndex
                    val entry = entries[entryIndex]
                    val bundleProvider = entry.provider.substringBefore(',').endsWith("AssetBundleProvider")
                    val bundleId = entry.internalId.substringBefore('?').endsWith(".bundle")
                    if (bundleProvider || bundleId) {
                        // Shared bundle dependencies are not additional chart owners.
                        bundles += resolver.resolve(entry.internalId)
                    } else if (entry.dependency >= 0) {
                        val dependencies = buckets[entry.dependency].entries
                        require(dependencies.isNotEmpty()) { "Empty dependency bucket for $assetKey" }
                        dependencies.forEach { visit(it, depth + 1) }
                    }
                    visiting -= entryIndex
                }
                visit(index, 0)
                require(bundles.size == 1) {
                    "Expected one owning bundle for $assetKey; found " + bundles.size +
                        ". This catalog requires an unsupported multi-bundle mapping."
                }
                resolved += bundles.single()
            }
            require(resolved.size == 1) { "Ambiguous locations for chart $assetKey" }
            val location = ChartLocation(match.groupValues[1], match.groupValues[2], assetKey, resolved.single())
            val previous = result.put(assetKey, location)
            require(previous == null || previous == location) { "Conflicting chart key: $assetKey" }
        }
        return result.values.sortedWith(compareBy<ChartLocation> { it.songId }.thenBy { it.difficulty })
    }
    private fun strings(root: JSONObject, name: String): List<String> {
        val array = root.getJSONArray(name)
        require(array.length() <= MAX_ITEMS) { "$name contains too many items" }
        return List(array.length()) {
            require(array.get(it) is String) { "$name must contain strings" }; array.getString(it)
        }
    }
    private fun expand(id: String, prefixes: List<String>): String {
        if (prefixes.isEmpty()) return id
        val marker = id.lastIndexOf('#')
        if (marker < 0) return id
        val index = id.substring(0, marker).toIntOrNull() ?: return id
        require(index in prefixes.indices) { "Invalid internal ID prefix: $index" }
        return prefixes[index] + id.substring(marker + 1)
    }
    private fun decode(root: JSONObject, name: String): ByteArray {
        val encoded = root.getString(name)
        require(encoded.length.toLong() <= MAX_BYTES.toLong() * 4 / 3 + 1024) { "$name is too large" }
        val compact = encoded.filterNot { it == ' ' || it == '\n' || it == '\r' || it == '\t' }
        return Base64.getDecoder().decode(compact).also { require(it.size <= MAX_BYTES) { "$name is too large" } }
    }
    private fun readKey(reader: Reader): String? = when (val type = reader.byte()) {
        0 -> reader.string(reader.int(), Charsets.US_ASCII)
        1 -> {
            val length = reader.int()
            require(length % 2 == 0) { "Odd UTF-16 key length" }
            reader.string(length, Charsets.UTF_16LE)
        }
        2 -> { reader.skip(2); null }
        3, 4 -> { reader.int(); null }
        5, 6 -> { reader.skip(reader.byte()); null }
        7 -> {
            reader.string(reader.byte(), Charsets.US_ASCII)
            reader.string(reader.byte(), Charsets.US_ASCII)
            val length = reader.int()
            require(length % 2 == 0) { "Odd serialized JSON key length" }
            reader.string(length, Charsets.UTF_16LE); null
        }
        else -> throw IllegalArgumentException("Unsupported Addressables key type: $type")
    }
    private class BundleResolver(available: Collection<String>) {
        private val entries = available.toSet()
        private val byName = entries.filter { it.startsWith("assets/aa/") && !it.endsWith("/") }
            .groupBy { it.substringAfterLast('/') }
        fun resolve(internalId: String): String {
            val id = internalId.replace('\\', '/')
            require(!id.startsWith("http://", true) && !id.startsWith("https://", true)) {
                "Chart bundle is remote and not available in the installed APK: $internalId"
            }
            require('\u0000' !in id && id.split('/').none { it == ".." }) { "Invalid bundle path" }
            val candidates = linkedSetOf<String>()
            candidates += id.removePrefix("/")
            if (id.startsWith("$RUNTIME_PATH/")) candidates += "assets/aa/" + id.removePrefix("$RUNTIME_PATH/")
            val embedded = id.indexOf("!/assets/aa/")
            if (embedded >= 0) candidates += id.substring(embedded + 2)
            val assetRoot = id.indexOf("/assets/aa/")
            if (assetRoot >= 0) candidates += id.substring(assetRoot + 1)
            val runtimeAndroid = id.indexOf("/aa/Android/")
            if (runtimeAndroid >= 0) candidates += "assets" + id.substring(runtimeAndroid)
            val exact = candidates.filter { it in entries }.distinct()
            require(exact.size <= 1) { "Ambiguous bundle paths for $internalId" }
            if (exact.size == 1) return exact.single()
            val matches = byName[id.substringAfterLast('/')].orEmpty()
            require(matches.size == 1) {
                if (matches.isEmpty()) "Bundle missing from installed APK/splits: $internalId"
                else "Ambiguous bundle filename in APK/splits: $internalId"
            }
            return matches.single()
        }
    }
    private class Reader(private val data: ByteArray, var position: Int = 0) {
        val remaining: Int get() = data.size - position
        fun skip(length: Int) {
            require(length >= 0 && length <= remaining) { "Truncated or invalid catalog field" }; position += length
        }
        fun byte(): Int {
            require(remaining >= 1) { "Truncated catalog byte" }; return data[position++].toInt() and 255
        }
        fun int(): Int {
            require(remaining >= 4) { "Truncated catalog int32" }
            val value = (data[position].toInt() and 255) or
                ((data[position + 1].toInt() and 255) shl 8) or
                ((data[position + 2].toInt() and 255) shl 16) or
                ((data[position + 3].toInt() and 255) shl 24)
            position += 4
            return value
        }
        fun count(label: String, minimumBytes: Int): Int {
            val value = int()
            require(value in 0..MAX_ITEMS && value.toLong() * minimumBytes <= remaining) { "Invalid $label count: $value" }
            return value
        }
        fun string(length: Int, charset: Charset): String {
            val start = position
            skip(length)
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, start, length)).toString()
        }
    }
}
