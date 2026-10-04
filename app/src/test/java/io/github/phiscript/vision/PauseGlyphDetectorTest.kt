package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test

class PauseGlyphDetectorTest {
    private val width = 160
    private val height = 80
    private val dark = 0xff202020.toInt()
    private val white = 0xffe6e6e6.toInt()
    private fun frame(color: Int = dark) = IntArray(width * height) { color }
    private fun rect(pixels: IntArray, x: Int, y: Int, w: Int, h: Int, color: Int = white) {
        for (row in y until y + h) for (column in x until x + w)
            if (column in 0 until width && row in 0 until height) pixels[row * width + column] = color
    }
    private fun pause(pixels: IntArray, x: Int = 32, y: Int = 20, color: Int = white) {
        rect(pixels, x, y, 6, 24, color); rect(pixels, x + 12, y, 6, 24, color)
    }
    private fun detect(pixels: IntArray) = PauseGlyphDetector.detect(pixels, width, height, 540)
    @Test fun findsMeasuredCenterOfAnIsolatedPause() {
        val pixels = frame(); pause(pixels)
        val glyph = detect(pixels)
        assertNotNull(glyph); assertEquals(41f, glyph!!.centerX, 0.01f); assertEquals(32f, glyph.centerY, 0.01f)
        assertEquals(18f, glyph.width, 0.01f); assertEquals(24f, glyph.height, 0.01f)
    }
    @Test fun rejectsCommonNonPauseShapes() {
        val cases = linkedMapOf<String, IntArray>()
        cases["blank"] = frame()
        cases["single bar"] = frame().also { rect(it, 32, 20, 6, 24) }
        cases["H bridge"] = frame().also { pause(it); rect(it, 38, 30, 6, 3) }
        cases["dim H bridge"] = frame().also { pause(it); rect(it, 38, 30, 6, 3, 0xffb4b4b4.toInt()) }
        cases["box"] = frame().also { pause(it); rect(it, 32, 20, 18, 3); rect(it, 32, 41, 18, 3) }
        cases["solid block"] = frame().also { rect(it, 32, 20, 18, 24) }
        cases["unequal heights"] = frame().also { rect(it, 32, 20, 6, 24); rect(it, 44, 20, 6, 12) }
        cases["three bars"] = frame().also { pause(it); rect(it, 56, 20, 6, 24) }
        cases["touching ROI edge"] = frame().also { pause(it, x = 0) }
        cases["low contrast"] = frame(0xffc8c8c8.toInt()).also { pause(it) }
        cases["colored bars"] = frame().also { pause(it, color = 0xffffff00.toInt()) }
        cases["two separate plausible glyphs"] = frame().also { pause(it); pause(it, x = 104) }
        for ((name, pixels) in cases) assertNull(name, detect(pixels))
    }
    @Test fun toleratesAntialiasingAroundOtherwiseSolidBars() {
        val pixels = frame()
        rect(pixels, 31, 19, 8, 26, 0xff8c8c8c.toInt()); rect(pixels, 43, 19, 8, 26, 0xff8c8c8c.toInt())
        pause(pixels); assertNotNull(detect(pixels))
    }
    @Test fun usesLocalBackgroundInsteadOfWholeRoiBrightness() {
        val pixels = frame(0xffb4b4b4.toInt())
        rect(pixels, 16, 8, 50, 48, dark); pause(pixels); assertNotNull(detect(pixels))
    }
    @Test fun scalesShapeLimitsWithFrameResolution() {
        val pixels = IntArray(80 * 40) { dark }
        for (y in 10 until 22) {
            for (x in 16 until 19) pixels[y * 80 + x] = white
            for (x in 22 until 25) pixels[y * 80 + x] = white
        }
        assertNotNull(PauseGlyphDetector.detect(pixels, 80, 40, 270))
    }
}
class PauseEvidenceGateTest {
    private val glyph = PauseGlyphDetector.Glyph(41f, 32f, 18f, 24f)
    @Test fun requiresThreeSpacedFramesAndEnoughElapsedTime() {
        val gate = PauseEvidenceGate()
        assertNull(gate.observe(glyph, 0)); assertNull(gate.observe(glyph, 90))
        assertNotNull(gate.observe(glyph, 180)); assertNotNull(gate.observe(glyph, 210))
    }
    @Test fun rapidFramesCannotBypassMinimumSpacing() {
        val gate = PauseEvidenceGate()
        assertNull(gate.observe(glyph, 0)); assertNull(gate.observe(glyph, 20))
        assertNull(gate.observe(glyph, 180)); assertNotNull(gate.observe(glyph, 240))
    }
    @Test fun fastCadenceStillAccumulatesTimeFromFirstSeen() {
        val gate = PauseEvidenceGate()
        for (time in 0L..176L step 16L) assertNull("time=$time", gate.observe(glyph, time))
        assertNotNull(gate.observe(glyph, 192))
    }
    @Test fun repeatedAndOutOfOrderTimestampsNeverAddEvidenceOrReturnHits() {
        val gate = PauseEvidenceGate()
        assertNull(gate.observe(glyph, 0)); assertNull(gate.observe(glyph, 90))
        assertNull(gate.observe(glyph, 90)); assertNull(gate.observe(glyph, 80))
        assertNotNull(gate.observe(glyph, 180)); assertNull(gate.observe(glyph, 180))
    }
    @Test fun missingFreshFrameImmediatelyClearsConfirmation() {
        val gate = PauseEvidenceGate()
        gate.observe(glyph, 0); gate.observe(glyph, 90)
        assertNotNull(gate.observe(glyph, 180)); assertNull(gate.observe(null, 210))
        assertNull(gate.observe(glyph, 240)); assertNull(gate.observe(glyph, 330))
        assertNotNull(gate.observe(glyph, 420))
    }
    @Test fun largeFrameGapStartsASeparateConfirmation() {
        val gate = PauseEvidenceGate()
        gate.observe(glyph, 0); gate.observe(glyph, 90)
        assertNull(gate.observe(glyph, 800)); assertNull(gate.observe(glyph, 890))
        assertNotNull(gate.observe(glyph, 980))
    }
    @Test fun toleratesSmallJitterButResetsForAnotherPosition() {
        val gate = PauseEvidenceGate()
        assertNull(gate.observe(glyph, 0)); assertNull(gate.observe(glyph.copy(centerX = 42f), 90))
        assertNotNull(gate.observe(glyph.copy(centerY = 33f), 180))
        val moved = glyph.copy(centerX = 61f)
        assertNull(gate.observe(moved, 240)); assertNull(gate.observe(moved, 330))
        assertNotNull(gate.observe(moved, 420))
    }
    @Test fun resetDiscardsExistingEvidence() {
        val gate = PauseEvidenceGate()
        gate.observe(glyph, 0); gate.observe(glyph, 90)
        assertNotNull(gate.observe(glyph, 180)); gate.reset()
        assertNull(gate.observe(glyph, 240)); assertNull(gate.observe(glyph, 330))
        assertNotNull(gate.observe(glyph, 420))
    }
}
