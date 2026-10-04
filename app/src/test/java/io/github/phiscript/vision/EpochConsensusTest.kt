package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test

class EpochConsensusTest {
    @Test fun requiresSeveralFreshFramesBeforeLocking() {
        val clock = EpochConsensus()
        assertNull(clock.observe(10_003.0, 11_000))
        assertNull(clock.observe(9_998.0, 11_080))
        assertNull(clock.observe(10_001.0, 11_160))
        assertEquals(10_001L, clock.observe(10_000.0, 11_240))
    }
    @Test fun aStaticScreenshotDoesNotLookLikeAPlayingChart() {
        val clock = EpochConsensus()
        for (i in 0..12) assertNull(clock.observe(10_000.0 + i * 80, 11_000L + i * 80))
    }
    @Test fun outOfOrderAndDuplicateFramesDoNotCount() {
        val clock = EpochConsensus()
        clock.observe(10_000.0, 11_000)
        assertNull(clock.observe(10_000.0, 11_000))
        assertNull(clock.observe(10_000.0, 10_900))
        assertEquals(1, clock.size)
    }
    @Test fun aPauseOrTimingJumpRequiresFreshConsensus() {
        val clock = EpochConsensus()
        clock.observe(10_000.0, 11_000)
        clock.observe(10_000.0, 11_080)
        clock.observe(10_000.0, 11_160)
        assertNull(clock.observe(11_000.0, 12_000))
        assertEquals(1, clock.size)
    }
    @Test fun rejectsCumulativeDriftBeyondTheSpreadLimit() {
        val clock = EpochConsensus()
        clock.observe(10_000.0, 11_000)
        clock.observe(10_018.0, 11_080)
        clock.observe(10_035.0, 11_160)
        assertNull(clock.observe(10_050.0, 11_240))
        assertEquals(1, clock.size)
    }
}
