package io.github.phiscript.assets
import io.github.phiscript.engine.Viewport
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhiraChartParserTest {
    private fun triple(b: Int) = JSONArray().put(b).put(0).put(1)
    private fun event(a: Int, b: Int, from: Double, to: Double) = JSONObject()
        .put("startTime", triple(a)).put("endTime", triple(b)).put("start", from).put("end", to).put("easingType", 1)
    private fun note(type: Int, beat: Int, end: Int = beat, x: Double = 0.0) = JSONObject()
        .put("type", type).put("startTime", triple(beat)).put("endTime", triple(end))
        .put("positionX", x).put("above", 1).put("yOffset", 0).put("isFake", 0)
    private fun rpe() = JSONObject()
        .put("META", JSONObject().put("offset", 150).put("name", "Test Song").put("level", "SP Lv.12"))
        .put("BPMList", JSONArray().put(JSONObject().put("bpm", 120).put("startTime", triple(0))))
        .put("judgeLineList", JSONArray().put(JSONObject().put("father", -1)
            .put("eventLayers", JSONArray().put(JSONObject()
                .put("moveXEvents", JSONArray().put(event(0, 16, 0.0, 0.0)))
                .put("moveYEvents", JSONArray().put(event(0, 16, 0.0, 0.0)))
                .put("rotateEvents", JSONArray().put(event(0, 16, 0.0, 0.0)))
                .put("alphaEvents", JSONArray().put(event(0, 16, 255.0, 255.0)))))
            .put("notes", JSONArray().put(note(1, 4)))))
    private fun line(root: JSONObject) = root.getJSONArray("judgeLineList").getJSONObject(0)
    private fun pgr(version: Int = 3) = JSONObject().put("formatVersion", version).put("offset", 0.0)
        .put("judgeLineList", JSONArray().put(JSONObject().put("bpm", 120)
            .put("judgeLineMoveEvents", JSONArray().put(JSONObject().put("startTime", 0).put("endTime", 10000)
                .put("start", if (version == 1) 440260 else 0.5).put("end", if (version == 1) 440260 else 0.5)
                .put("start2", 0.5).put("end2", 0.5)))
            .put("judgeLineRotateEvents", JSONArray()).put("judgeLineDisappearEvents", JSONArray())
            .put("speedEvents", JSONArray().put(JSONObject().put("startTime", 0).put("endTime", 10000).put("value", 1)))
            .put("notesAbove", JSONArray().put(JSONObject().put("type", 1).put("time", 32).put("holdTime", 0)
                .put("positionX", 0).put("speed", 1).put("floorPosition", 0.5))).put("notesBelow", JSONArray())))
    private fun rejects(root: JSONObject, fragment: String) {
        val e = runCatching { PhiraChartParser.parse(root.toString()) }.exceptionOrNull()
        assertNotNull(e); assertTrue(e!!.message.orEmpty().contains(fragment))
    }
    @Test fun pgrTicksAndInfoOffset() {
        val c = PhiraChartParser.parse(pgr().toString(), 0.125)
        assertEquals(0.5, c.notes.single().timeSeconds, 1e-9); assertEquals(0.125, c.offsetSeconds, 1e-9)
    }
    @Test fun pgr1PackedCoordinates() {
        val c = PhiraChartParser.parse(pgr(1).toString())
        val pt = c.position(c.notes.single(), 0.5, Viewport(0f, 0f, 1600f, 900f))!!
        assertEquals(800f, pt.x, 0.01f); assertEquals(450f, pt.y, 0.01f)
    }
    @Test fun rpeBeatsAndMillisecondOffset() {
        val c = PhiraChartParser.parse(rpe().toString(), -0.025)
        assertEquals(2.0, c.notes.single().timeSeconds, 1e-9); assertEquals(0.125, c.offsetSeconds, 1e-9)
    }
    @Test fun changingBpmIntegratesHoldDuration() {
        val r = rpe(); r.getJSONArray("BPMList").put(JSONObject().put("bpm", 60).put("startTime", triple(4)))
        line(r).put("notes", JSONArray().put(note(2, 2, 6)))
        val n = PhiraChartParser.parse(r.toString()).notes.single()
        assertEquals(1.0, n.timeSeconds, 1e-9); assertEquals(4.0, n.endSeconds, 1e-9)
    }
    @Test fun mapsFourTypes() {
        val r = rpe(); line(r).put("notes", JSONArray().put(note(1, 1)).put(note(2, 2, 3))
            .put(note(3, 4)).put(note(4, 5)))
        assertEquals(listOf(1, 3, 4, 2), PhiraChartParser.parse(r.toString()).notes.map { it.type })
    }
    @Test fun positionUnitsAndLineMovement() {
        val r = rpe(); line(r).put("notes", JSONArray().put(note(1, 4, x = 135.0)))
        line(r).getJSONArray("eventLayers").getJSONObject(0)
            .put("moveXEvents", JSONArray().put(event(0, 8, 0.0, 270.0)))
        val c = PhiraChartParser.parse(r.toString()); val pt = c.position(c.notes.single(), 2.0, Viewport(0f, 0f, 1600f, 900f))!!
        assertEquals(1120f, pt.x, 0.01f); assertEquals(450f, pt.y, 0.01f)
    }
    @Test fun multipleLayersSumMotion() {
        val r = rpe(); line(r).getJSONArray("eventLayers").put(JSONObject()
            .put("moveXEvents", JSONArray().put(event(0, 8, 135.0, 135.0))))
        val c = PhiraChartParser.parse(r.toString())
        assertEquals(960f, c.position(c.notes.single(), 2.0, Viewport(0f, 0f, 1600f, 900f))!!.x, 0.01f)
    }
    @Test fun fakeNotesExcluded() {
        val r = rpe(); line(r).getJSONArray("notes").put(note(1, 1).put("isFake", 1))
        assertEquals(1, PhiraChartParser.parse(r.toString()).notes.size)
    }
    @Test fun eventJumpRemainsInstantaneous() {
        val r = rpe(); line(r).getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents",
            JSONArray().put(event(0, 4, 0.0, 0.0)).put(event(4, 8, 270.0, 270.0)))
        val c = PhiraChartParser.parse(r.toString()); val v = Viewport(0f, 0f, 1600f, 900f)
        assertEquals(800f, c.position(c.notes.single(), 1.999, v)!!.x, 0.01f)
        assertEquals(1120f, c.position(c.notes.single(), 2.0, v)!!.x, 0.01f)
    }
    @Test fun nonlinearEventsRejected() {
        val r = rpe(); line(r).getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents",
            JSONArray().put(event(0, 8, 0.0, 270.0).put("easingType", 2))); rejects(r, "非线性")
    }
    @Test fun parentLinesRejected() { val r = rpe(); line(r).put("father", 0); rejects(r, "父子") }
    @Test fun yOffsetRejected() {
        val r = rpe(); line(r).getJSONArray("notes").getJSONObject(0).put("yOffset", 1); rejects(r, "yOffset")
    }
    @Test fun editorIdentityDefaultsAccepted() {
        val r = rpe(); line(r).put("posControl", JSONArray().put(JSONObject().put("pos", 1).put("x", 0)))
            .put("extended", JSONObject().put("scaleXEvents", JSONArray().put(event(0, 8, 1.0, 1.0))))
        assertEquals(1, PhiraChartParser.parse(r.toString()).notes.size)
    }
    @Test fun alteredControlsRejected() {
        val r = rpe(); line(r).put("posControl", JSONArray().put(JSONObject().put("pos", 2))); rejects(r, "非默认")
    }
    @Test fun duplicateBpmRejected() {
        val r = rpe(); r.getJSONArray("BPMList").put(JSONObject().put("bpm", 100).put("startTime", triple(0)))
        rejects(r, "重复")
    }
    @Test fun zeroDenominatorRejected() {
        val r = rpe(); line(r).getJSONArray("notes").getJSONObject(0)
            .put("startTime", JSONArray().put(1).put(1).put(0)); rejects(r, "分母")
    }
    @Test fun noiseAreaRejected() { rejects(pgr().put("blockAreaList", JSONArray().put(JSONObject())), "噪域") }
    @Test fun nestingRejectedBeforeParsing() {
        val text = "{\"nested\":" + "[".repeat(65) + "0" + "]".repeat(65) + "}"
        assertTrue(runCatching { PhiraChartParser.parse(text) }.exceptionOrNull()?.message.orEmpty().contains("嵌套"))
    }
    @Test fun nonHoldIgnoresPlaceholderEndTime() {
        val r = rpe(); line(r).put("notes", JSONArray().put(note(1, 4, 0)).put(note(3, 5, 0)).put(note(4, 6, 0)))
        assertEquals(listOf(2.0, 2.5, 3.0), PhiraChartParser.parse(r.toString()).notes.map { it.timeSeconds })
    }
}
