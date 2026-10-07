package io.github.phiscript.engine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhiraNativeChartTest {
    private val viewport = Viewport(0f, 0f, 1600f, 900f)
    private fun frame(time: Double, value: Double, id: Int = 0) = JSONObject()
        .put("time", time).put("value", value).put("tween", JSONObject().put("id", id))
    private fun fixed(value: Double) = JSONArray().put(JSONArray().put(frame(0.0, value)))
    private fun animated() = JSONArray().put(JSONArray().put(frame(0.0, 0.0, 2)).put(frame(4.0, 0.4)))
    private fun note(type: Int = 1) = JSONObject().put("type", type).put("time", 1.0).put("endTime", 3.0)
        .put("object", JSONObject().put("x", fixed(0.0))).put("above", true).put("fake", false)
    private fun line() = JSONObject().put("object", JSONObject().put("x", fixed(0.0))
        .put("y", fixed(0.0)).put("rotation", fixed(0.0))).put("parent", -1).put("rotWithParent", false)
        .put("notes", JSONArray().put(note()))
    private fun root(line: JSONObject = line()) = JSONObject().put("phiscriptFormat", 1)
        .put("offset", 0.0).put("lines", JSONArray().put(line))
    @Test fun nativeCenteredCoordinatesUseActualViewport() {
        val l = line(); l.getJSONObject("object").put("x", fixed(0.2)).put("y", fixed(0.2))
        val c = Chart.parse(root(l).toString()); val p = c.position(c.notes.single(), 1.0, Viewport(240f, 20f, 1600f, 900f))!!
        assertEquals(1200f, p.x, 0.01f); assertEquals(380f, p.y, 0.01f)
    }
    @Test fun pbcAnimatedNoteXMovesAHoldThroughItsDuration() {
        val l = line(); l.put("notes", JSONArray().put(note(3).put("object", JSONObject().put("x", animated()))))
        val c = Chart.parse(root(l).toString()); val n = c.notes.single()
        assertEquals(880f, c.position(n, 1.0, viewport)!!.x, 0.01f)
        assertEquals(1040f, c.position(n, 3.0, viewport)!!.x, 0.01f); assertEquals(3.0, n.endSeconds, 1e-9)
    }
    @Test fun nativeAdditiveAnimLayersAreSummed() {
        val l = line(); l.getJSONObject("object").put("x", JSONArray().put(JSONArray().put(frame(0.0, 0.1)))
            .put(JSONArray().put(frame(0.0, 0.2))))
        val c = Chart.parse(root(l).toString()); assertEquals(1040f, c.position(c.notes.single(), 1.0, viewport)!!.x, 0.01f)
    }
    @Test fun offScreenLineUsesTheSameJudgeXInsideViewport() {
        val l = line(); l.getJSONObject("object").put("y", fixed(2.0)); val c = Chart.parse(root(l).toString())
        val p = c.position(c.notes.single(), 1.0, viewport)!!
        assertTrue(viewport.contains(p)); assertEquals(800f, p.x, 0.01f)
    }
    @Test fun rotatedOffScreenLineCanUseHorizontalJudgeStrip() {
        val l = line(); l.getJSONObject("object").put("x", fixed(4.0)).put("rotation", fixed(90.0))
        val c = Chart.parse(root(l).toString()); val p = c.position(c.notes.single(), 1.0, viewport)!!
        assertTrue(viewport.contains(p)); assertEquals(450f, p.y, 0.01f)
    }
    @Test fun completelyOffScreenJudgeXIsNeverClampedToAnotherNote() {
        val l = line(); l.getJSONObject("object").put("x", fixed(4.0)); val c = Chart.parse(root(l).toString())
        assertFalse(viewport.contains(c.position(c.notes.single(), 1.0, viewport)!!))
    }
    @Test fun instantJumpsTakeTheLastKeyframeValue() {
        val l = line(); l.getJSONObject("object").put("x", JSONArray().put(JSONArray()
            .put(frame(0.0, 0.0)).put(frame(2.0, 0.0)).put(frame(2.0, 0.4))))
        val c = Chart.parse(root(l).toString()); val n = c.notes.single()
        assertEquals(800f, c.position(n, 1.999, viewport)!!.x, 0.01f)
        assertEquals(1120f, c.position(n, 2.0, viewport)!!.x, 0.01f)
    }
    @Test fun holdTweenKeepsAValueAcrossAnEventGap() {
        val l = line(); l.getJSONObject("object").put("x", JSONArray().put(JSONArray()
            .put(frame(0.0, 0.2)).put(frame(4.0, 0.4))))
        val c = Chart.parse(root(l).toString()); assertEquals(960f, c.position(c.notes.single(), 3.0, viewport)!!.x, 0.01f)
    }
    @Test fun fakePbcNotesAreExcluded() {
        val l = line(); l.getJSONArray("notes").put(note().put("fake", true))
        assertEquals(1, Chart.parse(root(l).toString()).notes.size)
    }
    @Test fun invalidParentIndexFailsDuringImport() {
        val r = root(line().put("parent", 7))
        assertTrue(runCatching { Chart.parse(r.toString()) }.exceptionOrNull()?.message.orEmpty().contains("索引"))
    }
    @Test fun invalidTweenAndBezierXFailDuringImport() {
        val l = line(); val bad = frame(0.0, 0.0).put("tween", JSONObject().put("id", 99))
        l.getJSONObject("object").put("x", JSONArray().put(JSONArray().put(bad)))
        assertTrue(runCatching { Chart.parse(root(l).toString()) }.isFailure)
        bad.put("tween", JSONObject().put("id", 2).put("bezier", JSONArray().put(-1).put(0).put(1).put(1)))
        assertTrue(runCatching { Chart.parse(root(l).toString()) }.isFailure)
    }
    @Test fun deepParentsUseIterativeTransforms() {
        val lines = JSONArray()
        for (i in 0 until 256) lines.put(line().put("parent", i - 1)
            .put("notes", if (i == 255) JSONArray().put(note()) else JSONArray()))
        val c = Chart.parse(root().put("lines", lines).toString()); val p = c.position(c.notes.single(), 1.0, viewport)!!
        assertEquals(800f, p.x, 0.01f); assertEquals(450f, p.y, 0.01f)
    }
    @Test fun jumpPreservesTheLeftSideOfAnIncomingLinearRamp() {
        val l = line(); l.getJSONObject("object").put("x", JSONArray().put(JSONArray()
            .put(frame(0.0, 0.0, 2)).put(frame(2.0, 0.2)).put(frame(2.0, 0.4))))
        val c = Chart.parse(root(l).toString()); val n = c.notes.single()
        assertEquals(880f, c.position(n, 1.0, viewport)!!.x, 0.01f)
        assertEquals(1120f, c.position(n, 2.0, viewport)!!.x, 0.01f)
    }
    @Test fun tapFollowsTheLineAtActualDispatchTime() {
        val l = line(); l.getJSONObject("object").put("x", animated()); val c = Chart.parse(root(l).toString())
        val event = TouchEvent.forChart(c).single(); val p = c.touchPosition(event, 1020L, viewport)
        assertEquals(881.6f, p.x, 0.01f); assertTrue(p.x != c.position(c.notes.single(), 1.0, viewport)!!.x)
    }
    @Test fun firstEmptyAnimationSuppressesAdditiveLayersLikePhira() {
        val l = line(); l.getJSONObject("object").put("x", JSONArray().put(JSONArray()).put(JSONArray().put(frame(0.0, 0.2))))
        val c = Chart.parse(root(l).toString()); assertEquals(800f, c.position(c.notes.single(), 1.0, viewport)!!.x, 0.01f)
    }
    @Test fun nonFirstEmptyAnimationIsRejected() {
        val l = line(); l.getJSONObject("object").put("x", JSONArray().put(JSONArray().put(frame(0.0, 0.2))).put(JSONArray()))
        assertTrue(runCatching { Chart.parse(root(l).toString()) }.isFailure)
    }
    @Test fun rotatedFlickPreservesJudgeXAndRetainsContact() {
        val l = line(); l.getJSONObject("object").put("rotation", fixed(33.0))
        l.put("notes", JSONArray().put(note(4))); val c = Chart.parse(root(l).toString())
        val e = TouchEvent.forChart(c).single(); val angle = Math.toRadians(33.0)
        for (elapsed in listOf(0L, 15L, 30L, 45L, 60L, 80L, 96L)) {
            val p = c.touchPosition(e, e.startMs + elapsed, viewport); assertTrue(viewport.contains(p))
            val localX = (p.x - 800.0) * kotlin.math.cos(angle) - (p.y - 450.0) * kotlin.math.sin(angle)
            assertEquals(0.0, localX, 0.001)
        }
        assertEquals(96L, e.endMs - e.startMs)
        assertEquals(c.touchPosition(e, e.startMs + 60L, viewport), c.touchPosition(e, e.endMs, viewport))
    }
    @Test fun offScreenFlickUsesVisibleNormalChord() {
        val l = line(); l.getJSONObject("object").put("y", fixed(2.0)); l.put("notes", JSONArray().put(note(4)))
        val c = Chart.parse(root(l).toString()); val e = TouchEvent.forChart(c).single()
        for (elapsed in listOf(0L, 15L, 30L, 45L, 60L, 96L)) {
            val p = c.touchPosition(e, e.startMs + elapsed, viewport)
            assertTrue(viewport.contains(p)); assertEquals(800f, p.x, 0.01f)
        }
    }
    @Test fun degenerateBezierControlsRemainFinite() {
        for ((x1, x2) in listOf(0.0 to 0.0, 1.0 to 1.0, 1.0 to 0.0)) {
            val l = line(); val t = JSONObject().put("id", 2).put("bezier", JSONArray().put(x1).put(0.0).put(x2).put(1.0))
            l.getJSONObject("object").put("x", JSONArray().put(JSONArray().put(frame(0.0, 0.0).put("tween", t)).put(frame(4.0, 0.2))))
            val c = Chart.parse(root(l).toString())
            for (time in listOf(-1.0, 0.0, 0.004, 0.04, 0.4, 1.0, 2.0, 3.6, 3.96, 3.996, 4.0, 5.0)) {
                val p = c.position(c.notes.single(), time, viewport)!!; assertTrue(p.x.isFinite()); assertTrue(p.y.isFinite())
            }
        }
    }
}
