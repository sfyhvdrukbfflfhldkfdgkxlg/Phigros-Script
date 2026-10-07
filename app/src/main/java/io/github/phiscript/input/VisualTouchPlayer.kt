package io.github.phiscript.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** Streams contacts without a chart, song clock, or fitted note timing. */
internal object VisualTouchPlayer {
    private const val CHUNK_MS = 32L
    private const val TAP_MS = 20L
    fun play(service: AccessibilityService, screenWidth: Int, screenHeight: Int,
             snapshot: () -> VisualTouchFrame?, stop: AtomicBoolean,
             isAllowed: () -> Boolean, onStatus: (String) -> Unit) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "触控必须在后台线程执行" }
        require(screenWidth > 0 && screenHeight > 0)
        val thread = HandlerThread("VisualTouches")
        thread.start()
        val handler = Handler(thread.looper)
        val state = VisualTouchState()
        val limit = min(10, GestureDescription.getMaxStrokeCount())
        var active = linkedMapOf<Long, LiveStroke>()
        var uncertain: LinkedHashMap<Long, LiveStroke>? = null
        var latest: VisualTouchFrame? = null
        val began = SystemClock.uptimeMillis()
        var started = false
        fun shouldStop() = stop.get() || !isAllowed()
        fun pixel(touch: VisualTouch): Pixel = Pixel(
            (touch.x * screenWidth).coerceIn(0f, (screenWidth - 1).toFloat()),
            (touch.y * screenHeight).coerceIn(0f, (screenHeight - 1).toFloat()))
        try {
            while (!shouldStop()) {
                snapshot()?.let { frame ->
                    check(frame.screenWidth == screenWidth && frame.screenHeight == screenHeight) {
                        "屏幕尺寸变化，已停止视觉演奏"
                    }
                    latest = frame
                }
                val now = SystemClock.uptimeMillis()
                val frame = latest
                if (frame == null) {
                    check(now - began <= 3000) { "未收到视觉识别画面，已停止触摸" }
                    Thread.sleep(8)
                    continue
                }
                val demand = state.update(frame, now)
                if (!started) { onStatus("纯视觉演奏已开始"); started = true }
                val desired = demand.holds.associateBy { it.id }
                check(demand.shots.none { active.containsKey(it.id) }) { "视觉音符类型发生冲突，已停止触摸" }
                val count = (active.keys + desired.keys).toSet().size + demand.shots.size
                check(count <= limit) { "视觉同时触摸数量超过系统上限：$limit" }
                if (count == 0) { Thread.sleep(6); continue }
                val builder = GestureDescription.Builder()
                val next = linkedMapOf<Long, LiveStroke>()
                active.forEach { (id, record) ->
                    val touch = desired[id]
                    val to = touch?.let(::pixel) ?: record.position
                    val keep = touch != null
                    val stroke = record.stroke.continueStroke(
                        path(record.position, to), 0, if (keep) CHUNK_MS else 1L, keep)
                    builder.addStroke(stroke)
                    if (keep) next[id] = LiveStroke(stroke, to)
                }
                demand.holds.filterNot { active.containsKey(it.id) }.forEach { touch ->
                    val point = pixel(touch)
                    val stroke = GestureDescription.StrokeDescription(path(point, point), 0, CHUNK_MS, true)
                    builder.addStroke(stroke)
                    next[touch.id] = LiveStroke(stroke, point)
                }
                demand.shots.forEach { touch ->
                    val from = pixel(touch)
                    val flick = touch.kind == VisualTouchKind.FLICK
                    val to = if (flick) FlickGeometry.endpoint(from.x, from.y,
                        touch.flickDx * screenWidth, touch.flickDy * screenHeight,
                        screenWidth, screenHeight).let { Pixel(it.x, it.y) } else from
                    builder.addStroke(GestureDescription.StrokeDescription(
                        path(from, to), 0, if (flick) CHUNK_MS else TAP_MS, false))
                }
                val done = CountDownLatch(1)
                val completed = AtomicBoolean(false)
                val wasCancelled = AtomicBoolean(false)
                val gesture = builder.build()
                val dispatchedAt = SystemClock.uptimeMillis()
                if (shouldStop()) break
                check(service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        completed.set(true); done.countDown()
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) {
                        wasCancelled.set(true); done.countDown()
                    }
                }, handler)) { "Android 拒绝视觉触摸" }
                uncertain = next
                var aborted = false
                while (!done.await(20, TimeUnit.MILLISECONDS)) {
                    if (shouldStop()) { aborted = true; break }
                    check(SystemClock.uptimeMillis() - dispatchedAt < 1500) { "视觉手势回调超时，已停止触摸" }
                }
                if (wasCancelled.get()) {
                    active.clear(); uncertain = null
                    error("系统取消了视觉手势，已停止触摸")
                }
                if (aborted) break
                check(completed.get()) { "视觉手势未完成" }
                active = next
                uncertain = null
            }
            onStatus("已停止视觉触摸")
        } finally {
            val interrupted = Thread.interrupted()
            val held = uncertain ?: active
            if (held.isNotEmpty()) runCatching {
                val builder = GestureDescription.Builder()
                held.values.forEach { record ->
                    builder.addStroke(record.stroke.continueStroke(
                        path(record.position, record.position), 0, 1, false))
                }
                val done = CountDownLatch(1)
                if (service.dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) { done.countDown() }
                    override fun onCancelled(gestureDescription: GestureDescription) { done.countDown() }
                }, handler)) done.await(250, TimeUnit.MILLISECONDS)
            }
            thread.quitSafely()
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
    private data class Pixel(val x: Float, val y: Float)
    private data class LiveStroke(val stroke: GestureDescription.StrokeDescription, val position: Pixel)
    private fun path(from: Pixel, to: Pixel): Path = Path().apply {
        moveTo(from.x, from.y)
        if (from != to) lineTo(to.x, to.y)
    }
}
