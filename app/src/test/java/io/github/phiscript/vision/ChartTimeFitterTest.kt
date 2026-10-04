package io.github.phiscript.vision
import io.github.phiscript.engine.Chart
import io.github.phiscript.engine.Viewport
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChartTimeFitterTest {
    private data class InputNote(val time: Double, val x: Double, val type: Int)
    private val viewport = Viewport(0f, 0f, 480f, 270f)
    private fun chart(notes: List<InputNote>, noteSpeed: Double = 1.0): Chart {
        val encoded = JSONArray(notes.map {
            JSONObject().put("type", it.type).put("time", it.time * 64).put("positionX", it.x)
                .put("holdTime", 0).put("speed", noteSpeed).put("floorPosition", it.time)
        }).toString()
        return Chart.parse("""
            {"formatVersion":3,"offset":0.125,"judgeLineList":[{
              "bpm":120,"notesAbove":$encoded,"notesBelow":[],
              "judgeLineMoveEvents":[],"judgeLineRotateEvents":[],
              "judgeLineDisappearEvents":[],
              "speedEvents":[{"startTime":0,"endTime":6400,"value":1}]
            }]}
        """.trimIndent())
    }
    private fun detections(chart: Chart, seconds: Double, viewport: Viewport = this.viewport) =
        chart.notes.filter { it.timeSeconds > seconds + 0.04 }.mapNotNull { note ->
            chart.position(note, seconds, viewport, true)?.takeIf { viewport.contains(it) }?.let {
                ChartTimeFitter.Detection(it.x.toDouble(), it.y.toDouble(), note.type)
            }
        }
    private fun phrase(start: Double) = listOf(InputNote(start + 0.30, -3.0, 1),
        InputNote(start + 0.45, 0.0, 2), InputNote(start + 0.60, 3.0, 4))
    @Test fun matchesAfterTheFirstTwentyNotesAndTheOldEightSecondWindow() {
        val notes = (0 until 20).map { InputNote(0.2 + it * 0.03, -3.0 + it % 7, 1) } + phrase(12.0)
        val chart = chart(notes)
        val result = ChartTimeFitter(chart).search(detections(chart, 12.0), viewport, -5.0, 13.0)
        assertFalse(result.ambiguous); assertNotNull(result.fit)
        assertEquals(12.0, result.fit!!.seconds, 0.006)
        assertEquals(3, result.fit!!.matches)
    }
    @Test fun repeatedPhrasesRemainAmbiguousAcrossTheLargerSearchWindow() {
        val chart = chart(phrase(1.0) + phrase(5.0))
        val result = ChartTimeFitter(chart).search(detections(chart, 1.0), viewport, 0.0, 5.2)
        assertNull(result.fit); assertTrue(result.ambiguous)
    }
    @Test fun wrongColorsDoNotProduceAClock() {
        val chart = chart(phrase(2.0))
        val wrong = detections(chart, 2.0).map { it.copy(type = when (it.type) { 1 -> 4; 2 -> 1; else -> 2 }) }
        assertNull(ChartTimeFitter(chart).fitAt(2.0, wrong, viewport))
    }
    @Test fun oneBlobCannotSatisfyTwoNotes() {
        val chart = chart(listOf(InputNote(1.3, 0.0, 1), InputNote(1.3, 0.0, 1)))
        assertNull(ChartTimeFitter(chart).fitAt(1.0, detections(chart, 1.0).take(1), viewport))
    }
    @Test fun viewportChangesInvalidateCachedPredictions() {
        val chart = chart(phrase(2.0)); val fitter = ChartTimeFitter(chart)
        assertNotNull(fitter.search(detections(chart, 2.0), viewport, 1.5, 2.2).fit)
        val inset = Viewport(35f, 18f, 360f, 200f)
        val result = fitter.search(detections(chart, 2.0, inset), inset, 1.5, 2.2)
        assertNotNull(result.fit); assertEquals(2.0, result.fit!!.seconds, 0.006)
    }

    @Test fun gridPhaseErrorCannotMakeARepeatedPhraseUnique() {
        fun pattern(start: Double) = listOf(InputNote(start + 0.12, -3.0, 1),
            InputNote(start + 0.20, -1.0, 2), InputNote(start + 0.26, 1.0, 4), InputNote(start + 0.33, 3.0, 1))
        val chart = chart(pattern(1.0) + pattern(5.0125), noteSpeed = 1.8)
        val result = ChartTimeFitter(chart).search(detections(chart, 1.0), viewport, 0.0, 5.2)
        assertNull(result.fit); assertTrue(result.ambiguous)
    }
}
