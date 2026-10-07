package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VisualNoteAppearanceTest {
    private val width = 320
    private val height = 180
    private val blue = 0xff30d5ff.toInt()
    private val red = 0xffff5070.toInt()
    private val white = -1
    private fun pixels() = IntArray(width * height) { 0xff101010.toInt() }
    private fun rect(p: IntArray, l: Int, t: Int, r: Int, b: Int, c: Int) {
        for (y in t until b) for (x in l until r) p[y * width + x] = c
    }
    private fun line(p: IntArray) = rect(p, 20, 100, 300, 103, white)
    private fun outline(p: IntArray, l: Int, t: Int, r: Int, b: Int, c: Int) {
        rect(p, l, t, r, t + 1, c); rect(p, l, b - 1, r, b, c)
        rect(p, l, t, l + 1, b, c); rect(p, r - 1, t, r, b, c)
    }
    @Test fun paleBlueTapKeepsItsColor() {
        val p = pixels(); line(p); rect(p, 100, 40, 124, 45, 0xffc8dce3.toInt())
        val s = VisualPixelDetector().detect(p, width, height)
        assertTrue(s.reliable); assertEquals(1, s.notes.single().kind)
    }
    @Test fun blueOutlineWithWhiteCenterIsOneTap() {
        val p = pixels(); line(p); rect(p, 100, 40, 126, 46, white); outline(p, 100, 40, 126, 46, blue)
        val n = VisualPixelDetector().detect(p, width, height).notes.single()
        assertEquals(1, n.kind); assertEquals(112.5, n.x, 1.0); assertEquals(42.5, n.y, 1.0)
    }
    @Test fun brokenBlueOutlineStillHasOneCenter() {
        val p = pixels(); line(p); rect(p, 100, 40, 128, 46, white); outline(p, 100, 40, 128, 46, blue)
        rect(p, 113, 40, 115, 46, white)
        val n = VisualPixelDetector().detect(p, width, height).notes.single()
        assertEquals(1, n.kind); assertEquals(113.5, n.x, 1.0)
    }
    @Test fun squareRedFlickIsNotRequiredToLookLikeATap() {
        val p = pixels(); line(p); rect(p, 180, 45, 193, 58, red)
        val n = VisualPixelDetector().detect(p, width, height).notes.single()
        assertEquals(4, n.kind); assertEquals(186.0, n.x, 1.0)
    }
    @Test fun redChevronIsRecognized() {
        val p = pixels(); line(p)
        for (x in 180..200) { val y = 45 + (abs(x - 190) * 0.7).toInt(); rect(p, x, y, x + 1, y + 3, red) }
        val n = VisualPixelDetector().detect(p, width, height).notes.single()
        assertEquals(4, n.kind); assertEquals(190.0, n.x, 1.0)
    }
    @Test fun twoPixelBreakInRedSpriteDoesNotCreateTwoFlicks() {
        val p = pixels(); line(p); rect(p, 100, 45, 107, 52, red); rect(p, 109, 45, 116, 52, red)
        val ns = VisualPixelDetector().detect(p, width, height).notes
        assertEquals(1, ns.size); assertEquals(4, ns.single().kind)
    }
    @Test fun disconnectedHollowHoldRailsArePaired() {
        val p = pixels(); line(p); rect(p, 100, 20, 113, 80, white)
        rect(p, 100, 20, 102, 80, blue); rect(p, 111, 20, 113, 80, blue)
        val n = VisualPixelDetector().detect(p, width, height).notes.single()
        assertEquals(3, n.kind); assertEquals(106.0, n.x, 1.0)
        assertEquals(49.5, n.y, 1.0); assertEquals(29.5, abs(n.extentY), 1.0)
    }
    @Test fun tapAtTheJudgeLineDoesNotFloodTheEntireWhiteLine() {
        val p = pixels(); line(p); rect(p, 100, 96, 126, 104, white); outline(p, 100, 96, 126, 104, blue)
        val s = VisualPixelDetector().detect(p, width, height)
        assertTrue(s.reliable); assertEquals(1, s.lines.size); assertEquals(1, s.notes.size)
        assertEquals(1, s.notes.single().kind); assertEquals(112.5, s.notes.single().x, 2.0)
    }
    @Test fun interruptedJudgeLineRetainsItsFullSpan() {
        val p = pixels(); line(p); rect(p, 100, 100, 126, 103, 0xff101010.toInt()); rect(p, 100, 40, 126, 45, blue)
        val s = VisualPixelDetector().detect(p, width, height)
        assertTrue(s.reliable); assertEquals(1, s.lines.size); assertTrue(s.lines.single().length > 250.0)
        assertEquals(1, s.notes.single().kind)
    }
    @Test fun distinctParallelLinesAreNotMerged() {
        val p = pixels(); line(p); rect(p, 20, 140, 300, 143, white)
        val s = VisualPixelDetector().detect(p, width, height); assertTrue(s.reliable); assertEquals(2, s.lines.size)
    }
    @Test fun paleNeutralDecorationWithoutColoredSeedsIsIgnored() {
        val p = pixels(); line(p); rect(p, 100, 40, 124, 45, 0xffd9e3e6.toInt())
        val s = VisualPixelDetector().detect(p, width, height); assertTrue(s.reliable); assertTrue(s.notes.isEmpty())
    }
    @Test fun broadBlueIllustrationIsNotAnOrdinaryNoteOrHold() {
        val p = pixels(); line(p); rect(p, 30, 20, 280, 80, blue)
        val s = VisualPixelDetector().detect(p, width, height); assertTrue(s.reliable); assertTrue(s.notes.isEmpty())
    }
    @Test fun yellowJudgeLineRemainsALine() {
        val p = pixels(); rect(p, 20, 100, 300, 103, 0xffffd340.toInt()); rect(p, 100, 40, 124, 45, blue)
        val s = VisualPixelDetector().detect(p, width, height); assertTrue(s.reliable)
        assertEquals(1, s.lines.size); assertEquals(1, s.notes.single().kind)
    }
    @Test fun supportsHigherAnalysisResolutionWithoutChangingCoordinates() {
        val w = 960; val h = 540; val p = IntArray(w * h) { 0xff101010.toInt() }
        for (y in 300 until 305) for (x in 60 until 900) p[y * w + x] = white
        for (y in 120 until 130) for (x in 300 until 370) p[y * w + x] = blue
        val s = VisualPixelDetector().detect(p, w, h); assertTrue(s.reliable)
        assertEquals(1, s.notes.single().kind); assertEquals(334.5, s.notes.single().x, 1.0)
    }
}
