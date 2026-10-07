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
    @Test fun nonlinearEventsUseSineOut() {
        val r = rpe(); line(r).getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents",
            JSONArray().put(event(0, 8, 0.0, 270.0).put("easingType", 2)))
        val c = PhiraChartParser.parse(r.toString())
        assertEquals(1026.2742f, c.position(c.notes.single(), 2.0, Viewport(0f, 0f, 1600f, 900f))!!.x, 0.01f)
    }
    @Test fun parentCyclesRejected() { val r = rpe(); line(r).put("father", 0); rejects(r, "循环") }
    @Test fun yOffsetDoesNotChangeJudgeCoordinates() {
        val r = rpe(); line(r).getJSONArray("notes").getJSONObject(0).put("yOffset", 1000)
        val c = PhiraChartParser.parse(r.toString()); val p = c.position(c.notes.single(), 2.0, Viewport(0f, 0f, 1600f, 900f))!!
        assertEquals(800f, p.x, 0.01f); assertEquals(450f, p.y, 0.01f)
    }
    @Test fun editorIdentityDefaultsAccepted() {
        val r = rpe(); line(r).put("posControl", JSONArray().put(JSONObject().put("pos", 1).put("x", 0)))
            .put("extended", JSONObject().put("scaleXEvents", JSONArray().put(event(0, 8, 1.0, 1.0))))
        assertEquals(1, PhiraChartParser.parse(r.toString()).notes.size)
    }
    @Test fun renderControlsDoNotChangeJudgeCoordinates() {
        val r = rpe(); line(r).put("posControl", JSONArray().put(JSONObject().put("pos", 2)))
            .put("attachUI", "score").put("extended", JSONObject().put("scaleXEvents", JSONArray().put(event(0, 8, 2.0, 3.0))))
        val c = PhiraChartParser.parse(r.toString())
        assertEquals(800f, c.position(c.notes.single(), 2.0, Viewport(0f, 0f, 1600f, 900f))!!.x, 0.01f)
    }
    @Test fun duplicateBpmRejected() {
        val r = rpe(); r.getJSONArray("BPMList").put(JSONObject().put("bpm", 100).put("startTime", triple(0)))
        rejects(r, "重复")
    }
    @Test fun zeroDenominatorRejected() {
        val r = rpe(); line(r).getJSONArray("notes").getJSONObject(0)
            .put("startTime", JSONArray().put(1).put(1).put(0)); rejects(r, "分母")
    }
    @Test fun unknownRenderingFieldIsIgnored() { assertEquals(1, PhiraChartParser.parse(pgr().put("blockAreaList", JSONArray().put(JSONObject())).toString()).notes.size) }
    @Test fun nestingRejectedBeforeParsing() {
        val text = "{\"nested\":" + "[".repeat(65) + "0" + "]".repeat(65) + "}"
        assertTrue(runCatching { PhiraChartParser.parse(text) }.exceptionOrNull()?.message.orEmpty().contains("嵌套"))
    }
    @Test fun nonHoldIgnoresPlaceholderEndTime() {
        val r = rpe(); line(r).put("notes", JSONArray().put(note(1, 4, 0)).put(note(3, 5, 0)).put(note(4, 6, 0)))
        assertEquals(listOf(2.0, 2.5, 3.0), PhiraChartParser.parse(r.toString()).notes.map { it.timeSeconds })
    }
    @Test fun futureBezierTrackExtrapolatesLikePhira() {
        val r = rpe(); line(r).put("notes", JSONArray().put(note(1, 2)))
        line(r).getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents",
            JSONArray().put(event(4, 12, 0.0, 270.0).put("bezier", 1)
                .put("bezierPoints", JSONArray().put(0.25).put(0.1).put(0.25).put(1.0))))
        val c = PhiraChartParser.parse(r.toString())
        assertEquals(832.5f, c.position(c.notes.single(), 1.0, Viewport(0f, 0f, 1600f, 900f))!!.x, 0.01f)
    }
    @Test fun bezierMotionUsesCubicXInversion() {
        val r = rpe(); line(r).getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents",
            JSONArray().put(event(0, 8, 0.0, 270.0).put("bezier", 1)
                .put("bezierPoints", JSONArray().put(0.25).put(0.1).put(0.25).put(1.0))))
        val c = PhiraChartParser.parse(r.toString())
        assertEquals(1056.769f, c.position(c.notes.single(), 2.0, Viewport(0f, 0f, 1600f, 900f))!!.x, 0.01f)
    }
    @Test fun croppedEasingRenormalizesTheOutputRange() {
        val r = rpe(); line(r).getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents",
            JSONArray().put(event(0, 8, 0.0, 270.0).put("easingType", 5).put("easingLeft", 0.25).put("easingRight", 0.75)))
        val c = PhiraChartParser.parse(r.toString())
        assertEquals(920f, c.position(c.notes.single(), 2.0, Viewport(0f, 0f, 1600f, 900f))!!.x, 0.01f)
    }
    @Test fun allRpeEasingTypesHaveFiniteMotion() {
        for (easing in 1..29) {
            val r = rpe(); line(r).getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents",
                JSONArray().put(event(0, 8, 0.0, 135.0).put("easingType", easing)))
            val c = PhiraChartParser.parse(r.toString())
            val p = c.position(c.notes.single(), 1.0, Viewport(0f, 0f, 1600f, 900f))!!
            assertTrue(p.x.isFinite()); assertTrue(p.y.isFinite())
        }
    }
    @Test fun parentPositionRotatesEvenWithoutRotationInheritance() {
        val r = rpe(); val parent = line(r)
        val child = JSONObject(parent.toString()).put("father", 0).put("rotateWithFather", false)
        parent.put("notes", JSONArray())
        parent.getJSONArray("eventLayers").getJSONObject(0)
            .put("moveXEvents", JSONArray().put(event(0, 8, 135.0, 135.0)))
            .put("moveYEvents", JSONArray().put(event(0, 8, 90.0, 90.0)))
            .put("rotateEvents", JSONArray().put(event(0, 8, -90.0, -90.0)))
        child.getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents", JSONArray().put(event(0, 8, 135.0, 135.0)))
        child.put("notes", JSONArray().put(note(1, 4, x = 135.0)))
        r.getJSONArray("judgeLineList").put(child)
        val v = Viewport(0f, 0f, 1600f, 900f); var c = PhiraChartParser.parse(r.toString())
        var p = c.position(c.notes.single(), 2.0, v)!!
        assertEquals(1120f, p.x, 0.01f); assertEquals(200f, p.y, 0.01f)
        child.put("rotateWithFather", true); c = PhiraChartParser.parse(r.toString()); p = c.position(c.notes.single(), 2.0, v)!!
        assertEquals(960f, p.x, 0.01f); assertEquals(40f, p.y, 0.01f)
    }
    @Test fun rpeWithoutMetaUsesZeroOffset() {
        val r = rpe(); r.remove("META"); assertEquals(0.0, PhiraChartParser.parse(r.toString()).offsetSeconds, 1e-9)
    }
    @Test fun nativeNormalizationRoundTripsWithOneInfoOffset() {
        val json = PhiraChartParser.normalizeJson(rpe().toString(), 0.125)
        assertTrue(JSONObject(json).has("phiscriptFormat")); val c = PhiraChartParser.parse(json)
        assertEquals(0.275, c.offsetSeconds, 1e-9); assertEquals(2.0, c.notes.single().timeSeconds, 1e-9)
    }
    @Test fun eventJumpPreservesThePrecedingRamp() {
        val r = rpe(); line(r).getJSONArray("eventLayers").getJSONObject(0).put("moveXEvents",
            JSONArray().put(event(0, 4, 0.0, 135.0)).put(event(4, 8, 270.0, 270.0)))
        val c = PhiraChartParser.parse(r.toString()); val v = Viewport(0f, 0f, 1600f, 900f)
        assertEquals(880f, c.position(c.notes.single(), 1.0, v)!!.x, 0.01f)
        assertEquals(1120f, c.position(c.notes.single(), 2.0, v)!!.x, 0.01f)
    }
    @Test fun firstEmptyRpeTrackSuppressesLaterLayers() {
        val r = rpe(); line(r).put("eventLayers", JSONArray().put(JSONObject().put("moveXEvents", JSONArray()))
            .put(JSONObject().put("moveXEvents", JSONArray().put(event(0, 8, 135.0, 135.0)))))
        val c = PhiraChartParser.parse(r.toString())
        assertEquals(800f, c.position(c.notes.single(), 2.0, Viewport(0f, 0f, 1600f, 900f))!!.x, 0.01f)
    }
}
