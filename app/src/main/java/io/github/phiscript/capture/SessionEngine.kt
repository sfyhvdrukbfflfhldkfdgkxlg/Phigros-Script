package io.github.phiscript.capture

import android.os.SystemClock
import io.github.phiscript.RuntimeState
import io.github.phiscript.SessionSettings
import io.github.phiscript.input.TouchService
import io.github.phiscript.input.VisualTouchFrame
import io.github.phiscript.vision.GameplayDetector
import io.github.phiscript.vision.VisualNoteDetector
import io.github.phiscript.vision.VisualNoteTracker
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Pure visual mode: no chart loading, pause calibration, or chart epoch fitting. */
internal class SessionEngine(
    private val frames: FrameStore,
    private val settings: SessionSettings,
    private val preview: Boolean,
    private val stop: AtomicBoolean,
    private val overlay: RecognitionOverlay
) {
    private var lastFrame = -1L
    private var nextDiagnostic = 0L
    private var nextToken = 0L
    private data class Selection(val width: Int, val height: Int)

    fun run() {
        RuntimeState.log("纯视觉模式：直接检测判定线与四类音符，不读取谱面，不进行音符对齐")
        try {
            while (!stop.get()) {
                val selected = recognizeAndConfirm() ?: return
                val afterHide = overlay.hide()
                if (stop.get()) return
                playVisual(selected, afterHide)
                // A completed/stopped player cannot automatically replay against the same screen.
                stop.set(true)
            }
        } finally { runCatching { overlay.hide() } }
    }

    private fun recognizeAndConfirm(): Selection? {
        val detector = VisualNoteDetector(settings.viewport)
        val gameplay = GameplayDetector(settings.pauseRoi)
        var token: Long? = null
        var selected: Selection? = null
        var lastEvidence = -1L
        var ignoredUntil = 0L
        var shown = false
        overlay.status("纯视觉待命\n进入歌曲后等待可见判定线与暂停图标")
        while (!stop.get()) {
            if (!preview && TouchService.current?.gameForeground != true) {
                if (shown || token != null) overlay.hide()
                shown = false; token = null; selected = null
                gameplay.reset()
                sleep(80); continue
            }
            if (!shown) {
                overlay.status("纯视觉待命\n进入歌曲后等待可见判定线与暂停图标")
                shown = true
            }
            val now = SystemClock.uptimeMillis()
            val decision = overlay.consumeChoice()
            val pending = selected
            if (decision != null && token == decision.token && pending != null &&
                now - lastEvidence in 0..500) {
                if (decision.start && !preview) {
                    RuntimeState.log("已确认纯视觉演奏；隐藏悬浮窗，直接从当前画面演奏")
                    return pending
                }
                token = null; selected = null; ignoredUntil = now + 3000
                overlay.status("继续检测\n未开始演奏")
            }
            val frame = freshFrame()
            if (frame == null) {
                if (token != null && now - lastEvidence > 500) {
                    token = null; selected = null
                    overlay.status("画面已过期\n等待新的游戏画面")
                }
                sleep(20); continue
            }
            frame.use {
                val scene = detector.detect(it.bitmap)
                val active = gameplay.observe(it.bitmap, it.uptimeMs) != null
                val eligible = scene.reliable && scene.lines.isNotEmpty() && active
                if (eligible) lastEvidence = it.uptimeMs
                if (token != null && (SystemClock.uptimeMillis() - lastEvidence > 500 ||
                    selected?.width != it.screenWidth || selected?.height != it.screenHeight)) {
                    token = null; selected = null
                    overlay.status("画面发生变化\n等待游戏画面与判定线")
                }
                if (token == null && eligible && SystemClock.uptimeMillis() >= ignoredUntil) {
                    selected = Selection(it.screenWidth, it.screenHeight)
                    token = ++nextToken
                    overlay.offer(token!!, "Phigros · 纯视觉", "实时画面，无需谱库", preview)
                    RuntimeState.log("已检测到游戏画面与判定线；" + if (preview) "只识别" else "等待悬浮窗确认")
                }
                diagnostic("视觉检测：判定线 " + scene.lines.size + " · 音符 " + scene.notes.size +
                    if (scene.reliable) "" else " · 当前画面不可靠")
            }
            sleep(25)
        }
        return null
    }

    private fun playVisual(selected: Selection, afterHide: Long) {
        val service = TouchService.current ?: error("无障碍服务已断开")
        val detector = VisualNoteDetector(settings.viewport)
        val tracker = VisualNoteTracker()
        val gameplay = GameplayDetector(settings.pauseRoi)
        val touchStop = AtomicBoolean(stop.get())
        val activeTouch = RuntimeState.ActiveTouch(stop, touchStop)
        val snapshot = AtomicReference<VisualTouchFrame?>(null)
        val failure = AtomicReference<Throwable?>(null)
        RuntimeState.activeTouchStop.set(activeTouch)
        var player: Thread? = null
        val beganAt = SystemClock.uptimeMillis()
        var lastGame = -1L
        var lastReliable = beganAt
        try {
            while (!stop.get() && !touchStop.get()) {
                check(service.gameForeground) { "游戏已离开前台" }
                if (player != null && player?.isAlive != true) break
                val wallNow = SystemClock.uptimeMillis()
                if (wallNow - maxOf(lastGame, beganAt) > 3000 || wallNow - lastReliable > 3000) {
                    RuntimeState.log("无法持续确认游戏画面或可见判定线，本次演奏已停止")
                    touchStop.set(true)
                    break
                }
                val frame = freshFrame()
                if (frame == null) { sleep(10); continue }
                frame.use {
                    check(it.screenWidth == selected.width && it.screenHeight == selected.height) {
                        "屏幕尺寸变化，请重新启动"
                    }
                    if (it.uptimeMs <= afterHide) return@use
                    val scene = detector.detect(it.bitmap)
                    if (gameplay.observe(it.bitmap, it.uptimeMs) != null) lastGame = it.uptimeMs
                    val now = SystemClock.uptimeMillis()
                    if (scene.reliable && scene.lines.isNotEmpty()) lastReliable = it.uptimeMs
                    val active = lastGame > afterHide && now - lastGame <= 250 && scene.reliable
                    val touches = if (active) tracker.observe(scene, it.uptimeMs, now,
                        (settings.captureLagMs.coerceIn(0, 120) -
                            settings.touchOffsetMs.coerceIn(-80, 80)).coerceIn(-80, 120))
                    else { tracker.reset(); emptyList() }
                    snapshot.set(VisualTouchFrame(it.uptimeMs, it.screenWidth, it.screenHeight, touches))
                    if (active && player == null) {
                        player = Thread({
                            try { service.playVisual(snapshot::get, touchStop, RuntimeState::log) }
                            catch (e: Throwable) { failure.set(e) }
                        }, "phi-visual-touch").apply { start() }
                        RuntimeState.log("纯视觉演奏开始；暂停、离开游戏或画面过期时停止触控")
                    }
                    if (now - maxOf(lastGame, beganAt) > 3000 || now - lastReliable > 3000) {
                        RuntimeState.log("无法持续确认游戏画面或可见判定线，本次演奏已停止")
                        touchStop.set(true)
                    }
                }
                if (player != null && player?.isAlive != true) break
                sleep(10)
            }
        } finally {
            touchStop.set(true)
            snapshot.set(null)
            player?.join(3000)
            RuntimeState.activeTouchStop.compareAndSet(activeTouch, null)
            if (player?.isAlive == true) { stop.set(true); RuntimeState.log("触控线程未及时退出") }
        }
        failure.get()?.let { RuntimeState.log("纯视觉演奏停止：" + it.message) }
    }

    private fun freshFrame(): CapturedFrame? {
        val frame = frames.snapshot() ?: return null
        if (frame.uptimeMs <= lastFrame || frame.bitmap.width <= frame.bitmap.height ||
            SystemClock.uptimeMillis() - frame.uptimeMs !in 0..180) {
            frame.close(); return null
        }
        lastFrame = frame.uptimeMs
        return frame
    }
    private fun diagnostic(message: String) {
        val now = SystemClock.uptimeMillis()
        if (now >= nextDiagnostic) { RuntimeState.log(message); nextDiagnostic = now + 4000 }
    }
    private fun sleep(ms: Long) { if (!stop.get()) Thread.sleep(ms) }
}
