package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class VisualPixelDetectorTest {
    private val width = 320; private val height = 180
    private val blue = 0xff30d5ff.toInt()
    private fun pixels() = IntArray(width * height) { 0xff101010.toInt() }
    private fun rect(p: IntArray, l: Int, t: Int, r: Int, b: Int, c: Int) {
        for (y in t until b) for (x in l until r) p[y * width + x] = c
    }
    private fun line(p: IntArray) = rect(p, 20, 100, 300, 103, -1)
    @Test fun detectsLineAndDefaultTap() {
        val p = pixels(); line(p); rect(p, 100, 40, 124, 45, blue)
        val s = VisualPixelDetector().detect(p, width, height)
        assertTrue(s.reliable); assertEquals(1, s.lines.size); assertEquals(1, s.notes.size)
        assertEquals(1, s.notes.single().kind); assertEquals(111.5, s.notes.single().x, 1.0)
    }
    @Test fun perpendicularBlueBodyIsHold() {
        val p = pixels(); line(p); rect(p, 100, 25, 108, 70, blue)
        val n = VisualPixelDetector().detect(p, width, height).notes.single()
        assertEquals(3, n.kind); assertTrue(abs(n.extentY) > 15)
    }
    @Test fun distinguishesYellowDragAndRedFlick() {
        val p = pixels(); line(p)
        rect(p, 60, 40, 84, 45, 0xffffd340.toInt()); rect(p, 180, 50, 204, 55, 0xffff5070.toInt())
        assertEquals(setOf(2, 4), VisualPixelDetector().detect(p, width, height).notes.map { it.kind }.toSet())
    }
    @Test fun noLineOrBroadBackgroundDoesNotTrigger() {
        val p = pixels(); rect(p, 10, 20, 300, 80, blue)
        val s = VisualPixelDetector().detect(p, width, height)
        assertFalse(s.reliable); assertTrue(s.notes.isEmpty())
    }
    @Test fun excludesProgressBar() {
        val p = pixels(); rect(p, 0, 2, width, 5, -1)
        assertFalse(VisualPixelDetector().detect(p, width, height).reliable)
    }
    @Test fun appliesViewportCrop() {
        val p = pixels(); line(p); rect(p, 60, 40, 84, 45, blue); rect(p, 180, 50, 204, 55, blue)
        val s = VisualPixelDetector().detect(p, width, height, 150, 10, 310, 160)
        assertEquals(1, s.notes.size); assertTrue(s.notes.single().x > 150)
    }
    @Test fun detectsRotatedLine() {
        val p = pixels(); val angle = Math.PI / 6; val dx = cos(angle); val dy = sin(angle)
        for (y in 0 until height) for (x in 0 until width) {
            val along = (x - 160.0) * dx + (y - 90.0) * dy
            val across = (x - 160.0) * -dy + (y - 90.0) * dx
            if (abs(along) < 115 && abs(across) < 1.6) p[y * width + x] = -1
        }
        val s = VisualPixelDetector().detect(p, width, height)
        assertTrue(s.reliable); assertEquals(1, s.lines.size)
        assertEquals(dx, s.lines.single().dx, 0.02); assertEquals(dy, s.lines.single().dy, 0.02)
    }
    @Test fun ambiguousParallelLinesRejectNote() {
        val p = pixels(); line(p); rect(p, 20, 140, 300, 143, -1); rect(p, 100, 120, 124, 125, blue)
        val s = VisualPixelDetector().detect(p, width, height)
        assertEquals(2, s.lines.size); assertTrue(s.notes.isEmpty())
    }
}
