package io.github.phiscript.vision
import kotlin.math.abs
import kotlin.math.roundToLong

/** Capture and chart clocks must advance together; a frozen countdown is not a clock. */
internal class EpochConsensus(
    private val requiredFrames: Int = 4,
    private val minimumSpanMs: Long = 180,
    private val maximumSpreadMs: Double = 40.0,
    private val maximumFrameGapMs: Long = 800
) {
    private data class Sample(val epoch: Double, val frame: Long)
    private val samples = ArrayDeque<Sample>()
    val size: Int get() = samples.size
    init {
        require(requiredFrames >= 3)
        require(minimumSpanMs > 0 && maximumSpreadMs.isFinite() && maximumSpreadMs > 0 && maximumFrameGapMs > 0)
    }
    fun clear() = samples.clear()
    fun observe(epochUptimeMs: Double, frameUptimeMs: Long): Long? {
        if (!epochUptimeMs.isFinite()) { clear(); return null }
        val last = samples.lastOrNull()
        if (last != null) {
            if (frameUptimeMs <= last.frame) return null
            if (frameUptimeMs - last.frame > maximumFrameGapMs ||
                abs(last.epoch - epochUptimeMs) > maximumSpreadMs) clear()
        }
        val epochs = samples.map { it.epoch } + epochUptimeMs
        if (epochs.maxOrNull()!! - epochs.minOrNull()!! > maximumSpreadMs) clear()
        val current = Sample(epochUptimeMs, frameUptimeMs)
        samples.addLast(current)
        while (samples.size > maxOf(8, requiredFrames)) samples.removeFirst()
        val first = samples.first()
        val span = frameUptimeMs - first.frame
        if (samples.size < requiredFrames || span < minimumSpanMs) return null
        val chartAdvance = span.toDouble() - (epochUptimeMs - first.epoch)
        if (chartAdvance < span * 0.7 || chartAdvance > span * 1.3) {
            clear(); samples.addLast(current); return null
        }
        val ordered = samples.map { it.epoch }.sorted()
        return ordered[ordered.size / 2].roundToLong()
    }
}
