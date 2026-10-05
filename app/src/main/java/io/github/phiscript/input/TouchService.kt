package io.github.phiscript.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.content.Intent
import io.github.phiscript.capture.CaptureService
import io.github.phiscript.Diagnostics
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import io.github.phiscript.engine.Chart
import io.github.phiscript.engine.Point
import io.github.phiscript.engine.TouchEvent
import io.github.phiscript.engine.Viewport
import io.github.phiscript.engine.touchPosition
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** Accessibility injects touches and observes foreground package names; it grants no file access. */
class TouchService : AccessibilityService() {
    @Volatile var foregroundPackage: String? = null
        private set
    val gameForeground: Boolean get() = foregroundPackage == GAME_PACKAGE
    private val playing = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        Diagnostics.record(this, "无障碍服务已创建")
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        foregroundPackage = try { rootInActiveWindow?.packageName?.toString() }
        catch (e: RuntimeException) { Diagnostics.record(this, "读取初始窗口失败", e); null }
        current = this
        Diagnostics.record(this, "无障碍服务已连接")
    }
    override fun onUnbind(intent: Intent?): Boolean {
        cancelCurrent()
        foregroundPackage = null
        if (current === this) current = null
        Diagnostics.record(this, "无障碍服务解除绑定")
        return super.onUnbind(intent)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val name = when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val reported = event.packageName?.toString() ?: return
                // Non-focusable overlay windows also emit STATE_CHANGED. Use actual focus for
                // our own overlay, while a real MainActivity transition must stop touches.
                if (reported == packageName &&
                    event.className?.toString() != "io.github.phiscript.MainActivity")
                    focusedWindowPackage() ?: reported
                else reported
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> focusedWindowPackage()
            else -> return
        }
        foregroundPackage = name
        if (playing.get() && name != GAME_PACKAGE) cancelled.set(true)
    }

    private fun focusedWindowPackage(): String? = try {
        val window = windows.firstOrNull { it.isFocused }
        if (window == null) null else {
            val root = window.root
            try {
                // A focused system window (for example notification shade) is not gameplay.
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) ""
                else root?.packageName?.toString()
            } finally {
                @Suppress("DEPRECATION")
                root?.recycle()
            }
        }
    } catch (_: RuntimeException) { null }
    override fun onInterrupt() {
        cancelCurrent()
        Diagnostics.record(this, "无障碍服务收到中断")
    }
    override fun onDestroy() {
        cancelCurrent()
        foregroundPackage = null
        if (current === this) current = null
        Diagnostics.record(this, "无障碍服务已销毁")
        super.onDestroy()
    }
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            cancelCurrent()
            runCatching {
                startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
            }
        }
        return false
    }
    fun cancelCurrent() { cancelled.set(true) }


    /** Menu controls share the same single gesture owner as note playback. */
    fun tapNormalized(x: Float, y: Float, count: Int, intervalMs: Int,
                      screenWidth: Int, screenHeight: Int, stop: AtomicBoolean) {
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
        require(x.isFinite() && y.isFinite() && x > 0f && x < 1f && y > 0f && y < 1f)
        require(count in 1..2 && intervalMs in 80..300)
        check(playing.compareAndSet(false, true)) { "已有触控在运行" }
        cancelled.set(false)
        try {
            repeat(count) {
                check(!stop.get() && !cancelled.get() && gameForeground) { "菜单操作已停止" }
                val screen = screenDimensions()
                check(screen.first == screenWidth && screen.second == screenHeight) { "屏幕尺寸变化，请重新开始" }
                val point = Point(x * screenWidth, y * screenHeight)
                val gesture = GestureDescription.Builder().addStroke(
                    GestureDescription.StrokeDescription(path(point, point), 0, 35, false)).build()
                val done = CountDownLatch(1)
                val completed = AtomicBoolean(false)
                val began = SystemClock.uptimeMillis()
                check(dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        completed.set(true); done.countDown()
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) { done.countDown() }
                }, Handler(mainLooper))) { "Android 拒绝菜单点击" }
                while (!done.await(20, TimeUnit.MILLISECONDS)) {
                    check(!stop.get() && !cancelled.get() && gameForeground) { "菜单操作已停止" }
                    check(SystemClock.uptimeMillis() - began < 1500) { "菜单手势回调超时" }
                }
                check(completed.get()) { "菜单手势被系统取消" }
                if (it + 1 < count) {
                    val next = began + intervalMs
                    while (SystemClock.uptimeMillis() < next) {
                        check(!stop.get() && !cancelled.get() && gameForeground) { "菜单操作已停止" }
                        Thread.sleep(minOf(10, next - SystemClock.uptimeMillis()).coerceAtLeast(1))
                    }
                }
            }
        } finally { playing.set(false) }
    }

    /** Blocking worker; uptime epoch and REAL SCREEN pixels. Abort if over 70 ms behind. */
    fun play(chart: Chart, epochUptimeMs: Long, viewport: Viewport,
             stop: AtomicBoolean, onStatus: (String) -> Unit) {
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { "触控必须在后台线程执行" }
        check(playing.compareAndSet(false, true)) { "已有演奏在运行" }
        cancelled.set(false)
        val callbackThread = HandlerThread("PhigrosTouches")
        callbackThread.start()
        val callbackHandler = Handler(callbackThread.looper)
        var active: List<ActiveStroke> = emptyList()
        var uncertain: List<ActiveStroke>? = null
        try {
            check(gameForeground) { "Phigros 尚未处于前台" }
            val screen = screenDimensions()
            require(viewport.left >= 0 && viewport.top >= 0 &&
                viewport.right <= screen.first + 1 && viewport.bottom <= screen.second + 1) {
                "玩法视口超出屏幕，请重新校准"
            }
            val initialTime = SystemClock.uptimeMillis() - epochUptimeMs
            val all = TouchEvent.forChart(chart)
            val events = all.filter { (it.note.timeSeconds * 1000) >= initialTime + 25 }
            if (events.isEmpty()) { onStatus("对齐过晚，已无后续音符"); return }
            val skipped = all.size - events.size
            onStatus(if (skipped > 0) "开始演奏；跳过已错过的 $skipped 个音符" else "开始演奏")
            var cursor = 0
            var expectedBoundary: Long? = null
            val strokeLimit = min(10, GestureDescription.getMaxStrokeCount())
            fun shouldStop(): Boolean = stop.get() || cancelled.get() || !gameForeground
            fun point(event: TouchEvent, time: Long): Point {
                val p = chart.touchPosition(event, time, viewport)
                require(p.x.isFinite() && p.y.isFinite() && p.x >= 0 && p.x < screen.first &&
                    p.y >= 0 && p.y < screen.second) { "音符落点超出屏幕，已停止；请检查视口或特殊谱面" }
                return p
            }
            while ((cursor < events.size || active.isNotEmpty()) && !shouldStop()) {
                val base = SystemClock.uptimeMillis() - epochUptimeMs
                if (active.isNotEmpty() && expectedBoundary != null) {
                    check(base - expectedBoundary!! <= MAX_LATENESS_MS) { "连续触摸调度延迟超过 70 ms，已停止" }
                }
                if (active.isEmpty() && cursor < events.size && events[cursor].startMs > base) {
                    Thread.sleep((events[cursor].startMs - base).coerceIn(1, 12))
                    continue
                }
                var until = base + CHUNK_MS
                val room = strokeLimit - active.size
                if (cursor + room < events.size && events[cursor + room].startMs < until) {
                    until = events[cursor + room].startMs
                    check(until > base) { "同时触摸数量超过系统上限：$strokeLimit" }
                }
                val records = active.toMutableList()
                while (cursor < events.size && events[cursor].startMs < until) {
                    val event = events[cursor++]
                    check(base - event.startMs <= MAX_LATENESS_MS) { "触控调度延迟超过 70 ms，已停止" }
                    records.add(ActiveStroke(event, null, null))
                }
                check(records.isNotEmpty()) { "触控调度没有可执行事件" }
                val builder = GestureDescription.Builder()
                val next = ArrayList<ActiveStroke>()
                for (record in records) {
                    val event = record.event
                    val continued = record.stroke != null
                    val from = if (continued) base else maxOf(base, event.startMs)
                    val release = if (continued) event.endMs else maxOf(event.endMs, from + 2)
                    val to = maxOf(from + 1, minOf(release, until))
                    val keep = release > until
                    val startPoint = record.lastPoint ?: point(event, from)
                    val endPoint = if (continued && event.endMs <= base) startPoint else point(event, to)
                    val path = path(startPoint, endPoint)
                    val stroke = if (continued) {
                        record.stroke!!.continueStroke(path, 0, to - from, keep)
                    } else {
                        GestureDescription.StrokeDescription(path, from - base, to - from, keep)
                    }
                    builder.addStroke(stroke)
                    if (keep) next.add(ActiveStroke(event, stroke, endPoint))
                }
                val gesture = builder.build()
                val latch = CountDownLatch(1)
                val completed = AtomicBoolean(false)
                val wasCancelled = AtomicBoolean(false)
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        completed.set(true); latch.countDown()
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) {
                        wasCancelled.set(true); latch.countDown()
                    }
                }
                if (shouldStop()) break
                check(dispatchGesture(gesture, callback, callbackHandler)) { "Android 拒绝无障碍触摸" }
                uncertain = next
                check(latch.await(1500, TimeUnit.MILLISECONDS)) { "无障碍手势回调超时" }
                if (wasCancelled.get()) {
                    active = emptyList(); uncertain = null; error("系统取消了手势，已停止")
                }
                check(completed.get()) { "无障碍手势未完成" }
                active = next
                expectedBoundary = until
                uncertain = null
            }
            onStatus(if (shouldStop()) "已停止触摸" else "演奏完成")
        } finally {
            val held = uncertain ?: active
            if (held.isNotEmpty()) {
                runCatching {
                    val builder = GestureDescription.Builder()
                    held.forEach { record ->
                        val position = requireNotNull(record.lastPoint)
                        builder.addStroke(requireNotNull(record.stroke).continueStroke(
                            path(position, position), 0, 1, false))
                    }
                    val done = CountDownLatch(1)
                    val accepted = dispatchGesture(builder.build(), object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription) { done.countDown() }
                        override fun onCancelled(gestureDescription: GestureDescription) { done.countDown() }
                    }, callbackHandler)
                    if (accepted) done.await(250, TimeUnit.MILLISECONDS)
                }
            }
            callbackThread.quitSafely()
            playing.set(false)
        }
    }
    @Suppress("DEPRECATION")
    private fun screenDimensions(): Pair<Int, Int> {
        val manager = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = manager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            manager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }
    private data class ActiveStroke(val event: TouchEvent,
        val stroke: GestureDescription.StrokeDescription?, val lastPoint: Point?)
    companion object {
        const val GAME_PACKAGE = "com.PigeonGames.Phigros"
        private const val CHUNK_MS = 32L
        private const val MAX_LATENESS_MS = 70L
        @Volatile var current: TouchService? = null
            private set
        private fun path(from: Point, to: Point): Path = Path().apply {
            moveTo(from.x, from.y)
            if (from != to) lineTo(to.x, to.y)
        }
    }
}
