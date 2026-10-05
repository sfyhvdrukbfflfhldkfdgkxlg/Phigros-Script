package io.github.phiscript.assets

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportedApkArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun readsUtf8BinaryManifestWithUnsignedVersionCode() {
        val parsed = ApkManifestInfo.parse(manifest(version = -1))
        assertEquals(PHIGROS_PACKAGE, parsed.packageName)
        assertEquals(0xffffffffL, parsed.versionCode)
        assertEquals("", parsed.split)
    }
    @Test fun readsUtf16ManifestAndMajorVersion() {
        val parsed = ApkManifestInfo.parse(manifest(split = "config.arm64_v8a", version = 7, major = 2, utf8 = false))
        assertEquals((2L shl 32) or 7, parsed.versionCode)
        assertEquals("config.arm64_v8a", parsed.split)
    }
    @Test fun rejectsTextXmlAndTruncatedManifest() {
        assertThrows(IllegalArgumentException::class.java) { ApkManifestInfo.parse("<manifest/>".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { ApkManifestInfo.parse(manifest().dropLast(1).toByteArray()) }
    }
    @Test fun rejectsInvalidStringPoolOffsetWithoutAllocation() {
        val bytes = manifest()
        // The string pool begins at 8; its string-data offset is at +20.
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(28, Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { ApkManifestInfo.parse(bytes) }
    }
    @Test fun readsAssetsFromTheirExactSplitOwner() {
        val base = apk("base", mapOf("assets/aa/catalog.json" to "catalog".toByteArray()))
        val split = apk("split", mapOf("assets/aa/Android/song.bundle" to "bundle".toByteArray()), split = "assets")
        val archive = ImportedApkArchive(listOf(base, split))
        assertEquals(setOf("assets/aa/catalog.json", "assets/aa/Android/song.bundle"), archive.entries().toSet())
        assertArrayEquals("bundle".toByteArray(), archive.read("assets/aa/Android/song.bundle", 100))
    }
    @Test fun rejectsDifferentSplitVersion() {
        val base = apk("base", emptyMap())
        val split = apk("split", emptyMap(), split = "assets", version = 2)
        assertThrows(IllegalArgumentException::class.java) { ImportedApkArchive(listOf(base, split)) }
    }
    @Test fun rejectsDifferentPackageAndTwoBaseApks() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportedApkArchive(listOf(apk("wrong", emptyMap(), packageName = "other.game")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportedApkArchive(listOf(apk("base1", emptyMap()), apk("base2", emptyMap())))
        }
    }
    @Test fun rejectsOnlySplitAndDuplicateSplitIds() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportedApkArchive(listOf(apk("split", emptyMap(), split = "assets")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportedApkArchive(listOf(apk("base", emptyMap()),
                apk("one", emptyMap(), split = "assets"), apk("two", emptyMap(), split = "assets")))
        }
    }
    @Test fun rejectsDuplicateResourceOwnersEvenWhenBytesMatch() {
        val asset = mapOf("assets/aa/catalog.json" to byteArrayOf(1))
        val base = apk("base", asset)
        val split = apk("split", asset, split = "assets")
        assertThrows(IllegalArgumentException::class.java) { ImportedApkArchive(listOf(base, split)) }
    }
    @Test fun rejectsTraversalAndBackslashEntries() {
        listOf("assets/../chart.json", "assets/aa\\chart.json", "/assets/aa/chart.json").forEachIndexed { i, path ->
            assertThrows(IllegalArgumentException::class.java) {
                ImportedApkArchive(listOf(apk("unsafe" + i, mapOf(path to byteArrayOf(1)))))
            }
        }
    }
    @Test fun rejectsNonApkZipAndAssetOutsideAssets() {
        val file = temporary.newFile("plain.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("assets/aa/catalog.json")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        assertThrows(IllegalStateException::class.java) { ImportedApkArchive(listOf(file)) }
        val archive = ImportedApkArchive(listOf(apk("base", emptyMap())))
        assertThrows(ApkAccessException::class.java) { archive.read("AndroidManifest.xml", 100) }
    }
    @Test fun boundsDecompressedResourceAndInvalidatesMissingCopy() {
        val file = apk("base", mapOf("assets/aa/chart" to ByteArray(2048)))
        val archive = ImportedApkArchive(listOf(file))
        assertThrows(IllegalArgumentException::class.java) { archive.read("assets/aa/chart", 1024) }
        assertTrue(file.delete())
        assertThrows(IllegalArgumentException::class.java) { archive.entries() }
    }
    @Test fun copiesWithoutMaterializingApkAndComputesDigest() {
        val output = ByteArrayOutputStream()
        var checks = 0
        val result = copyImportedApk(ByteArrayInputStream("abc".toByteArray()), output, 3) { checks++ }
        assertEquals(3L, result.bytes)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
        assertArrayEquals("abc".toByteArray(), output.toByteArray())
        assertTrue(checks > 0)
    }
    @Test fun refusesOversizedAndEmptyInput() {
        assertThrows(IllegalArgumentException::class.java) {
            copyImportedApk(ByteArrayInputStream(ByteArray(5)), ByteArrayOutputStream(), 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            copyImportedApk(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 4)
        }
    }
    @Test fun checksInterruptionAndSpaceBeforeReading() {
        val input = object : InputStream() {
            override fun read(): Int = error("Must not read")
            override fun read(bytes: ByteArray): Int = error("Must not read")
        }
        assertThrows(IllegalStateException::class.java) {
            copyImportedApk(input, ByteArrayOutputStream(), 1) { error("Out of space") }
        }
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedIOException::class.java) {
                copyImportedApk(input, ByteArrayOutputStream(), 1)
            }
        } finally { Thread.interrupted() }
    }
    @Test fun keepsInstalledImportedAndNewGenerationsInDifferentCaches() {
        val installed = PackageSnapshot(PHIGROS_PACKAGE, "1", 1, 2, listOf("/installed/base.apk"))
        val imported = installed.copy(paths = listOf("/private/generation1/base.apk"))
        val replacement = imported.copy(paths = listOf("/private/generation2/base.apk"))
        val keys = listOf(chartCacheKey(installed, "已安装 APK"), chartCacheKey(imported, "导入 APK"),
            chartCacheKey(replacement, "导入 APK"), chartCacheKey(imported, "已安装 APK"))
        assertEquals(4, keys.toSet().size)
        assertEquals(keys[1], chartCacheKey(imported, "导入 APK"))
    }

    private fun apk(name: String, assets: Map<String, ByteArray>, split: String = "", version: Int = 1,
        packageName: String = PHIGROS_PACKAGE): File = temporary.newFile(name + ".apk").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            (mapOf("AndroidManifest.xml" to manifest(split, version, packageName = packageName)) + assets)
                .forEach { (path, data) ->
                    zip.putNextEntry(ZipEntry(path)); zip.write(data); zip.closeEntry()
                }
        }
    }
    private fun manifest(split: String = "", version: Int = 1, major: Int = 0, utf8: Boolean = true,
        packageName: String = PHIGROS_PACKAGE): ByteArray {
        val strings = listOf("manifest", "package", "versionCode", "versionCodeMajor", "split",
            packageName, split, "http://schemas.android.com/apk/res/android")
        val data = ByteArrayOutputStream()
        val offsets = strings.map { value ->
            val offset = data.size()
            if (utf8) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                data.write(value.length); data.write(bytes.size); data.write(bytes); data.write(0)
            } else {
                data.short(value.length); data.write(value.toByteArray(Charsets.UTF_16LE)); data.short(0)
            }
            offset
        }
        while (data.size() % 4 != 0) data.write(0)
        val pool = ByteArrayOutputStream()
        pool.short(1); pool.short(28); pool.int(28 + offsets.size * 4 + data.size())
        pool.int(strings.size); pool.int(0); pool.int(if (utf8) 0x100 else 0)
        pool.int(28 + offsets.size * 4); pool.int(0)
        offsets.forEach { pool.int(it) }; pool.write(data.toByteArray())
        data class Attribute(val namespace: Int, val name: Int, val type: Int, val value: Int)
        val attrs = mutableListOf(Attribute(-1, 1, 3, 5), Attribute(7, 2, 0x10, version),
            Attribute(7, 3, 0x10, major))
        if (split.isNotEmpty()) attrs.add(Attribute(-1, 4, 3, 6))
        val node = ByteArrayOutputStream()
        node.short(0x0102); node.short(16); node.int(36 + attrs.size * 20)
        node.int(1); node.int(-1); node.int(-1); node.int(0)
        node.short(20); node.short(20); node.short(attrs.size); repeat(3) { node.short(0) }
        attrs.forEach {
            node.int(it.namespace); node.int(it.name); node.int(-1)
            node.short(8); node.write(0); node.write(it.type); node.int(it.value)
        }
        return ByteArrayOutputStream().apply {
            short(3); short(8); int(8 + pool.size() + node.size()); write(pool.toByteArray()); write(node.toByteArray())
        }.toByteArray()
    }
    private fun ByteArrayOutputStream.short(value: Int) { write(value and 255); write((value ushr 8) and 255) }
    private fun ByteArrayOutputStream.int(value: Int) { short(value and 65535); short(value ushr 16) }
}
