package io.github.phiscript.capture

import android.os.SystemClock
import io.github.phiscript.RuntimeState
import io.github.phiscript.SessionSettings
import io.github.phiscript.assets.ChartLibrary
import io.github.phiscript.engine.Chart
import io.github.phiscript.engine.Viewport
import io.github.phiscript.input.TouchService
import io.github.phiscript.vision.GameplayDetector
import io.github.phiscript.vision.Identity
import io.github.phiscript.vision.OcrConfig
import io.github.phiscript.vision.SongIdentifier
import io.github.phiscript.vision.VisualSynchronizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SessionEngine(
    private val frames: FrameStore,
    private val library: ChartLibrary,
    private val settings: SessionSettings,
    private val preview: Boolean,
    private val stop: AtomicBoolean
) {
    private var lastFrame = -1L
    private var nextDiagnostic = 0L
    private data class Prepared(val chart: Chart, val identity: Identity, val afterFrame: Long,
                                val width: Int, val height: Int)
    private data class Aligned(val epoch: Long, val width: Int, val height: Int)

    fun run() {
        val recognizer = SongIdentifier(OcrConfig(settings.titleRoi, settings.difficultyRoi))
        RuntimeState.log(when {
            preview -> "只识别模式：不会点击暂停或恢复，可手动暂停后检查识别"
            settings.pauseBeforeAlign -> "等待进入歌曲：检测暂停图标 → 双击暂停 → 读取谱面 → 恢复对齐"
            else -> "自动演奏待命：直接识别并对齐"
        })
        try {
            while (!stop.get()) {
                val prepared = if (!preview && settings.pauseBeforeAlign) preparePaused(recognizer)
                    else preparePassive(recognizer)
                if (prepared == null || stop.get()) return
                val aligned = align(prepared, recognizer) ?: return
                RuntimeState.log("已对齐。此前经过的音符不会补打")
                if (preview) {
                    RuntimeState.log("识别测试完成，未发送任何触摸")
                    return
                }
                play(prepared.chart, aligned, recognizer)
                if (!stop.get()) awaitNextSong(recognizer)
            }
        } finally { recognizer.close() }
    }

    /** No title OCR is required before pausing: entry is detected from the actual pause glyph. */
    private fun preparePaused(recognizer: SongIdentifier): Prepared? {
        val detector = GameplayDetector(settings.pauseRoi)
        val pausedAlready = FreshFrameConsensus()
        var width = 0
        var height = 0
        var requestedAt = 0L
        var nextOcr = 0L
        var nonGameplay = false
        var entered = false
        while (!stop.get() && !entered) {
            if (TouchService.current?.gameForeground != true) {
                detector.reset()
                sleep(150)
                continue
            }
            val frame = freshFrame()
            if (frame == null) { sleep(60); continue }
            frame.use {
                val hit = detector.observe(it.bitmap, it.uptimeMs)
                width = it.screenWidth; height = it.screenHeight
                if (hit != null && !nonGameplay) {
                    RuntimeState.log("检测到演奏画面，双击暂停键")
                    control(hit.pauseX, hit.pauseY, 2, width, height)
                    requestedAt = SystemClock.uptimeMillis()
                    entered = true
                } else if (SystemClock.uptimeMillis() >= nextOcr) {
                    val observation = recognizer.inspect(it.bitmap, library.songs())
                    nonGameplay = observation.pauseOrResult
                    if (observation.resultScreen) detector.reset()
                    nextOcr = SystemClock.uptimeMillis() + 650
                    if (pausedAlready.observe(if (observation.pauseMenu != null) "pause" else null, it.uptimeMs)) {
                        RuntimeState.log("已在暂停菜单，开始读取歌曲信息")
                        requestedAt = it.uptimeMs
                        entered = true
                    }
                    diagnostic("等待暂停图标：" + recognizer.lastDiagnostic +
                        "；未检测到时可校准暂停图标搜索区域")
                }
            }
        }
        if (stop.get()) return null
        val menuGate = FreshFrameConsensus(afterFrameMs = requestedAt)
        val identityGate = FreshFrameConsensus(afterFrameMs = requestedAt)
        val deadline = SystemClock.uptimeMillis() + 20000
        var confirmedPause = false
        var identity: Identity? = null
        while (!stop.get() && SystemClock.uptimeMillis() < deadline && identity == null) {
            requireForeground()
            val frame = freshFrame()
            if (frame == null) { sleep(70); continue }
            frame.use {
                checkDimensions(it, width, height)
                val observation = recognizer.inspect(it.bitmap, library.songs())
                check(!observation.resultScreen) { "已到结算画面，请重新进入歌曲" }
                val menu = observation.pauseMenu != null
                if (menuGate.observe(if (menu) "pause" else null, it.uptimeMs)) {
                    if (!confirmedPause) RuntimeState.log("暂停已确认，稳定识别曲名和难度…")
                    confirmedPause = true
                }
                val candidate = observation.identity?.takeIf { menu && confirmedPause &&
                    library.hasChart(it.songId, it.difficulty) }
                if (identityGate.observe(candidate?.let(::key), it.uptimeMs)) identity = candidate
                diagnostic("暂停识别：" + recognizer.lastDiagnostic)
            }
        }
        if (stop.get()) return null
        if (!confirmedPause) error("未确认暂停菜单，已停止；请校准暂停图标区域或双击间隔")
        val selected = identity ?: error("暂停后未能唯一识别曲名/难度，已保持暂停；请校准文字区域或修正曲名")
        RuntimeState.log("暂停识别成功：" + selected.title + " " + selected.difficulty)
        val chart = library.load(selected.songId, selected.difficulty)
        if (stop.get()) return null
        RuntimeState.log("谱面已加载，重新确认继续按钮")
        // Loading may take seconds; never click using a stale pre-load menu position.
        val ready = FreshFrameConsensus(afterFrameMs = SystemClock.uptimeMillis())
        val resumeDeadline = SystemClock.uptimeMillis() + 12000
        var resumedAt = 0L
        while (!stop.get() && resumedAt == 0L && SystemClock.uptimeMillis() < resumeDeadline) {
            requireForeground()
            val frame = freshFrame()
            if (frame == null) { sleep(70); continue }
            frame.use {
                checkDimensions(it, width, height)
                val observation = recognizer.inspect(it.bitmap, library.songs())
                val sameIdentity = observation.identity == null || key(observation.identity) == key(selected)
                check(sameIdentity && !observation.resultScreen) { "歌曲信息发生变化，未恢复播放" }
                val menu = observation.pauseMenu
                if (ready.observe(if (menu != null) "ready" else null, it.uptimeMs) &&
                    menu != null && SystemClock.uptimeMillis() - it.uptimeMs < 900) {
                    control(menu.resume.x, menu.resume.y, 1, width, height)
                    resumedAt = SystemClock.uptimeMillis()
                    RuntimeState.log("已点击继续，等待暂停菜单消失及画面恢复")
                }
            }
        }
        if (stop.get()) return null
        check(resumedAt > 0L) { "无法确认继续按钮，已保持暂停；可手动恢复并使用只识别模式检查" }
        val running = FreshFrameConsensus(afterFrameMs = resumedAt)
        val runningDeadline = SystemClock.uptimeMillis() + 15000
        while (!stop.get() && SystemClock.uptimeMillis() < runningDeadline) {
            requireForeground()
            val frame = freshFrame()
            if (frame == null) { sleep(70); continue }
            var accepted = false
            var timestamp = 0L
            frame.use {
                checkDimensions(it, width, height)
                val observation = recognizer.inspect(it.bitmap, library.songs())
                accepted = running.observe(if (!observation.pauseOrResult) "running" else null, it.uptimeMs)
                timestamp = it.uptimeMs
            }
            if (accepted) {
                RuntimeState.log("恢复已确认，丢弃暂停前时钟，从移动音符重新对齐")
                return Prepared(chart, selected, timestamp, width, height)
            }
        }
        if (stop.get()) return null
        error("恢复后菜单未消失，已停止；不会重复点击继续按钮")
    }

    private fun preparePassive(recognizer: SongIdentifier): Prepared? {
        val identities = FreshFrameConsensus()
        while (!stop.get()) {
            if (!preview && TouchService.current?.gameForeground != true) { sleep(150); continue }
            val frame = freshFrame()
            if (frame == null) { sleep(80); continue }
            var selected: Identity? = null
            var width = 0
            var height = 0
            frame.use {
                val observation = recognizer.inspect(it.bitmap, library.songs())
                val candidate = observation.identity?.takeUnless { observation.resultScreen }
                    ?.takeIf { library.hasChart(it.songId, it.difficulty) }
                if (identities.observe(candidate?.let(::key), it.uptimeMs)) selected = candidate
                width = it.screenWidth; height = it.screenHeight
                diagnostic(recognizer.lastDiagnostic)
            }
            if (selected != null) {
                RuntimeState.log("识别：" + selected!!.title + " " + selected!!.difficulty)
                return Prepared(library.load(selected!!.songId, selected!!.difficulty),
                    selected!!, SystemClock.uptimeMillis(), width, height)
            }
            sleep(160)
        }
        return null
    }

    private fun align(prepared: Prepared, recognizer: SongIdentifier): Aligned? {
        val synchronizer = VisualSynchronizer(prepared.chart, settings.viewport)
        val deadline = SystemClock.uptimeMillis() + 60000
        var nextMenuCheck = 0L
        var menuVisible = false
        RuntimeState.log("等待移动音符对齐；倒计时和静止画面不会启动演奏")
        while (!stop.get() && SystemClock.uptimeMillis() < deadline) {
            if (!preview) requireForeground()
            val frame = freshFrame()
            if (frame == null) { sleep(55); continue }
            var result: Aligned? = null
            frame.use {
                checkDimensions(it, prepared.width, prepared.height)
                if (it.uptimeMs <= prepared.afterFrame) return@use
                if (SystemClock.uptimeMillis() >= nextMenuCheck) {
                    menuVisible = recognizer.detectPaused(it.bitmap)
                    nextMenuCheck = SystemClock.uptimeMillis() + 700
                }
                if (menuVisible) {
                    synchronizer.reset()
                    diagnostic("暂停或结算界面仍可见，等待恢复")
                } else {
                    val epoch = synchronizer.observe(it.bitmap, it.uptimeMs - settings.captureLagMs)
                    if (epoch != null) result = Aligned(epoch, it.screenWidth, it.screenHeight)
                    diagnostic("对齐中：" + synchronizer.lastDiagnostic)
                }
            }
            if (result != null) return result
            sleep(55)
        }
        if (!stop.get()) RuntimeState.log("本次对齐超时，已停止；请检查玩法视口、曲名/难度与运行记录")
        return null
    }

    private fun play(chart: Chart, aligned: Aligned, recognizer: SongIdentifier) {
        val service = TouchService.current ?: error("无障碍服务已断开")
        val touchStop = AtomicBoolean(stop.get())
        val activeTouch = RuntimeState.ActiveTouch(stop, touchStop)
        RuntimeState.activeTouchStop.set(activeTouch)
        if (stop.get()) touchStop.set(true)
        val failure = AtomicReference<Throwable?>(null)
        val rect = settings.viewport
        val viewport = Viewport(rect.left * aligned.width, rect.top * aligned.height,
            rect.width() * aligned.width, rect.height() * aligned.height)
        val player = Thread({
            try { service.play(chart, aligned.epoch + settings.touchOffsetMs, viewport, touchStop, RuntimeState::log) }
            catch (e: Throwable) { failure.set(e) }
        }, "phi-touch").apply { start() }
        try {
            while (player.isAlive && !stop.get()) {
                if (!service.gameForeground) { touchStop.set(true); break }
                freshFrame()?.use {
                    if (it.screenWidth != aligned.width || it.screenHeight != aligned.height ||
                        recognizer.detectPaused(it.bitmap)) {
                        RuntimeState.log("检测到暂停、结算或尺寸变化，停止本次演奏")
                        touchStop.set(true)
                    }
                }
                if (touchStop.get()) break
                sleep(350)
            }
        } finally {
            touchStop.set(true)
            player.join(3000)
            RuntimeState.activeTouchStop.compareAndSet(activeTouch, null)
            if (player.isAlive) { stop.set(true); RuntimeState.log("触控线程未及时退出，结束会话") }
        }
        failure.get()?.let { RuntimeState.log("演奏停止：" + it.message) }
    }

    /** Do not double-tap pause again in the same song after a cancelled or completed playback. */
    private fun awaitNextSong(recognizer: SongIdentifier) {
        RuntimeState.log("本曲处理结束，等待结算或离开游戏后再次进入")
        val exit = FreshFrameConsensus()
        while (!stop.get()) {
            if (TouchService.current?.gameForeground != true) return
            val frame = freshFrame()
            if (frame == null) { sleep(120); continue }
            var finished = false
            frame.use {
                val observation = recognizer.inspect(it.bitmap, emptyList())
                finished = exit.observe(if (observation.resultScreen) "result" else null, it.uptimeMs)
            }
            if (finished) return
            sleep(350)
        }
    }

    private fun control(x: Float, y: Float, count: Int, width: Int, height: Int) {
        requireForeground()
        val service = TouchService.current ?: error("无障碍服务已断开")
        service.tapNormalized(x, y, count, settings.doubleTapIntervalMs, width, height, stop)
    }
    private fun freshFrame(): CapturedFrame? {
        val frame = frames.snapshot() ?: return null
        if (frame.uptimeMs <= lastFrame || frame.bitmap.width <= frame.bitmap.height ||
            SystemClock.uptimeMillis() - frame.uptimeMs > 900) {
            frame.close(); return null
        }
        lastFrame = frame.uptimeMs
        return frame
    }
    private fun requireForeground() {
        check(!stop.get() && TouchService.current?.gameForeground == true) { "会话已停止或 Phigros 已离开前台" }
    }
    private fun checkDimensions(frame: CapturedFrame, width: Int, height: Int) {
        check(frame.screenWidth == width && frame.screenHeight == height) { "屏幕尺寸变化，请重新开始" }
    }
    private fun key(identity: Identity) = identity.songId + "/" + identity.difficulty
    private fun diagnostic(message: String) {
        val now = SystemClock.uptimeMillis()
        if (now >= nextDiagnostic) { RuntimeState.log(message); nextDiagnostic = now + 4000 }
    }
    private fun sleep(ms: Long) { if (!stop.get()) Thread.sleep(ms) }
}
