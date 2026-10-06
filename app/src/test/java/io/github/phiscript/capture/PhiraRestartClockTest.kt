package io.github.phiscript.capture
import org.junit.Assert.*
import org.junit.Test
class PhiraRestartClockTest {
    @Test fun retryIncludesStartingAnimation() {
        assertEquals(10700L, PhiraRestartClock.epoch(10000, 700, 0.0, 0, 0))
    }
    @Test fun positiveOffsetDelaysZero() {
        assertEquals(11000L, PhiraRestartClock.epoch(10000, 700, 0.2, 100, 0))
    }
    @Test fun negativeOffsetDoesNotAdvanceZeroBeforeStarting() {
        assertEquals(10700L, PhiraRestartClock.epoch(10000, 700, -0.5, 100, 0))
    }
    @Test fun calibrationIsAppliedOnce() {
        assertEquals(10835L, PhiraRestartClock.epoch(10000, 800, 0.0, 0, 35))
    }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteTimeRejected() {
        PhiraRestartClock.epoch(10000, 700, Double.NaN, 0, 0)
    }
    @Test(expected = IllegalArgumentException::class) fun hugeOffsetRejected() {
        PhiraRestartClock.epoch(10000, 700, 121.0, 0, 0)
    }
}
