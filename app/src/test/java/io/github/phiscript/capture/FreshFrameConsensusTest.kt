package io.github.phiscript.capture

import org.junit.Assert.*
import org.junit.Test

class FreshFrameConsensusTest {
    @Test fun repeatedScreenshotCannotConfirmPause() {
        val gate = FreshFrameConsensus()
        assertFalse(gate.observe("pause", 1000))
        assertFalse(gate.observe("pause", 1000))
        assertFalse(gate.observe("pause", 900))
        assertTrue(gate.observe("pause", 1200))
    }
    @Test fun framesCapturedBeforeResumeCannotConfirmGameplay() {
        val gate = FreshFrameConsensus(afterFrameMs = 5000)
        assertFalse(gate.observe("running", 4800))
        assertFalse(gate.observe("running", 5000))
        assertFalse(gate.observe("running", 5100))
        assertTrue(gate.observe("running", 5300))
    }
    @Test fun menuFlickerAndMissingTextResetConfirmation() {
        val gate = FreshFrameConsensus()
        assertFalse(gate.observe("running", 1000))
        assertFalse(gate.observe(null, 1200))
        assertFalse(gate.observe("running", 1400))
        assertTrue(gate.observe("running", 1600))
    }
    @Test fun conflictingSongMustSettleBeforeLoadingChart() {
        val gate = FreshFrameConsensus()
        assertFalse(gate.observe("A/IN", 1000))
        assertFalse(gate.observe("B/HD", 1300))
        assertFalse(gate.observe("A/IN", 1600))
        assertTrue(gate.observe("A/IN", 1900))
    }
    @Test fun longGapAndTooShortSpanDoNotConfirm() {
        val gate = FreshFrameConsensus()
        assertFalse(gate.observe("pause", 1000))
        assertFalse(gate.observe("pause", 1050))
        assertFalse(gate.observe("pause", 4000))
        assertTrue(gate.observe("pause", 4200))
    }
}
