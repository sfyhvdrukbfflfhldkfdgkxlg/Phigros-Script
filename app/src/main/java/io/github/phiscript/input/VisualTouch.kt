package io.github.phiscript.input

/** Coordinates and flick vectors are normalized to the actual captured screen. */
enum class VisualTouchKind { TAP, HOLD, DRAG, FLICK }
data class VisualTouch(
    val id: Long, val kind: VisualTouchKind, val x: Float, val y: Float,
    val flickDx: Float = 0f, val flickDy: Float = -0.035f
)
data class VisualTouchFrame(
    val capturedAtMs: Long, val screenWidth: Int, val screenHeight: Int,
    val touches: List<VisualTouch>
)
internal data class VisualTouchDemand(val holds: List<VisualTouch>, val shots: List<VisualTouch>)

/** Retains shot identities across frames so a visible note cannot be tapped twice. */
internal class VisualTouchState {
    private var width = 0
    private var height = 0
    private var latestFrame = Long.MIN_VALUE
    private val consumed = LinkedHashMap<Long, Long>()
    fun update(frame: VisualTouchFrame, nowMs: Long): VisualTouchDemand {
        require(frame.screenWidth > 0 && frame.screenHeight > 0) { "视觉帧尺寸无效" }
        check(frame.capturedAtMs <= nowMs &&
            nowMs - frame.capturedAtMs <= MAX_FRAME_AGE_MS) { "视觉画面超过 350 ms 未更新，已停止触摸" }
        check(frame.capturedAtMs >= latestFrame) { "视觉画面时间倒退，已停止触摸" }
        if (width == 0) { width = frame.screenWidth; height = frame.screenHeight }
        check(width == frame.screenWidth && height == frame.screenHeight) { "屏幕尺寸变化，已停止视觉演奏" }
        require(frame.touches.size <= 10) { "视觉同时触摸数量超过上限" }
        require(frame.touches.map { it.id }.distinct().size == frame.touches.size) { "视觉音符身份重复" }
        frame.touches.forEach {
            require(it.id >= 0 && it.x.isFinite() && it.y.isFinite() &&
                it.x in 0f..1f && it.y in 0f..1f &&
                it.flickDx.isFinite() && it.flickDy.isFinite() &&
                it.flickDx in -1f..1f && it.flickDy in -1f..1f) { "视觉音符坐标无效" }
        }
        latestFrame = frame.capturedAtMs
        consumed.entries.removeAll { frame.capturedAtMs - it.value > RETAIN_SHOT_MS }
        val holds = ArrayList<VisualTouch>()
        val shots = ArrayList<VisualTouch>()
        frame.touches.forEach { touch ->
            when (touch.kind) {
                VisualTouchKind.HOLD, VisualTouchKind.DRAG -> holds.add(touch)
                VisualTouchKind.TAP, VisualTouchKind.FLICK -> {
                    if (consumed.put(touch.id, frame.capturedAtMs) == null) shots.add(touch)
                }
            }
        }
        check(consumed.size <= 4096) { "视觉音符身份数量异常，已停止触摸" }
        return VisualTouchDemand(holds, shots)
    }
    companion object {
        const val MAX_FRAME_AGE_MS = 350L
        private const val RETAIN_SHOT_MS = 10_000L
    }
}
