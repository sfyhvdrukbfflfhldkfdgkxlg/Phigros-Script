package io.github.phiscript.assets

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

class AddressablesCatalogTest {
    private val asset = "Assets/Tracks/Test.Composer.0/Chart_IN.json"
    private val bundle = "assets/aa/Android/chart_hash.bundle"
    private val runtime = "{UnityEngine.AddressableAssets.Addressables.RuntimePath}/Android/"
    @Test fun resolvesDependencyBucketAndExpandedInternalId() {
        val json = fixture(listOf(key(asset, 0), numericKey(0x12345678, 1)),
            listOf(Row(asset, 1, 0), Row("0#chart_hash.bundle", -1, 1, 1)), listOf(runtime))
        assertEquals(listOf(ChartLocation("Test.Composer", "IN", asset, bundle)),
            AddressablesCatalog.parse(json, listOf("assets/aa/catalog.json", bundle)))
    }
    @Test fun handlesUnicodeStringLengthsBeyondOneByte() {
        val song = "安月名".repeat(110) + ".Composer"
        val name = "Assets/Tracks/$song.0/Chart_AT.json"
        val json = fixture(listOf(key(name, 0, unicode = true), key("bundle", 1)),
            listOf(Row(name, 1, 0), Row(runtime + "chart_hash.bundle", -1, 1, 1)))
        assertEquals(song, AddressablesCatalog.parse(json, listOf(bundle)).single().songId)
        assertEquals("AT", AddressablesCatalog.parse(json, listOf(bundle)).single().difficulty)
    }
    @Test fun handlesAsciiStringLengthsBeyondOneByte() {
        val song = "a".repeat(300)
        val name = "Assets/Tracks/$song.0/Chart_EZ.json"
        val json = fixture(listOf(key(name, 0), key("bundle", 1)),
            listOf(Row(name, 1, 0), Row(runtime + "chart_hash.bundle", -1, 1, 1)))
        assertEquals(song, AddressablesCatalog.parse(json, listOf(bundle)).single().songId)
    }
    @Test fun dependencyIndexUsesAllThirtyTwoBits() {
        val dependencyIndex = 65536
        val keys = MutableList(dependencyIndex + 1) { numericKey(it) }
        keys[0] = key(asset, 0); keys[dependencyIndex] = numericKey(Int.MIN_VALUE, 1)
        val json = fixture(keys, listOf(Row(asset, dependencyIndex, 0),
            Row(runtime + "chart_hash.bundle", -1, dependencyIndex, 1)))
        assertEquals(bundle, AddressablesCatalog.parse(json, listOf(bundle)).single().bundleEntry)
    }
    @Test fun rejectsPositiveDependencyIndexWhoseHighByteIsNonzero() {
        val root = JSONObject(basic())
        val bytes = Base64.getDecoder().decode(root.getString("m_EntryDataString"))
        bytes[4 + 8 + 3] = 1
        root.put("m_EntryDataString", Base64.getEncoder().encodeToString(bytes))
        assertThrows(IllegalArgumentException::class.java) { AddressablesCatalog.parse(root.toString(), listOf(bundle)) }
    }
    @Test fun choosesExplicitPathAcrossSplitEntryUnion() {
        assertEquals(bundle, AddressablesCatalog.parse(basic(),
            listOf("assets/aa/Android/other/chart_hash.bundle", bundle)).single().bundleEntry)
    }
    @Test fun rejectsAmbiguousFilenameFallback() {
        val json = fixture(listOf(key(asset, 0), key("bundle", 1)),
            listOf(Row(asset, 1, 0), Row("/unknown/runtime/chart_hash.bundle", -1, 1, 1)))
        val error = assertThrows(IllegalArgumentException::class.java) {
            AddressablesCatalog.parse(json, listOf(bundle, "assets/aa/other/chart_hash.bundle"))
        }
        assertTrue(error.message.orEmpty().contains("Ambiguous"))
    }
    @Test fun neverGuessesBundleNameByRemovingPrefixOrHash() {
        val json = fixture(listOf(key(asset, 0), key("bundle", 1)),
            listOf(Row(asset, 1, 0), Row(runtime + "prefix_chart_hash.bundle", -1, 1, 1)))
        assertThrows(IllegalArgumentException::class.java) { AddressablesCatalog.parse(json, listOf(bundle)) }
    }
    @Test fun rejectsMultipleOwningBundles() {
        val json = fixture(listOf(key(asset, 0), key("dependency set", 1, 2)), listOf(
            Row(asset, 1, 0), Row(runtime + "chart_hash.bundle", -1, 1, 1),
            Row(runtime + "another.bundle", -1, 1, 1)))
        assertThrows(IllegalArgumentException::class.java) {
            AddressablesCatalog.parse(json, listOf(bundle, "assets/aa/Android/another.bundle"))
        }
    }
    @Test fun bundleDependenciesDoNotBecomeChartOwners() {
        val json = fixture(listOf(key(asset, 0), key("owner", 1), key("shared", 2)), listOf(
            Row(asset, 1, 0), Row(runtime + "chart_hash.bundle", 2, 1, 1),
            Row(runtime + "shared.bundle", -1, 2, 1)))
        assertEquals(bundle, AddressablesCatalog.parse(json,
            listOf(bundle, "assets/aa/Android/shared.bundle")).single().bundleEntry)
    }
    @Test fun rejectsDependencyCycles() {
        val json = fixture(listOf(key(asset, 0), key("alias", 1)), listOf(Row(asset, 1, 0), Row("alias", 0, 1)))
        val error = assertThrows(IllegalArgumentException::class.java) { AddressablesCatalog.parse(json, listOf(bundle)) }
        assertTrue(error.message.orEmpty().contains("cycle"))
    }
    @Test fun ignoresNonChartAndUnsupportedDifficultyKeys() {
        val names = listOf("Assets/Tracks/Test.Composer.0/music.wav", "Assets/Tracks/Test.Composer.0/Chart_EX.json",
            "something/Chart_IN.json", "Assets/Tracks/Test.Composer.1/Chart_IN.json")
        val json = fixture(names.mapIndexed { i, name -> key(name, i) }, names.mapIndexed { i, name -> Row(name, -1, i) })
        assertTrue(AddressablesCatalog.parse(json, listOf(bundle)).isEmpty())
    }
    @Test fun rejectsTruncatedRowsAndInvalidBucketOffset() {
        val truncated = JSONObject(basic())
        val entries = Base64.getDecoder().decode(truncated.getString("m_EntryDataString"))
        truncated.put("m_EntryDataString", Base64.getEncoder().encodeToString(entries.copyOf(entries.size - 1)))
        assertThrows(IllegalArgumentException::class.java) { AddressablesCatalog.parse(truncated.toString(), listOf(bundle)) }
        val invalid = JSONObject(basic())
        val buckets = Base64.getDecoder().decode(invalid.getString("m_BucketDataString"))
        buckets[4] = 0xff.toByte(); buckets[5] = 0xff.toByte()
        buckets[6] = 0xff.toByte(); buckets[7] = 0x7f.toByte()
        invalid.put("m_BucketDataString", Base64.getEncoder().encodeToString(buckets))
        assertThrows(IllegalArgumentException::class.java) { AddressablesCatalog.parse(invalid.toString(), listOf(bundle)) }
    }
    @Test fun rejectsMalformedBase64AndMissingSplit() {
        val invalid = JSONObject(basic()).put("m_KeyDataString", "%%%%")
        assertThrows(IllegalArgumentException::class.java) { AddressablesCatalog.parse(invalid.toString(), listOf(bundle)) }
        val error = assertThrows(IllegalArgumentException::class.java) { AddressablesCatalog.parse(basic(), emptyList()) }
        assertTrue(error.message.orEmpty().contains("missing"))
    }
    private fun basic(): String = fixture(listOf(key(asset, 0), key("bundle alias", 1)),
        listOf(Row(asset, 1, 0), Row(runtime + "chart_hash.bundle", -1, 1, 1)))
    private data class Key(val bytes: ByteArray, val entries: IntArray)
    private data class Row(val internalId: String, val dependency: Int, val primaryKey: Int, val provider: Int = 0)
    private fun key(value: String, vararg refs: Int, unicode: Boolean = false): Key {
        val data = value.toByteArray(if (unicode) Charsets.UTF_16LE else Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        out.write(if (unicode) 1 else 0); out.int(data.size); out.write(data)
        return Key(out.toByteArray(), refs)
    }
    private fun numericKey(value: Int, vararg refs: Int): Key {
        val out = ByteArrayOutputStream(); out.write(4); out.int(value)
        return Key(out.toByteArray(), refs)
    }
    private fun fixture(keys: List<Key>, rows: List<Row>, prefixes: List<String> = emptyList()): String {
        val keyData = ByteArrayOutputStream(); val bucketData = ByteArrayOutputStream(); val entryData = ByteArrayOutputStream()
        keyData.int(keys.size); bucketData.int(keys.size)
        for (key in keys) {
            bucketData.int(keyData.size()); bucketData.int(key.entries.size)
            key.entries.forEach { bucketData.int(it) }; keyData.write(key.bytes)
        }
        entryData.int(rows.size)
        rows.forEachIndexed { index, row ->
            entryData.int(index); entryData.int(row.provider); entryData.int(row.dependency)
            entryData.int(0x76543210); entryData.int(-1); entryData.int(row.primaryKey); entryData.int(0)
        }
        return JSONObject().put("m_KeyDataString", Base64.getEncoder().encodeToString(keyData.toByteArray()))
            .put("m_BucketDataString", Base64.getEncoder().encodeToString(bucketData.toByteArray()))
            .put("m_EntryDataString", Base64.getEncoder().encodeToString(entryData.toByteArray()))
            .put("m_InternalIds", JSONArray(rows.map { it.internalId }))
            .put("m_InternalIdPrefixes", JSONArray(prefixes))
            .put("m_ProviderIds", JSONArray(listOf(
                "UnityEngine.ResourceManagement.ResourceProviders.BundledAssetProvider",
                "UnityEngine.ResourceManagement.ResourceProviders.AssetBundleProvider"))).toString()
    }
    private fun ByteArrayOutputStream.int(value: Int) { repeat(4) { write((value ushr (it * 8)) and 255) } }
}
