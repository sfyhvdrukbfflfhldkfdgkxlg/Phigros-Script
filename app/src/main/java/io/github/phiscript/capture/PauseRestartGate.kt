package io.github.phiscript.capture

/** One calibrated menu click belongs to a bounded, confirmed session pause transaction. */
internal class PauseRestartGate(
    private val expectedKey: String,
    private val enteredAtMs: Long,
    private val ownPauseRequest: Boolean,
    private val settleMs: Long = 1000
) {
    enum class Outcome { WAIT, READY, CHANGED, RESULT, TIMEOUT }
    private var finished = false
    private var lastFrame = enteredAtMs
    private var firstEligible = 0L
    private var count = 0

    init {
        require(expectedKey.isNotBlank() && enteredAtMs >= 0)
        require(settleMs in 900..4000)
    }

    fun observe(frameMs: Long, nowMs: Long, observedKey: String?, textPauseMenu: Boolean,
                gameplayVisible: Boolean, resultScreen: Boolean): Outcome {
        if (finished) return Outcome.WAIT
        if (nowMs - enteredAtMs > 12000) return finish(Outcome.TIMEOUT)
        if (frameMs <= lastFrame) return Outcome.WAIT
        val gap = frameMs - lastFrame
        lastFrame = frameMs
        if (frameMs > nowMs || nowMs - frameMs >= 900) { reset(); return Outcome.WAIT }
        if (resultScreen) return finish(Outcome.RESULT)
        if (observedKey != null && observedKey != expectedKey) return finish(Outcome.CHANGED)
        if (gap > 2500) reset()
        if (frameMs - enteredAtMs < settleMs || (gameplayVisible && !textPauseMenu) ||
            (!ownPauseRequest && !textPauseMenu)) {
            reset()
            return Outcome.WAIT
        }
        if (count == 0) firstEligible = frameMs
        count++
        // Three frames also allow the gameplay detector to establish positive running evidence.
        return if (count >= 3 && frameMs - firstEligible >= 240) finish(Outcome.READY)
            else Outcome.WAIT
    }
    private fun reset() { count = 0; firstEligible = 0L }
    private fun finish(outcome: Outcome): Outcome { finished = true; return outcome }
}
