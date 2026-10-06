package io.github.phiscript.capture
import kotlin.math.roundToLong
/** Phira normal retry: Starting animation, then chart time at max(audioTime-totalOffset,0). */
internal object PhiraRestartClock {
    fun epoch(retryTouchDownMs: Long, startDelayMs: Int, chartOffsetSeconds: Double,
              globalOffsetMs: Int, touchOffsetMs: Int): Long {
        require(retryTouchDownMs >= 0 && startDelayMs in 0..5000)
        require(chartOffsetSeconds.isFinite() && globalOffsetMs in -2000..2000 && touchOffsetMs in -500..500)
        val offsetMs = maxOf(0.0, chartOffsetSeconds * 1000 + globalOffsetMs)
        require(offsetMs <= 120_000) { "Phira 起始偏移超出支持范围" }
        return Math.addExact(retryTouchDownMs, startDelayMs.toLong() + offsetMs.roundToLong() + touchOffsetMs)
    }
}
