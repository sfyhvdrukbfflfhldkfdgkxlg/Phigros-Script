package io.github.phiscript.capture

import io.github.phiscript.capture.PauseRestartGate.Outcome.*
import org.junit.Assert.*
import org.junit.Test

class PauseRestartGateTest {
    private fun gate(own: Boolean = true) = PauseRestartGate("song/IN", 100, own, 1000)
    private fun PauseRestartGate.frame(time: Long, key: String? = null, menu: Boolean = false,
        game: Boolean = false, result: Boolean = false, now: Long = time) =
        observe(time, now, key, menu, game, result)

    @Test fun ownPauseCanStartWithoutRepeatedSongOrButtonText() {
        val gate = gate()
        assertEquals(WAIT, gate.frame(1100))
        assertEquals(WAIT, gate.frame(1220))
        assertEquals(READY, gate.frame(1340))
    }
    @Test fun manualPauseRequiresVisibleMenuThroughoutConfirmation() {
        val gate = gate(false)
        assertEquals(WAIT, gate.frame(1100, "song/IN"))
        assertEquals(WAIT, gate.frame(1220, "song/IN"))
        assertEquals(WAIT, gate.frame(1340, "song/IN"))
        assertEquals(WAIT, gate.frame(1500, menu = true))
        assertEquals(WAIT, gate.frame(1620, menu = true))
        assertEquals(READY, gate.frame(1740, menu = true))
    }
    @Test fun explicitDifferentDifficultyPermanentlyRejectsStart() {
        val gate = gate()
        assertEquals(CHANGED, gate.frame(1100, "song/HD"))
        assertEquals(WAIT, gate.frame(1220))
        assertEquals(WAIT, gate.frame(1340))
        assertEquals(WAIT, gate.frame(1460))
    }
    @Test fun resultScreenPermanentlyRejectsStart() {
        val gate = gate()
        assertEquals(RESULT, gate.frame(1100, result = true))
        assertEquals(WAIT, gate.frame(1220))
        assertEquals(WAIT, gate.frame(1340))
        assertEquals(WAIT, gate.frame(1460))
    }
    @Test fun settlingFramesCannotAdvanceTheClick() {
        val gate = gate()
        assertEquals(WAIT, gate.frame(200))
        assertEquals(WAIT, gate.frame(400))
        assertEquals(WAIT, gate.frame(1099))
        assertEquals(WAIT, gate.frame(1100))
        assertEquals(WAIT, gate.frame(1220))
        assertEquals(READY, gate.frame(1340))
    }
    @Test fun duplicatePrePauseAndFutureFramesCannotConfirm() {
        val gate = gate()
        assertEquals(WAIT, gate.frame(100))
        assertEquals(WAIT, gate.frame(1100))
        repeat(5) { assertEquals(WAIT, gate.frame(1100)) }
        assertEquals(WAIT, gate.frame(1220, now = 1200))
        assertEquals(WAIT, gate.frame(1340))
        assertEquals(WAIT, gate.frame(1460))
        assertEquals(READY, gate.frame(1580))
    }
    @Test fun agedFrameResetsReadiness() {
        val gate = gate()
        gate.frame(1100)
        gate.frame(1220)
        assertEquals(WAIT, gate.frame(1340, now = 2240))
        assertEquals(WAIT, gate.frame(2340))
        assertEquals(WAIT, gate.frame(2460))
        assertEquals(READY, gate.frame(2580))
    }
    @Test fun stillRunningGameplayResetsReadiness() {
        val gate = gate()
        gate.frame(1100)
        gate.frame(1220)
        assertEquals(WAIT, gate.frame(1340, game = true))
        assertEquals(WAIT, gate.frame(1460))
        assertEquals(WAIT, gate.frame(1580))
        assertEquals(READY, gate.frame(1700))
    }
    @Test fun confirmedTextMenuCanOverrideBackgroundPauseGlyph() {
        val gate = gate()
        gate.frame(1100, menu = true, game = true)
        gate.frame(1220, menu = true, game = true)
        assertEquals(READY, gate.frame(1340, menu = true, game = true))
    }
    @Test fun eachPauseCanProduceOnlyOneClick() {
        val gate = gate()
        gate.frame(1100); gate.frame(1220)
        assertEquals(READY, gate.frame(1340))
        assertEquals(WAIT, gate.frame(1460))
        assertEquals(WAIT, gate.frame(1580))
        assertEquals(WAIT, gate.frame(1700))
    }
    @Test fun deadlineAndCaptureGapsAreBounded() {
        val gate = gate()
        gate.frame(1100); gate.frame(1220)
        assertEquals(WAIT, gate.frame(4000))
        assertEquals(WAIT, gate.frame(4120))
        assertEquals(READY, gate.frame(4240))
        val expired = PauseRestartGate("song/IN", 100, true)
        assertEquals(TIMEOUT, expired.frame(12101))
        assertEquals(WAIT, expired.frame(12221))
    }
    @Test fun minimumFrameSpanPreventsBurstConfirmation() {
        val gate = gate()
        gate.frame(1100); gate.frame(1101)
        assertEquals(WAIT, gate.frame(1102))
        assertEquals(READY, gate.frame(1340))
    }
}
