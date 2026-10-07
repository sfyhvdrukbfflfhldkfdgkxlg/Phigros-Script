package io.github.phiscript.assets
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PhiraImportDecoderTest {
    private fun triple(b: Int) = JSONArray().put(b).put(0).put(1)
    private fun chart(title: String = "Real Song", level: String = "SP Lv.12") = JSONObject()
        .put("META", JSONObject().put("name", title).put("level", level).put("offset", 100))
        .put("BPMList", JSONArray().put(JSONObject().put("bpm", 120).put("startTime", triple(0))))
        .put("judgeLineList", JSONArray().put(JSONObject().put("father", -1).put("eventLayers", JSONArray())
            .put("notes", JSONArray().put(JSONObject().put("type", 1).put("above", 1).put("positionX", 0)
                .put("startTime", triple(4)).put("endTime", triple(4)))))).toString()
    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in files) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }; return out.toByteArray()
    }
    private fun decode(bytes: ByteArray) = PhiraImportDecoder.decode(ByteArrayInputStream(bytes), "test.pez")
    private fun rejects(bytes: ByteArray, fragment: String) {
        val e = runCatching { decode(bytes) }.exceptionOrNull()
        assertNotNull(e); assertTrue(e!!.message.orEmpty().contains(fragment))
    }
    @Test fun bareRpeMetadata() {
        val d = decode(chart().toByteArray()); assertEquals("Real Song", d.title); assertEquals("SP Lv.12", d.level)
        assertEquals(1, JSONObject(d.normalizedJson).getInt("phiscriptFormat"))
    }
    @Test fun yamlOverridesStaleMetaAndAddsOffset() {
        val d = decode(zip("info.yml" to "name: \"YAML Song\"\nlevel: 'IN Lv.15'\nchart: chart.json\noffset: 0.025\n",
            "chart.json" to chart("Stale META", "0"), "song.mp3" to "ignored audio"))
        assertEquals("YAML Song", d.title); assertEquals("IN Lv.15", d.level)
        assertEquals(0.125, PhiraChartParser.parse(d.normalizedJson).offsetSeconds, 1e-9)
    }
    @Test fun resolvesRelativeManifestChart() {
        val d = decode(zip("folder/info.yml" to "name: Nested\nlevel: EZ Lv.2\nchart: chart.json", "folder/chart.json" to chart()))
        assertEquals("Nested", d.title)
    }
    @Test fun legacyInfoSupportsCustomLevel() {
        val d = decode(zip("info.txt" to "#\nName: Legacy Song\nLevel: EXPERT 15\nChart: chart.json\n", "chart.json" to chart()))
        assertEquals("Legacy Song", d.title); assertEquals("EXPERT 15", d.level)
    }
    @Test fun missingLevelUsesManualFallback() { assertEquals("未知难度", decode(chart(level = "").toByteArray()).level) }
    @Test fun missingNameUsesFilename() { assertEquals("test", decode(chart(title = "").toByteArray()).title) }
    @Test fun archiveTraversalRejected() { rejects(zip("../chart.json" to chart()), "不安全路径") }
    @Test fun referenceTraversalRejected() {
        rejects(zip("folder/info.yml" to "name: Song\nlevel: IN15\nchart: ../chart.json", "chart.json" to chart()), "不安全路径")
    }
    @Test fun unreferencedMultipleChartsStayAmbiguous() { rejects(zip("one.json" to chart(), "two.json" to chart("Second")), "唯一") }
    @Test fun manifestSelectsSpecificChart() {
        assertEquals("Song", decode(zip("info.yml" to "name: Song\nlevel: IN15\nchart: two.json",
            "one.json" to chart(), "two.json" to chart("Second"))).title)
    }
    @Test fun pecArchiveImports() {
        val d = decode(zip("info.yml" to "name: Song\nlevel: IN15\nchart: chart.pec", "chart.pec" to PhiraCodecFixtures.PEC))
        assertEquals(4, PhiraChartParser.parse(d.normalizedJson).notes.size)
    }
    @Test fun metadataQuotingAndComments() {
        val m = PhiraImportDecoder.readMetadata("name: \"A \\\"Quoted\\\" Song\" # comment\nlevel: 'IN''15'\nchart: chart.json # note\n")
        assertEquals("A \"Quoted\" Song", m["name"]); assertEquals("IN'15", m["level"]); assertEquals("chart.json", m["chart"])
    }
    @Test fun duplicateMetadataRejected() {
        assertTrue(runCatching { PhiraImportDecoder.readMetadata("name: A\nname: B") }.exceptionOrNull()?.message.orEmpty().contains("重复字段"))
    }
    @Test fun customAspectPreserved() {
        assertEquals(1.0, decode(zip("info.yml" to "name: Song\nlevel: IN15\nchart: chart.json\naspectRatio: 1.0", "chart.json" to chart())).aspectRatio, 1e-9)
    }
    @Test fun boundedReadDoesNotAcceptPrefix() {
        assertTrue(runCatching { PhiraImportDecoder.readBounded(ByteArrayInputStream(ByteArray(11)), 10) }
            .exceptionOrNull()?.message.orEmpty().contains("大小限制"))
    }
    @Test fun unsafeNamesRejected() {
        for (n in listOf("/chart.json", "C:/chart.json", "folder\\chart.json", "folder//chart.json"))
            assertNotNull(runCatching { PhiraImportDecoder.safeName(n) }.exceptionOrNull())
    }
}
