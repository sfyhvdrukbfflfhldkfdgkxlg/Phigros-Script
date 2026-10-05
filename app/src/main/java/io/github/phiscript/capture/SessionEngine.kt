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

internal class SessionEngine(
    private val frames: FrameStore,
    private val library: ChartLibrary,
    private val settings: SessionSettings,
    private val preview: Boolean,
    private val stop: AtomicBoolean,
    private val overlay: RecognitionOverlay
) {
    private var lastFrame = -1L
    private var nextDiagnostic = 0L
    private data class Selection(val identity: Identity, val width: Int, val height: Int)
    private data class Prepared(val chart: Chart, val identity: Identity, val afterFrame: Long,
                                val width: Int, val height: Int)
    private data class Aligned(val epoch: Long, val width: Int, val height: Int)

    fun run() {
        val recognizer = SongIdentifier(OcrConfig(settings.titleRoi, settings.difficultyRoi))
        RuntimeState.log(if (preview) "悬浮窗只识别：不会点击暂停、恢复或音符"
            else "悬浮窗识别待命：识别后由你确认，再暂停、恢复并重新对齐")
        try {
            while (!stop.get()) {
                val selected = recognizeAndConfirm(recognizer) ?: return
                val afterHide = overlay.hide()
                if (stop.get()) return
                val chart = try { library.load(selected.identity.songId, selected.identity.difficulty) }
                catch (e: Exception) {
                    throw IllegalStateException("谱面读取不可用：" + e.message +
                        "；请返回应用，导入同版本 Phigros APK 解析谱面", e)
                }
                if (stop.get()) return
                val prepared = preparePaused(recognizer, selected, chart, afterHide) ?: return
                val aligned = align(prepared, recognizer) ?: return
                RuntimeState.log("已对齐，悬浮窗保持隐藏；通知栏或音量减键可停止。此前经过的音符不会补打")
                play(prepared.chart, aligned, recognizer)
                if (!stop.get()) awaitNextOpportunity(recognizer)
            }
        } finally {
            runCatching { overlay.hide() }
            recognizer.close()
        }
    }

    /** Detection is passive. No control or note gesture can run before a fresh user confirmation. */
    private fun recognizeAndConfirm(recognizer: SongIdentifier): Selection? {
        var identities = FreshFrameConsensus()
        val confirmation = RecognitionConfirmation()
        var selected: Selection? = null
        var ignoredUntil = 0L
        var wasForeground = true
        overlay.status("等待曲名和难度…\n识别困难时可手动暂停")
        while (!stop.get()) {
            if (!preview && TouchService.current?.gameForeground != true) {
                if (wasForeground) {
                    confirmation.invalidate()
                    selected = null
                    identities = FreshFrameConsensus()
                    overlay.hide()
                }
                wasForeground = false
                sleep(150)
                continue
            }
            if (!wasForeground) overlay.status("等待曲名和难度…\n识别困难时可手动暂停")
            wasForeground = true
            val now = SystemClock.uptimeMillis()
            val decision = overlay.consumeChoice()
            val confirmedSelection = selected
            if (decision != null && confirmedSelection != null &&
                confirmation.pending(now)?.token == decision.token) {
                if (decision.start && !preview && confirmation.accept(decision.token, now)) {
                    RuntimeState.log("已确认演奏：" + confirmedSelection.identity.title + " " + confirmedSelection.identity.difficulty)
                    return confirmedSelection
                }
                confirmation.invalidate()
                selected = null
                identities = FreshFrameConsensus()
                ignoredUntil = now + 3000
                overlay.status("继续识别…\n尚未开始自动演奏")
            }
            val frame = freshFrame()
            if (frame == null) {
                if (selected != null && confirmation.pending(now) == null) {
                    selected = null
                    identities = FreshFrameConsensus()
                    overlay.status("识别已过期，正在重新确认…")
                }
                sleep(100)
                continue
            }
            frame.use {
                val observation = recognizer.inspect(it.bitmap, library.songs())
                val candidate = observation.identity?.takeUnless { observation.resultScreen }
                    ?.takeIf { item -> library.hasChart(item.songId, item.difficulty) }
                val pendingSelection = selected
                val invalid = observation.resultScreen || (pendingSelection != null &&
                    (it.screenWidth != pendingSelection.width || it.screenHeight != pendingSelection.height))
                confirmation.observe(observation.identity?.let(::key), it.uptimeMs, invalid)
                if (selected != null && confirmation.pending(SystemClock.uptimeMillis()) == null) {
                    selected = null
                    identities = FreshFrameConsensus()
                    overlay.status("画面或曲目已变化，正在重新识别…")
                }
                if (selected == null && SystemClock.uptimeMillis() >= ignoredUntil &&
                    identities.observe(candidate?.let(::key), it.uptimeMs) && candidate != null &&
                    SystemClock.uptimeMillis() - it.uptimeMs < 900) {
                    selected = Selection(candidate, it.screenWidth, it.screenHeight)
                    val offer = confirmation.propose(key(candidate), it.uptimeMs)
                    overlay.offer(offer.token, candidate.title, candidate.difficulty.toString(), preview)
                    RuntimeState.log("悬浮窗识别：" + candidate.title + " " + candidate.difficulty +
                        if (preview) "（只识别）" else "；等待确认")
                }
                diagnostic(recognizer.lastDiagnostic)
            }
            sleep(160)
        }
        return null
    }

    /** Hide first, reload fresh identity and gameplay evidence, then pause only the confirmed song. */
    private fun preparePaused(recognizer: SongIdentifier, selected: Selection, chart: Chart,
                              afterHide: Long): Prepared? {
        val detector = GameplayDetector(settings.pauseRoi)
        val identityGate = FreshFrameConsensus(afterFrameMs = maxOf(afterHide, SystemClock.uptimeMillis()))
        val pausedAlready = FreshFrameConsensus(afterFrameMs = afterHide)
        val deadline = SystemClock.uptimeMillis() + 20000
        var pausedAt = 0L
        RuntimeState.log("悬浮窗已隐藏，重新核对当前曲目及暂停图标")
        while (!stop.get() && SystemClock.uptimeMillis() < deadline && pausedAt == 0L) {
            requireForeground()
            val frame = freshFrame()
            if (frame == null) { sleep(70); continue }
            frame.use {
                checkDimensions(it, selected.width, selected.height)
                if (it.uptimeMs <= afterHide) return@use
                val observation = recognizer.inspect(it.bitmap, library.songs())
                check(!observation.resultScreen) { "已到结算画面，未开始演奏" }
                val identity = observation.identity
                check(identity == null || key(identity) == key(selected.identity)) {
                    "曲目或难度已变化，确认已取消；请重新启动识别"
                }
                val same = identity != null && key(identity) == key(selected.identity)
                val identityReady = identityGate.observe(if (same) key(selected.identity) else null, it.uptimeMs)
                val paused = pausedAlready.observe(if (observation.pauseMenu != null) "pause" else null, it.uptimeMs)
                val hit = if (!observation.pauseOrResult) detector.observe(it.bitmap, it.uptimeMs)
                    else { detector.reset(); null }
                if (!identityReady || SystemClock.uptimeMillis() - it.uptimeMs >= 900) return@use
                if (paused) {
                    pausedAt = it.uptimeMs
                    RuntimeState.log("已在暂停菜单，准备恢复后重新校准")
                } else if (hit != null) {
                    RuntimeState.log("当前曲目已复核，双击暂停键")
                    control(hit.pauseX, hit.pauseY, 2, selected.width, selected.height)
                    pausedAt = SystemClock.uptimeMillis()
                }
                diagnostic("开始前复核：" + recognizer.lastDiagnostic)
            }
        }
        if (stop.get()) return null
        check(pausedAt > 0L) { "未能复核当前曲目与暂停状态，未开始演奏；可手动暂停后重新启动识别" }
        val menuGate = FreshFrameConsensus(afterFrameMs = pausedAt)
        val resumeDeadline = SystemClock.uptimeMillis() + 20000
        var resumedAt = 0L
        while (!stop.get() && resumedAt == 0L && SystemClock.uptimeMillis() < resumeDeadline) {
            requireForeground()
            val frame = freshFrame()
            if (frame == null) { sleep(70); continue }
            frame.use {
                checkDimensions(it, selected.width, selected.height)
                val observation = recognizer.inspect(it.bitmap, library.songs())
                val identity = observation.identity
                check(!observation.resultScreen &&
                    (identity == null || key(identity) == key(selected.identity))) {
                    "暂停后曲目信息发生变化，未恢复播放"
                }
                val menu = observation.pauseMenu
                val same = identity != null && key(identity) == key(selected.identity)
                if (menuGate.observe(if (same && menu != null) "ready" else null, it.uptimeMs) &&
                    menu != null && SystemClock.uptimeMillis() - it.uptimeMs < 900) {
                    control(menu.resume.x, menu.resume.y, 1, selected.width, selected.height)
                    resumedAt = SystemClock.uptimeMillis()
                    RuntimeState.log("已点击继续，等待暂停菜单消失及画面恢复")
                }
                diagnostic("暂停校准：" + recognizer.lastDiagnostic)
            }
        }
        if (stop.get()) return null
        check(resumedAt > 0L) { "无法确认当前曲目与继续按钮，已保持暂停；请检查识别区域" }
        val running = FreshFrameConsensus(afterFrameMs = resumedAt)
        val runningDeadline = SystemClock.uptimeMillis() + 15000
        while (!stop.get() && SystemClock.uptimeMillis() < runningDeadline) {
            requireForeground()
            val frame = freshFrame()
            if (frame == null) { sleep(70); continue }
            var accepted = false
            var timestamp = 0L
            frame.use {
                checkDimensions(it, selected.width, selected.height)
                val observation = recognizer.inspect(it.bitmap, library.songs())
                accepted = running.observe(if (!observation.pauseOrResult) "running" else null, it.uptimeMs)
                timestamp = it.uptimeMs
            }
            if (accepted) {
                RuntimeState.log("恢复已确认，丢弃原时钟，从移动音符重新校准")
                return Prepared(chart, selected.identity, timestamp, selected.width, selected.height)
            }
        }
        if (stop.get()) return null
        error("恢复后菜单未消失，已停止；不会重复点击继续按钮")
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

    /** A manual pause permits a fresh confirmation; no automatic replay during moving gameplay. */
    private fun awaitNextOpportunity(recognizer: SongIdentifier) {
        RuntimeState.log("本次演奏结束，等待暂停菜单、结算或离开游戏；再次演奏需要重新确认")
        val exit = FreshFrameConsensus()
        while (!stop.get()) {
            if (TouchService.current?.gameForeground != true) return
            val frame = freshFrame()
            if (frame == null) { sleep(120); continue }
            var finished = false
            frame.use {
                val observation = recognizer.inspect(it.bitmap, emptyList())
                val state = when {
                    observation.resultScreen -> "result"
                    observation.pauseMenu != null -> "pause"
                    else -> null
                }
                finished = exit.observe(state, it.uptimeMs)
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
