package io.github.phiscript.assets

import net.jpountz.lz4.LZ4Factory
import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InterruptedIOException

data class TextAsset(val name: String, val bytes: ByteArray)

/**
 * Read-only player TextAsset extractor. UnityFS 6–8, SerializedFile 17–22.
 * Layout follows AssetStudio/UnityPy documentation. Executes no object code.
 * Input, uncompressed bundle, and accumulated payloads are bounded at 128 MiB.
 */
class UnityTextAssets {
    @Throws(IOException::class)
    fun extract(bundle: ByteArray): List<TextAsset> {
        if (bundle.size > MAX_BYTES) fail("Input exceeds 128 MiB")
        if (bundle.size < 8) fail("Truncated Unity file")
        return if (bundle.copyOfRange(0, 8).contentEquals(UNITY_FS)) readBundle(bundle)
        else readSerialized(bundle, 0, bundle.size)
    }
    private data class Block(val plain: Int, val packed: Int, val flags: Int)
    private data class Node(val offset: Int, val size: Int, val flags: Int, val path: String)
    private data class Entry(val start: Int, val size: Int, val type: Int)
    private fun readBundle(bytes: ByteArray): List<TextAsset> {
        val r = Reader(bytes)
        if (r.cString() != "UnityFS") fail("Unsupported bundle signature")
        val format = r.i32()
        if (format !in 6..8) fail("Unsupported UnityFS version: " + format)
        r.cString()
        val engine = r.cString()
        val declaredSize = r.i64()
        if (declaredSize != bytes.size.toLong()) fail("UnityFS size does not match input")
        val packedInfo = bounded(r.u32(), "Compressed block information")
        val plainInfo = bounded(r.u32(), "Block information")
        val flags = r.i32()
        if (flags and 0x40 == 0) fail("Separate UnityFS directory tables are unsupported")
        val v = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(engine)
            ?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: 0 } ?: listOf(0, 0, 0)
        val newerFlags = when (v[0]) {
            2020 -> v[1] > 3 || v[1] == 3 && v[2] >= 34
            2021 -> v[1] > 3 || v[1] == 3 && v[2] >= 2
            2022 -> v[1] > 1 || v[1] == 1 && v[2] >= 1
            else -> v[0] > 2022
        }
        val encryptionMask = if (newerFlags) 0x1400 else 0x200
        if (flags and encryptionMask != 0) fail("Encrypted UnityFS bundles are unsupported")
        val knownFlags = if (newerFlags) 0x3ff else 0x1ff
        if (flags and knownFlags.inv() != 0) fail("Unknown UnityFS flags")
        if (format >= 7 || v[0] == 2019 && (v[1] > 4 || v[1] == 4 && v[2] >= 15)) r.align(16)
        val dataStart = r.position
        val atEnd = flags and 0x80 != 0
        val infoStart = if (atEnd) bytes.size - packedInfo else dataStart
        if (infoStart < dataStart) fail("Invalid UnityFS information offset")
        val info = ByteArray(plainInfo)
        decode(bytes, infoStart, packedInfo, info, 0, plainInfo, flags and 0x3f)
        r.position = if (atEnd) dataStart else infoStart + packedInfo
        if (newerFlags && flags and 0x200 != 0) r.align(16)
        val dataEnd = if (atEnd) infoStart else bytes.size
        val ir = Reader(info)
        ir.skip(16)
        val blockCount = ir.count("block count", 10)
        var totalPlain = 0L
        var totalPacked = 0L
        val blocks = ArrayList<Block>(blockCount)
        repeat(blockCount) {
            val plain = bounded(ir.u32(), "Uncompressed block")
            val packed = bounded(ir.u32(), "Compressed block")
            val blockFlags = ir.u16()
            if (blockFlags and 0x7f.inv() != 0) fail("Unsupported UnityFS block flags")
            totalPlain += plain; totalPacked += packed
            if (totalPlain + plainInfo > MAX_BYTES) fail("Uncompressed bundle exceeds 128 MiB")
            blocks.add(Block(plain, packed, blockFlags))
        }
        if (r.position.toLong() + totalPacked > dataEnd) fail("Compressed blocks overlap directory or exceed bundle")
        val nodeCount = ir.count("directory count", 21)
        val nodes = ArrayList<Node>(nodeCount)
        repeat(nodeCount) {
            val offset = bounded(ir.i64(), "Directory offset")
            val size = bounded(ir.i64(), "Directory size")
            val nodeFlags = ir.i32()
            val path = ir.cString()
            if (offset.toLong() + size > totalPlain) fail("Directory node exceeds bundle data")
            nodes.add(Node(offset, size, nodeFlags, path))
        }
        val unpacked = ByteArray(totalPlain.toInt())
        var output = 0
        for (block in blocks) {
            interrupted()
            decode(bytes, r.position, block.packed, unpacked, output, block.plain, block.flags and 0x3f)
            r.skip(block.packed); output += block.plain
        }
        val result = ArrayList<TextAsset>()
        var payloadBytes = 0L
        for (node in nodes) {
            interrupted()
            if (node.flags and 4 == 0 && !looksSerialized(unpacked, node.offset, node.size)) continue
            try {
                for (asset in readSerialized(unpacked, node.offset, node.size)) {
                    payloadBytes += asset.bytes.size
                    if (payloadBytes > MAX_BYTES) fail("Extracted TextAssets exceed 128 MiB")
                    result.add(asset)
                }
            } catch (e: IOException) { throw IOException("Unity node '" + node.path + "': " + e.message, e) }
        }
        return result
    }
    private fun looksSerialized(bytes: ByteArray, offset: Int, size: Int): Boolean {
        if (size < 20) return false
        val r = Reader(bytes, offset, size); r.skip(8)
        return r.i32() in 17..22
    }
    private fun readSerialized(bytes: ByteArray, offset: Int, size: Int): List<TextAsset> {
        val r = Reader(bytes, offset, size)
        var metadataSize = r.u32()
        var fileSize = r.u32()
        val version = r.i32()
        var dataOffset = r.u32()
        if (version !in 17..22) fail("Unsupported SerializedFile version: " + version)
        val endian = r.u8()
        if (endian !in 0..1) fail("Invalid SerializedFile byte order")
        r.skip(3)
        if (version >= 22) {
            metadataSize = r.u32(); fileSize = r.i64(); dataOffset = r.i64(); r.skip(8)
        }
        if (fileSize != size.toLong()) fail("SerializedFile size does not match directory")
        val headerSize = r.position
        val metaEnd = headerSize.toLong() + metadataSize
        if (metadataSize < 0 || metaEnd > dataOffset || dataOffset > size || dataOffset < headerSize)
            fail("Invalid SerializedFile metadata bounds")
        r.limit = metaEnd.toInt()
        r.little = endian == 0
        r.cString()
        val target = r.i32()
        if (target == -2) fail("Editor TextAssets are unsupported; select an Android player APK")
        val tree = when (r.u8()) { 0 -> false; 1 -> true; else -> fail("Invalid type-tree flag") }
        val typeCount = r.count("type count", 23)
        val types = IntArray(typeCount)
        repeat(typeCount) { i ->
            interrupted()
            val classId = r.i32()
            types[i] = classId
            r.skip(1); r.skip(2)
            if (classId == 114) r.skip(16)
            r.skip(16)
            if (tree) {
                val nodeCount = r.count("type-tree node count")
                val stringBytes = bounded(r.u32(), "Type-tree string buffer")
                val nodeBytes = if (version >= 19) 32 else 24
                r.skipLong(nodeCount.toLong() * nodeBytes + stringBytes)
                if (version >= 21) {
                    val dependencies = r.count("type dependency count", 4)
                    r.skipLong(dependencies.toLong() * 4)
                }
            }
        }
        val objectCount = r.count("object count", if (version >= 22) 24 else 20)
        val entries = ArrayList<Entry>()
        repeat(objectCount) {
            interrupted()
            r.align(4); r.skip(8)
            val relative = if (version >= 22) r.i64() else r.u32()
            val byteSize = bounded(r.u32(), "Object size")
            val type = r.i32()
            if (type !in types.indices) fail("Object refers to a missing serialized type")
            if (relative < 0 || relative > fileSize - dataOffset || byteSize > fileSize - dataOffset - relative)
                fail("Object exceeds SerializedFile")
            if (types[type] == 49) entries.add(Entry((dataOffset + relative).toInt(), byteSize, type))
        }
        var extractedBytes = 0L
        return entries.map { entry ->
            interrupted()
            val obj = Reader(bytes, offset, size)
            obj.little = r.little
            obj.position = entry.start
            obj.limit = entry.start + entry.size
            val nameSize = obj.count("TextAsset name length", 1, MAX_NAME_BYTES)
            val name = obj.bytes(nameSize).toString(Charsets.UTF_8)
            obj.align(4)
            val payloadSize = bounded(obj.u32(), "TextAsset payload")
            extractedBytes += payloadSize
            if (extractedBytes > MAX_BYTES) fail("Extracted TextAssets exceed 128 MiB")
            TextAsset(name, obj.bytes(payloadSize))
        }
    }
    private fun decode(input: ByteArray, inputOffset: Int, packed: Int,
                       output: ByteArray, outputOffset: Int, plain: Int, codec: Int) {
        if (inputOffset < 0 || packed < 0 || inputOffset > input.size - packed)
            fail("Compressed data exceeds its containing file")
        if (outputOffset < 0 || plain < 0 || outputOffset > output.size - plain)
            fail("Uncompressed data exceeds its destination")
        try {
            when (codec) {
                0 -> {
                    if (packed != plain) fail("Uncompressed block size mismatch")
                    System.arraycopy(input, inputOffset, output, outputOffset, plain)
                }
                2, 3 -> {
                    val written = LZ4Factory.safeInstance().safeDecompressor()
                        .decompress(input, inputOffset, packed, output, outputOffset, plain)
                    if (written != plain) fail("LZ4 output size mismatch")
                }
                1 -> {
                    if (packed < 5) fail("Truncated Unity LZMA properties")
                    val props = input[inputOffset]
                    var dictionary = 0L
                    for (i in 0..3)
                        dictionary = dictionary or ((input[inputOffset + 1 + i].toLong() and 255) shl (8 * i))
                    if (dictionary > MAX_DICTIONARY) fail("LZMA dictionary exceeds 64 MiB")
                    val source = ByteArrayInputStream(input, inputOffset + 5, packed - 5)
                    LZMAInputStream(source, plain.toLong(), props, dictionary.toInt()).use { lzma ->
                        lzma.enableRelaxedEndCondition()
                        var done = 0
                        while (done < plain) {
                            interrupted()
                            val got = lzma.read(output, outputOffset + done, minOf(64 * 1024, plain - done))
                            if (got <= 0) fail("Truncated LZMA output")
                            done += got
                        }
                        if (lzma.read() != -1) fail("LZMA output exceeds declared size")
                    }
                }
                else -> fail("Unsupported Unity compression type: " + codec)
            }
        } catch (e: IOException) { throw e }
        catch (e: RuntimeException) { throw IOException("Invalid compressed Unity data", e) }
    }
    private class Reader(private val data: ByteArray, private val base: Int = 0, private val length: Int = data.size) {
        var position: Int = 0
            set(value) {
                if (value < 0 || value > limit) fail("Read position exceeds Unity data")
                field = value
            }
        var limit: Int = length
            set(value) {
                if (value < position || value > length || value < 0) fail("Invalid Unity read boundary")
                field = value
            }
        var little: Boolean = false
        init { if (base < 0 || length < 0 || base > data.size - length) fail("Invalid Unity file slice") }
        private fun need(n: Int) {
            if (n < 0 || n > limit - position) fail("Truncated Unity data at byte " + position)
        }
        fun skip(n: Int) { need(n); position += n }
        fun skipLong(n: Long) { skip(bounded(n, "Unity table")) }
        fun align(n: Int) { skip((n - position % n) % n) }
        fun u8(): Int { need(1); return data[base + position++].toInt() and 255 }
        fun u16(): Int {
            val a = u8(); val b = u8()
            return if (little) a or (b shl 8) else (a shl 8) or b
        }
        fun i32(): Int {
            var value = 0
            if (little) repeat(4) { value = value or (u8() shl (it * 8)) }
            else repeat(4) { value = (value shl 8) or u8() }
            return value
        }
        fun u32(): Long = i32().toLong() and 0xffffffffL
        fun i64(): Long {
            var value = 0L
            if (little) repeat(8) { value = value or (u8().toLong() shl (it * 8)) }
            else repeat(8) { value = (value shl 8) or u8().toLong() }
            return value
        }
        fun bytes(n: Int): ByteArray {
            need(n); val from = base + position; position += n
            return data.copyOfRange(from, from + n)
        }
        fun cString(): String {
            val start = position
            while (position < limit && data[base + position] != 0.toByte()) {
                if (position - start >= MAX_NAME_BYTES) fail("Unity string exceeds 64 KiB")
                position++
            }
            if (position == limit) fail("Unterminated Unity string")
            val value = String(data, base + start, position - start, Charsets.UTF_8)
            position++
            return value
        }
        fun count(label: String, itemBytes: Int = 1, maximum: Int = MAX_COUNT): Int {
            val count = i32()
            if (count < 0 || count > maximum || count.toLong() * itemBytes > limit - position)
                fail("Invalid " + label + ": " + count)
            return count
        }
    }
    companion object {
        const val MAX_BYTES: Int = 128 * 1024 * 1024
        private const val MAX_DICTIONARY: Int = 64 * 1024 * 1024
        private const val MAX_COUNT: Int = 1_000_000
        private const val MAX_NAME_BYTES: Int = 64 * 1024
        private val UNITY_FS = byteArrayOf(85, 110, 105, 116, 121, 70, 83, 0)
        private fun bounded(value: Long, label: String): Int {
            if (value < 0 || value > MAX_BYTES) fail(label + " exceeds 128 MiB or is negative")
            return value.toInt()
        }
        private fun interrupted() {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Unity extraction cancelled")
        }
        private fun fail(message: String): Nothing = throw IOException(message)
    }
}
