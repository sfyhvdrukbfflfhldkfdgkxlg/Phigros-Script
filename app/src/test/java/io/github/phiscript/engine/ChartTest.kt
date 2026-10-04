package io.github.phiscript.engine

import org.junit.Assert.*
import org.junit.Test

class ChartTest {
    private val viewport = Viewport(0f, 0f, 1000f, 600f)
    private fun chart(
        note: String = """{"type":1,"time":64,"positionX":0,"holdTime":0,"speed":1,"floorPosition":1}""",
        below: Boolean = false,
        move: String = """[{"startTime":0,"endTime":128,"start":0.5,"end":0.5,"start2":0.5,"end2":0.5}]""",
        rotate: String = "[]", opacity: String = "[]",
        speeds: String = """[{"startTime":0,"endTime":256,"value":1}]""",
        offset: Double = 0.0
    ): Chart {
        val aboveNotes = if (below) "[]" else "[$note]"
        val belowNotes = if (below) "[$note]" else "[]"
        return Chart.parse("""
            {"formatVersion":3,"offset":$offset,"judgeLineList":[{
              "bpm":120,"notesAbove":$aboveNotes,"notesBelow":$belowNotes,
              "judgeLineMoveEvents":$move,"judgeLineRotateEvents":$rotate,
              "judgeLineDisappearEvents":$opacity,"speedEvents":$speeds
            }]}
        """.trimIndent())
    }
    @Test fun tickConversionAndOffsetUseDifferentClocks() {
        val chart = chart(offset = 0.125)
        assertEquals(1.0, chart.notes.single().timeSeconds, 1e-10)
        assertEquals(0.125, chart.offsetSeconds, 1e-10)
        assertEquals(1.0, chart.durationSeconds, 1e-10)
        val hit = chart.position(chart.notes.single(), 1.0, viewport, visual = true)!!
        assertEquals(500f, hit.x, 0.001f); assertEquals(300f, hit.y, 0.001f)
    }
    @Test fun moveYOriginIsBottomAndRotationIsClockwiseInScreenSpace() {
        val chart = chart(
            note = """{"type":1,"time":64,"positionX":2,"holdTime":0,"speed":1,"floorPosition":1}""",
            move = """[{"startTime":0,"endTime":128,"start":0.25,"end":0.25,"start2":0.75,"end2":0.75}]""",
            rotate = """[{"startTime":0,"endTime":128,"start":90,"end":90}]"""
        )
        val hit = chart.position(chart.notes.single(), 1.0, viewport)!!
        assertEquals(250f, hit.x, 0.001f); assertEquals(37.5f, hit.y, 0.001f)
        val visible = chart.position(chart.notes.single(), 0.0, viewport, true)!!
        assertEquals(-110f, visible.x, 0.001f); assertEquals(37.5f, visible.y, 0.001f)
    }
    @Test fun belowNotesApproachFromOppositeSide() {
        val above = chart(); val below = chart(below = true)
        assertEquals(-60f, above.position(above.notes[0], 0.0, viewport, true)!!.y, 0.001f)
        assertEquals(660f, below.position(below.notes[0], 0.0, viewport, true)!!.y, 0.001f)
    }
    @Test fun holdHeadDoesNotMultiplyItsDistanceByBodySpeed() {
        val hold = chart(note = """{"type":3,"time":64,"positionX":0,"holdTime":64,"speed":3,"floorPosition":1}""")
        assertEquals(2.0, hold.notes[0].endSeconds, 1e-10)
        assertEquals(-60f, hold.position(hold.notes[0], 0.0, viewport, true)!!.y, 0.001f)
        assertEquals(2024L, TouchEvent.forChart(hold)[0].endMs)
    }
    @Test fun speedIntegrationPreservesFloorAcrossGapsAndAtBoundaries() {
        val chart = chart(note = """{"type":1,"time":192,"positionX":0,"holdTime":0,"speed":1,"floorPosition":3}""",
            speeds = """[{"startTime":-99999,"endTime":64,"value":1},{"startTime":128,"endTime":192,"value":2}]""")
        fun y(time: Double) = chart.position(chart.notes[0], time, viewport, true)!!.y
        assertEquals(-420f, y(1.0), 0.001f); assertEquals(-420f, y(1.5), 0.001f)
        assertEquals(-420f, y(2.0), 0.001f); assertEquals(-60f, y(2.5), 0.001f)
        assertEquals(300f, y(3.0), 0.001f); assertEquals(300f, y(4.0), 0.001f)
    }
    @Test fun movingLineClampsGapsAndInterpolatesWithinEvents() {
        val chart = chart(move = """[
            {"startTime":0,"endTime":64,"start":0.1,"end":0.3,"start2":0.5,"end2":0.5},
            {"startTime":128,"endTime":192,"start":0.3,"end":0.7,"start2":0.5,"end2":0.5}]""")
        fun x(time: Double) = chart.position(chart.notes[0], time, viewport)!!.x
        assertEquals(100f, x(-1.0), 0.001f); assertEquals(200f, x(0.5), 0.001f)
        assertEquals(300f, x(1.5), 0.001f); assertEquals(500f, x(2.5), 0.001f)
        assertEquals(700f, x(5.0), 0.001f)
    }
    @Test fun hiddenLineStillHasTouchGeometry() {
        val chart = chart(opacity = """[{"startTime":0,"endTime":256,"start":0,"end":0}]""")
        assertNull(chart.position(chart.notes[0], 0.5, viewport, true))
        assertNotNull(chart.position(chart.notes[0], 0.5, viewport))
    }
    @Test fun viewportMapsCapturePixelsToRealScreenPixels() {
        val scaled = Viewport(12f, 10f, 456f, 250f).scaleTo(480, 270, 2400, 1350)
        assertEquals(Viewport(60f, 50f, 2280f, 1250f), scaled)
        assertTrue(scaled.contains(Point(60f, 50f))); assertFalse(scaled.contains(Point(2340f, 50f)))
    }
    @Test fun flickCrossesHitPositionAndDragStartsBeforeHit() {
        val flick = chart(note = """{"type":4,"time":64,"positionX":0,"holdTime":0,"speed":1,"floorPosition":1}""")
        val event = TouchEvent.forChart(flick)[0]
        val from = flick.touchPosition(event, event.startMs, viewport)
        val to = flick.touchPosition(event, event.endMs, viewport)
        assertEquals(60f, to.x - from.x, 0.001f); assertTrue(from.x < 500f); assertTrue(to.x > 500f)
        val drag = chart(note = """{"type":2,"time":64,"positionX":0,"holdTime":0,"speed":1,"floorPosition":1}""")
        assertEquals(970L, TouchEvent.forChart(drag)[0].startMs)
    }
    @Test(expected = IllegalArgumentException::class) fun overlappingSpeedEventsAreRejected() {
        chart(speeds = """[{"startTime":0,"endTime":128,"value":1},{"startTime":64,"endTime":192,"value":2}]""")
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedFormatIsRejected() {
        Chart.parse("""{"formatVersion":1}""")
    }
}
