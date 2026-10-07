package io.github.phiscript.assets
import io.github.phiscript.engine.Chart
import io.github.phiscript.engine.Viewport
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object PhiraCodecFixtures {
    const val PEC = "250\nbp 0 120\ncv 0 0 5.85\ncp 0 0 1024 700\ncd 0 0 0\nca 0 0 255\n" +
        "n1 0 2 0 1 0 # 1.5 & 2\nn2 0 4 6 256 2 0\nn3 0 8 -256 1 0\nn4 0 10 0 1 0\nn1 0 12 0 1 1"
    fun pbc(fake: Boolean = false, clamp: Boolean = false, emptyFirst: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        fun b(value: Int) { out.write(value) }
        fun f(value: Float) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()) }
        fun u(value: Int) {
            var remaining = value
            do { val next = remaining and 127; remaining = remaining ushr 7; b(next or if (remaining != 0) 128 else 0) }
            while (remaining != 0)
        }
        fun empty() { b(1); b(0) }
        fun fixed(value: Float, timeMs: Int = 0, crop: Boolean = false, prependEmpty: Boolean = false) {
            if (prependEmpty) b(1)
            b(2); u(1); u(timeMs); f(value)
            if (crop) { b(130); f(0.2f); f(0.8f) } else b(0)
            b(0)
        }
        f(0.125f); u(1)
        repeat(3) { empty() }; empty(); fixed(0.1f); fixed(0.2f)
        b(0); empty(); u(1)
        repeat(3) { empty() }; empty(); fixed(0.25f, 400, clamp, emptyFirst); empty()
        b(1); f(2.25f); f(3f); u(600); f(1f); b(1); f(2f); b(0); b(if (fake) 1 else 0)
        b(2); u(1); u(0); b(10); b(20); b(30); b(255); b(0); b(0)
        u(0); b(0); b(0); b(8); repeat(5) { empty() }
        out.write(byteArrayOf(12, 0, 0, 0)); b(1); b(0)
        return out.toByteArray()
    }
}
class PhiraCodecTest {
    private val viewport = Viewport(0f, 0f, 1000f, 600f)
    private fun pec(text: String): Chart = Chart.parse(PhiraPecDecoder.toJson(text))
    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { archive ->
            for ((name, bytes) in files) { archive.putNextEntry(ZipEntry(name)); archive.write(bytes); archive.closeEntry() }
        }; return out.toByteArray()
    }
    @Test fun pecOfficialOffsetTypesAndFakeNotes() {
        val c = pec(PhiraCodecFixtures.PEC)
        assertEquals(0.1, c.offsetSeconds, 1e-9); assertEquals(listOf(1, 3, 4, 2), c.notes.map { it.type })
        assertEquals(listOf(1.0, 2.0, 4.0, 5.0), c.notes.map { it.timeSeconds })
        assertEquals(3.0, c.notes[1].endSeconds, 1e-9); assertFalse(c.notes[1].above)
    }
    @Test fun pecVariableBpmUsesPrefixClock() {
        val c = pec("0\nbp 0 120\nbp 4 240\nbp 8 60\ncp 0 0 1024 700\nn1 0 10 0 1 0")
        assertEquals(5.0, c.firstNoteSeconds, 1e-9)
    }
    @Test fun pecDuplicateTempoUsesLastAndNonOneAboveIsBelow() {
        val c = pec("0\nbp 0 120\nbp 0 240\ncp 0 0 1024 700\nn1 0 4 0 0 0")
        assertEquals(1.0, c.firstNoteSeconds, 1e-9); assertFalse(c.notes.single().above)
    }
    @Test fun pecEasingChangesRealJudgeCoordinate() {
        val c = pec("0\nbp 0 120\ncp 0 0 1024 700\ncm 0 0 4 2048 700 3\nn1 0 1 0 1 0")
        val p = c.position(c.notes.single(), 0.5, viewport)!!
        assertEquals(538.0602, p.x.toDouble(), 0.002); assertEquals(300f, p.y, 0.002f)
    }
    @Test fun pecRotationUsesUpstreamSign() {
        val c = pec("0\nbp 0 120\ncp 0 0 1024 700\ncd 0 0 90\nn1 0 2 512 1 0")
        val p = c.position(c.notes.single(), 1.0, viewport)!!
        assertEquals(500f, p.x, 0.002f); assertEquals(550f, p.y, 0.002f)
    }
    @Test fun pecUnknownCommandAndInterpolationWithoutStartFail() {
        for (s in listOf("0\nbp 0 120\nxx 1", "0\nbp 0 120\ncm 0 0 4 1024 700 1\nn1 0 2 0 1 0"))
            assertNotNull(runCatching { PhiraPecDecoder.toJson(s) }.exceptionOrNull())
    }
    @Test fun pbcLittleEndianAndInheritedAnimationClock() {
        val c = Chart.parse(PhiraPbcDecoder.toJson(PhiraCodecFixtures.pbc())); val n = c.notes.single()
        assertEquals(0.125, c.offsetSeconds, 1e-9); assertEquals(3, n.type); assertEquals(1.0, n.timeSeconds, 1e-9)
        assertEquals(2.25, n.endSeconds, 1e-9); assertEquals(2.0, n.speed, 1e-9); assertFalse(n.above)
        val p = c.position(n, 1.0, viewport)!!; assertEquals(675f, p.x, 0.002f); assertEquals(240f, p.y, 0.002f)
    }
    @Test fun pbcEmptyFirstAnimationKeepsTheClockButIgnoresNextLayer() {
        val c = Chart.parse(PhiraPbcDecoder.toJson(PhiraCodecFixtures.pbc(emptyFirst = true)))
        val n = c.notes.single()
        assertEquals(1.0, n.timeSeconds, 1e-9)
        assertEquals(550f, c.position(n, 1.0, viewport)!!.x, 0.002f)
    }
    @Test fun pbcClampedTweenFieldsPreserved() {
        val json = JSONObject(PhiraPbcDecoder.toJson(PhiraCodecFixtures.pbc(clamp = true)))
        val t = json.getJSONArray("lines").getJSONObject(0).getJSONArray("notes").getJSONObject(0)
            .getJSONObject("object").getJSONArray("x").getJSONArray(0).getJSONObject(0).getJSONObject("tween")
        assertEquals(2, t.getInt("id")); assertEquals(0.2, t.getDouble("left"), 1e-7); assertEquals(0.8, t.getDouble("right"), 1e-7)
    }
    @Test fun pbcTruncationUnknownTrailerAndOversizedUlebFail() {
        val v = PhiraCodecFixtures.pbc()
        for (bad in listOf(v.copyOf(v.size - 1), v + byteArrayOf(0), byteArrayOf(0, 0, 0, 0, 0xff.toByte(), 0xff.toByte(), 0x7f))) {
            val e = runCatching { PhiraPbcDecoder.toJson(bad) }.exceptionOrNull()
            assertNotNull(e); assertTrue(e!!.message.orEmpty().contains("PBC"))
        }
    }
    @Test fun standalonePecAndPbcUseFilenameAndUnknownLevel() {
        val a = PhiraImportDecoder.decode(ByteArrayInputStream(PhiraCodecFixtures.PEC.toByteArray()), "Readable Song.pec")
        assertEquals("Readable Song", a.title); assertEquals("未知难度", a.level)
        val b = PhiraImportDecoder.decode(ByteArrayInputStream(PhiraCodecFixtures.pbc()), "Binary Song.pbc")
        assertEquals("Binary Song", b.title); assertEquals("未知难度", b.level)
    }
    @Test fun archiveAcceptsPbcMetadataFormatAndCustomAspect() {
        val m = "name: Binary\nlevel: SP 15\nchart: chart.bin\nformat: pbc\naspectRatio: 1.25\nforceAspectRatio: true\noffset: 0.025"
        val d = PhiraImportDecoder.decode(ByteArrayInputStream(zip("info.yml" to m.toByteArray(), "chart.bin" to PhiraCodecFixtures.pbc())), "file.pez")
        assertEquals("Binary", d.title); assertEquals(1.25, d.aspectRatio, 1e-9); assertTrue(d.forceAspectRatio)
        assertEquals(0.15, Chart.parse(d.normalizedJson).offsetSeconds, 1e-9)
    }
    @Test fun nullFormatDetectsBinaryChartByContent() {
        val m = "name: Auto\nlevel: IN 14\nchart: chart.bin\nformat: null"
        val d = PhiraImportDecoder.decode(ByteArrayInputStream(zip("info.yml" to m.toByteArray(), "chart.bin" to PhiraCodecFixtures.pbc())), "file.pez")
        assertEquals("Auto", d.title); assertEquals(1.0, Chart.parse(d.normalizedJson).firstNoteSeconds, 1e-9)
    }
    @Test fun legacyChineseMetadataDecodesGb18030() {
        val m = "Name: 中文曲名\nLevel: IN 14\nChart: chart.pec"
        val d = PhiraImportDecoder.decode(ByteArrayInputStream(zip("info.txt" to m.toByteArray(Charset.forName("GB18030")),
            "chart.pec" to PhiraCodecFixtures.PEC.toByteArray())), "file.pez")
        assertEquals("中文曲名", d.title); assertEquals("IN 14", d.level)
    }
}
