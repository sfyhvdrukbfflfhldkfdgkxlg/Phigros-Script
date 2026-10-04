package io.github.phiscript.capture

/** Each state owns its gate; capture frames from before a menu action cannot confirm the next state. */
internal class FreshFrameConsensus(
    private val requiredFrames: Int = 2,
    private val minimumSpanMs: Long = 120,
    private val maximumGapMs: Long = 2500,
    private val afterFrameMs: Long = Long.MIN_VALUE
) {
    private var lastFrame = afterFrameMs
    private var firstFrame = 0L
    private var previous: String? = null
    private var count = 0
    init { require(requiredFrames >= 2 && minimumSpanMs > 0 && maximumGapMs > 0) }
    fun observe(key: String?, frameMs: Long): Boolean {
        if (frameMs <= lastFrame) return false
        val gap = if (lastFrame == Long.MIN_VALUE) 0L else frameMs - lastFrame
        lastFrame = frameMs
        if (key == null) { previous = null; count = 0; return false }
        if (previous != key || gap > maximumGapMs) {
            previous = key; count = 1; firstFrame = frameMs
        } else count++
        return count >= requiredFrames && frameMs - firstFrame >= minimumSpanMs
    }
}
