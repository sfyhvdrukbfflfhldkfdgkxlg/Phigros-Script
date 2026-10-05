package io.github.phiscript

import org.junit.Assert.*
import org.junit.Test

class RestartPointTest {
    @Test fun acceptsCenterAndCalibratedPosition() {
        val center = AppSettings.point("0.5,0.5")
        assertEquals(0.5f, center.x, 0f)
        assertEquals(0.5f, center.y, 0f)
        val adjusted = AppSettings.point(" 0.42, 0.6 ")
        assertEquals(0.42f, adjusted.x, 0f)
        assertEquals(0.6f, adjusted.y, 0f)
    }
    @Test fun rejectsInvalidOrOffScreenPoints() {
        for (value in listOf("0,0.5", "1,0.5", "-0.1,0.5", "0.5,1.1",
            "NaN,0.5", "Infinity,0.5", "0.5", "0.5,0.5,1", "")) {
            assertThrows(IllegalArgumentException::class.java) { AppSettings.point(value) }
        }
    }
}
