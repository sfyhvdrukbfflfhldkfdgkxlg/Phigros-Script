package io.github.phiscript.capture
import org.junit.Assert.*
import org.junit.Test
class PhiraViewportTest {
    @Test fun longPhoneGetsCenteredSixteenNine() {
        val v = PhiraViewport.bounds(2400, 1080, 16.0 / 9.0, false)
        assertEquals(240f, v.left, 0.001f); assertEquals(0f, v.top, 0.001f)
        assertEquals(1920f, v.width, 0.001f); assertEquals(1080f, v.height, 0.001f)
    }
    @Test fun narrowerScreenUsesItsAspectWhenNotForced() {
        val v = PhiraViewport.bounds(1600, 1200, 16.0 / 9.0, false)
        assertEquals(0f, v.left, 0.001f); assertEquals(0f, v.top, 0.001f)
        assertEquals(1600f, v.width, 0.001f); assertEquals(1200f, v.height, 0.001f)
    }
    @Test fun forcedAspectUsesTopBars() {
        val v = PhiraViewport.bounds(1600, 1200, 16.0 / 9.0, true)
        assertEquals(0f, v.left, 0.001f); assertEquals(150f, v.top, 0.001f)
        assertEquals(1600f, v.width, 0.001f); assertEquals(900f, v.height, 0.001f)
    }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteAspectRejected() {
        PhiraViewport.bounds(2400, 1080, Double.NaN, false)
    }
}
