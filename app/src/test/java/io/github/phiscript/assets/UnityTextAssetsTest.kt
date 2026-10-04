package io.github.phiscript.assets

import net.jpountz.lz4.LZ4Factory
import org.junit.Assert.*
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Independent deterministic binary fixtures; no copyrighted chart or network. */
class UnityTextAssetsTest {
    private val extractor = UnityTextAssets()
    private val chart = """{"formatVersion":3,"offset":0.125,"judgeLineList":[]}""".toByteArray()
    private val chartName = "测试曲.IN"
    @Test fun readsSupportedSerializedVersionsWithAndWithoutTypeTreesInEitherByteOrder() {
        for (version in 17..22) for (little in listOf(true, false)) for (tree in listOf(true, false))
            assertOneChart(extractor.extract(serialized(version = version, little = little, tree = tree).bytes))
    }
    @Test fun skipsNonTextTypesAndTheirScriptHashesAndPreservesBinaryTextPayloads() {
        val binary = byteArrayOf(0, 1, 0x80.toByte(), 0xff.toByte(), 0)
        val result = extractor.extract(serialized(texts = listOf(chartName to chart, "raw.bytes" to binary)).bytes)
        assertEquals(listOf(chartName, "raw.bytes"), result.map { it.name })
        assertArrayEquals(chart, result[0].bytes); assertArrayEquals(binary, result[1].bytes)
    }
    @Test fun readsAllCompressionModesWithDirectoryAtFrontOrEndAcrossBlockBoundaries() {
        val file = serialized().bytes
        for (codec in 0..3) for (atEnd in listOf(false, true))
            assertOneChart(extractor.extract(unityBundle(file, codec = codec, infoCodec = codec,
                atEnd = atEnd, padding = true, chunkSize = 97).bytes))
    }
    @Test fun supportsDifferentCompressionForDirectoryAndDataBlocks() {
        for ((codec, infoCodec) in listOf(0 to 2, 2 to 0, 3 to 1, 1 to 3))
            assertOneChart(extractor.extract(unityBundle(serialized().bytes, codec = codec, infoCodec = infoCodec).bytes))
    }
    @Test fun readsLegacyUnalignedAnd2019AlignedFormatSixHeaders() {
        for (engine in listOf("2018.4.36f1", "2019.4.15f1"))
            assertOneChart(extractor.extract(unityBundle(serialized(version = 17).bytes,
                format = 6, engine = engine, padding = false).bytes))
    }
    @Test fun skipsRawResourceNodesInsteadOfTreatingThemAsSerializedFiles() {
        assertOneChart(extractor.extract(unityBundle(serialized().bytes, addResource = true).bytes))
    }
    @Test fun refusesTextPayloadThatWouldReadBytesFromTheNextObject() {
        val fixture = serialized(texts = listOf(chartName to chart, "next" to ByteArray(64)), firstPayloadLengthExtra = 12)
        expectIOException("Truncated Unity data") { extractor.extract(fixture.bytes) }
    }
    @Test fun refusesObjectOffsetOutsideTheSerializedFile() {
        for (version in listOf(17, 22)) {
            val fixture = serialized(version = version)
            val bytes = fixture.bytes.copyOf()
            val position = fixture.firstTextEntry + 8
            if (version >= 22) putLong(bytes, position, bytes.size.toLong(), true)
            else putInt(bytes, position, bytes.size, true)
            expectIOException("Object exceeds SerializedFile") { extractor.extract(bytes) }
        }
    }
    @Test fun refusesObjectsWithAnUnknownTypeIndex() {
        val fixture = serialized(); val bytes = fixture.bytes.copyOf()
        putInt(bytes, fixture.firstTextEntry + 8 + 8 + 4, 12345, true)
        expectIOException("missing serialized type") { extractor.extract(bytes) }
    }
    @Test fun refusesMetadataOverlapAndUnsupportedFutureSerializedVersions() {
        val fixture = serialized(); val overlap = fixture.bytes.copyOf()
        putInt(overlap, 20, overlap.size, false)
        expectIOException("metadata bounds") { extractor.extract(overlap) }
        val future = fixture.bytes.copyOf(); putInt(future, 8, 23, false)
        expectIOException("Unsupported SerializedFile version") { extractor.extract(future) }
    }
    @Test fun refusesTruncatedInputAndMismatchingBundleSize() {
        val file = serialized().bytes
        for (length in listOf(0, 1, 7, 19, 47, file.size - 1))
            expectIOException { extractor.extract(file.copyOf(length)) }
        val bundle = unityBundle(file)
        expectIOException("size does not match") { extractor.extract(bundle.bytes.copyOf(bundle.bytes.size - 1)) }
    }
    @Test fun refusesUnsupportedCompressionAndEncryptionWithActionableErrors() {
        val fixture = unityBundle(serialized().bytes)
        val codec = fixture.bytes.copyOf(); putInt(codec, fixture.flagsOffset, 0x40 or 4, false)
        expectIOException("Unsupported Unity compression type: 4") { extractor.extract(codec) }
        val encrypted = fixture.bytes.copyOf(); putInt(encrypted, fixture.flagsOffset, 0x40 or 0x400, false)
        expectIOException("Encrypted UnityFS") { extractor.extract(encrypted) }
    }
    @Test fun boundsAllocationBeforeAttemptingToInflateAnOversizedDirectoryOrBlock() {
        val fixture = unityBundle(serialized().bytes)
        val largeInfo = fixture.bytes.copyOf()
        putInt(largeInfo, fixture.flagsOffset - 4, UnityTextAssets.MAX_BYTES + 1, false)
        expectIOException("128 MiB") { extractor.extract(largeInfo) }
        val largeBlock = fixture.bytes.copyOf()
        putInt(largeBlock, fixture.infoOffset + 20, UnityTextAssets.MAX_BYTES, false)
        expectIOException("Uncompressed bundle exceeds 128 MiB") { extractor.extract(largeBlock) }
    }
    @Test fun refusesUncompressedAndLz4LengthMismatches() {
        for (codec in listOf(0, 2)) {
            val fixture = unityBundle(serialized().bytes, codec = codec, infoCodec = 0)
            val bytes = fixture.bytes.copyOf()
            val original = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt(fixture.infoOffset + 20)
            putInt(bytes, fixture.infoOffset + 20, original + 1, false)
            expectIOException("size mismatch") { extractor.extract(bytes) }
        }
    }
    @Test fun boundsLzmaDictionaryBeforeConstructingTheDecoder() {
        val fixture = unityBundle(serialized().bytes, infoCodec = 1); val bytes = fixture.bytes.copyOf()
        putInt(bytes, fixture.infoOffset + 1, 128 * 1024 * 1024, true)
        expectIOException("LZMA dictionary exceeds 64 MiB") { extractor.extract(bytes) }
    }
    @Test fun cancelsExtractionWhenTheWorkerThreadIsInterrupted() {
        val wasInterrupted = Thread.interrupted()
        try {
            Thread.currentThread().interrupt()
            try {
                extractor.extract(serialized().bytes); fail("Expected InterruptedIOException")
            } catch (expected: InterruptedIOException) { assertTrue(Thread.currentThread().isInterrupted) }
        } finally { Thread.interrupted(); if (wasInterrupted) Thread.currentThread().interrupt() }
    }
    @Test fun explicitlyRejectsEditorObjectsInsteadOfGuessingTheirFieldLayout() {
        expectIOException("Editor TextAssets are unsupported") { extractor.extract(serialized(target = -2).bytes) }
    }
    private fun assertOneChart(assets: List<TextAsset>) {
        assertEquals(1, assets.size); assertEquals(chartName, assets.single().name)
        assertArrayEquals(chart, assets.single().bytes)
    }
    private fun expectIOException(message: String? = null, action: () -> Unit) {
        try { action(); fail("Expected IOException") }
        catch (error: IOException) {
            if (message != null) assertTrue("Expected '" + message + "', got: " + error.message,
                error.message.orEmpty().contains(message))
        }
    }
    private data class SerializedFixture(val bytes: ByteArray, val firstTextEntry: Int)
    private fun serialized(version: Int = 22, little: Boolean = true, tree: Boolean = true,
        texts: List<Pair<String, ByteArray>> = listOf(chartName to chart),
        firstPayloadLengthExtra: Int = 0, target: Int = 13): SerializedFixture {
        val headerSize = if (version >= 22) 48 else 20
        val data = Writer(little)
        data.bytes(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0xfd.toByte()))
        val offsets = mutableListOf(0); val sizes = mutableListOf(3)
        texts.forEachIndexed { index, (name, payload) ->
            data.align(8)
            val start = data.size; offsets.add(start)
            val encodedName = name.toByteArray()
            data.i32(encodedName.size); data.bytes(encodedName); data.align(4)
            data.i32(payload.size + if (index == 0) firstPayloadLengthExtra else 0); data.bytes(payload)
            sizes.add(data.size - start)
        }
        val meta = Writer(little)
        meta.cString("2021.3.40f1"); meta.i32(target); meta.u8(if (tree) 1 else 0); meta.i32(3)
        for (classId in listOf(114, 1, 49)) {
            meta.i32(classId); meta.u8(0); meta.u16(0xffff)
            if (classId == 114) meta.bytes(ByteArray(16) { 0x5a })
            meta.bytes(ByteArray(16) { 0x31 })
            if (tree) {
                val strings = "TextAsset\u0000Base\u0000".toByteArray()
                meta.i32(2); meta.i32(strings.size)
                repeat(2) { level ->
                    meta.u16(1); meta.u8(level); meta.u8(0); meta.i32(0); meta.i32(10)
                    meta.i32(-1); meta.i32(level); meta.i32(0)
                    if (version >= 19) meta.i64(0)
                }
                meta.bytes(strings)
                if (version >= 21) { meta.i32(2); meta.i32(0); meta.i32(1) }
            }
        }
        meta.i32(offsets.size)
        var firstTextEntry = -1
        offsets.forEachIndexed { index, offset ->
            meta.align(4)
            if (index == 1) firstTextEntry = headerSize + meta.size
            meta.i64(0x0102030405060700L + index)
            if (version >= 22) meta.i64(offset.toLong()) else meta.i32(offset)
            meta.i32(sizes[index]); meta.i32(if (index == 0) 1 else 2)
        }
        meta.i32(0); meta.i32(0)
        if (version >= 20) meta.i32(0)
        meta.cString("fixture")
        val dataOffset = (headerSize + meta.size + 15) and -16
        val fileSize = dataOffset + data.size
        val header = Writer(false)
        header.i32(if (version >= 22) 0 else meta.size); header.i32(if (version >= 22) 0 else fileSize)
        header.i32(version); header.i32(if (version >= 22) 0 else dataOffset)
        header.u8(if (little) 0 else 1); header.bytes(ByteArray(3))
        if (version >= 22) {
            header.i32(meta.size); header.i64(fileSize.toLong()); header.i64(dataOffset.toLong()); header.i64(0)
        }
        header.bytes(meta.toByteArray()); header.align(16); header.bytes(data.toByteArray())
        return SerializedFixture(header.toByteArray(), firstTextEntry)
    }
    private data class BundleFixture(val bytes: ByteArray, val flagsOffset: Int, val infoOffset: Int)
    private fun unityBundle(serialized: ByteArray, codec: Int = 0, infoCodec: Int = 0,
        atEnd: Boolean = false, padding: Boolean = false, format: Int = 7,
        engine: String = "2021.3.40f1", chunkSize: Int = 4096, addResource: Boolean = false): BundleFixture {
        val resource = if (addResource) ByteArray(71) { (it + 61).toByte() } else byteArrayOf()
        val combined = serialized + resource
        val chunks = combined.asList().chunked(chunkSize).map { it.toByteArray() }
        val compressedChunks = chunks.map { compress(it, codec) }
        val info = Writer(false)
        info.bytes(ByteArray(16)); info.i32(chunks.size)
        chunks.forEachIndexed { index, chunk ->
            info.i32(chunk.size); info.i32(compressedChunks[index].size); info.u16(codec)
        }
        info.i32(if (addResource) 2 else 1); info.i64(0); info.i64(serialized.size.toLong())
        info.i32(4); info.cString("CAB-fixture")
        if (addResource) {
            info.i64(serialized.size.toLong()); info.i64(resource.size.toLong()); info.i32(0)
            info.cString("CAB-fixture.resS")
        }
        val packedInfo = compress(info.toByteArray(), infoCodec)
        val writer = Writer(false)
        writer.cString("UnityFS"); writer.i32(format); writer.cString("5.x.x"); writer.cString(engine)
        val bundleSizeOffset = writer.size; writer.i64(0)
        writer.i32(packedInfo.size); writer.i32(info.size)
        val flagsOffset = writer.size
        writer.i32(0x40 or infoCodec or (if (atEnd) 0x80 else 0) or (if (padding) 0x200 else 0))
        if (format >= 7 || engine == "2019.4.15f1") writer.align(16)
        val infoOffset: Int
        if (atEnd) {
            if (padding) writer.align(16)
            compressedChunks.forEach { writer.bytes(it) }
            infoOffset = writer.size; writer.bytes(packedInfo)
        } else {
            infoOffset = writer.size; writer.bytes(packedInfo)
            if (padding) writer.align(16)
            compressedChunks.forEach { writer.bytes(it) }
        }
        val bytes = writer.toByteArray(); putLong(bytes, bundleSizeOffset, bytes.size.toLong(), false)
        return BundleFixture(bytes, flagsOffset, infoOffset)
    }
    private fun compress(bytes: ByteArray, codec: Int): ByteArray = when (codec) {
        0 -> bytes
        2 -> LZ4Factory.safeInstance().fastCompressor().compress(bytes)
        3 -> LZ4Factory.safeInstance().highCompressor().compress(bytes)
        1 -> {
            val options = LZMA2Options(1); options.dictSize = 64 * 1024
            val output = ByteArrayOutputStream()
            output.write((options.pb * 5 + options.lp) * 9 + options.lc)
            repeat(4) { output.write((options.dictSize ushr (it * 8)) and 255) }
            LZMAOutputStream(output, options, true).use { it.write(bytes) }
            output.toByteArray()
        }
        else -> error("Invalid fixture codec")
    }
    private fun putInt(bytes: ByteArray, at: Int, value: Int, little: Boolean) {
        ByteBuffer.wrap(bytes).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).putInt(at, value)
    }
    private fun putLong(bytes: ByteArray, at: Int, value: Long, little: Boolean) {
        ByteBuffer.wrap(bytes).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).putLong(at, value)
    }
    private class Writer(private val little: Boolean) {
        private val output = ByteArrayOutputStream()
        val size: Int get() = output.size()
        fun u8(value: Int) { output.write(value and 255) }
        fun u16(value: Int) {
            if (little) for (i in 0..1) u8(value ushr (i * 8)) else for (i in 1 downTo 0) u8(value ushr (i * 8))
        }
        fun i32(value: Int) {
            if (little) for (i in 0..3) u8(value ushr (i * 8)) else for (i in 3 downTo 0) u8(value ushr (i * 8))
        }
        fun i64(value: Long) {
            if (little) for (i in 0..7) u8((value ushr (i * 8)).toInt())
            else for (i in 7 downTo 0) u8((value ushr (i * 8)).toInt())
        }
        fun bytes(value: ByteArray) { output.write(value) }
        fun cString(value: String) { bytes(value.toByteArray()); u8(0) }
        fun align(alignment: Int) { while (size % alignment != 0) u8(0) }
        fun toByteArray(): ByteArray = output.toByteArray()
    }
}
