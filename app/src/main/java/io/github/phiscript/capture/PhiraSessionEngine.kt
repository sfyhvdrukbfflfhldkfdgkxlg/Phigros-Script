package io.github.phiscript.capture

import android.graphics.RectF
import android.os.SystemClock
import io.github.phiscript.RuntimeState
import io.github.phiscript.SessionSettings
import io.github.phiscript.assets.PhiraLibrary
import io.github.phiscript.engine.Chart
import io.github.phiscript.engine.Viewport
import io.github.phiscript.input.TouchService
import io.github.phiscript.vision.GameplayDetector
import io.github.phiscript.vision.OcrConfig
import io.github.phiscript.vision.OcrLabels
import io.github.phiscript.vision.PhiraCandidate
import io.github.phiscript.vision.PhiraMatcher
import io.github.phiscript.vision.PhiraSelectionPolicy
import io.github.phiscript.vision.PhiraPauseDetector
import io.github.phiscript.vision.SongIdentifier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Uses a confirmed retry, never moving-note alignment or a guessed resume position. */
internal class PhiraSessionEngine(private val frames: FrameStore, private val library: PhiraLibrary,
    private val settings: SessionSettings, private val preview: Boolean,
    private val stop: AtomicBoolean, private val overlay: RecognitionOverlay) {
    private val candidates = library.entries.map { PhiraCandidate(it.id, it.title, it.level, it.aliases) }
    private val selectionPolicy = PhiraSelectionPolicy(settings.phiraChartId, candidates)
    private var manualGameDetector: GameplayDetector? = null
    private var manualPauseMenu: PhiraPauseDetector? = null
    private var manualMenuEvidence = FreshFrameConsensus(requiredFrames = 3, minimumSpanMs = 240)
    private var manualDimensions: Pair<Int, Int>? = null
    private lateinit var pauseMenu: PhiraPauseDetector
    private var lastFrame = -1L
    private var nextDiagnostic = 0L
    private data class Selection(val candidate: PhiraCandidate, val width: Int, val height: Int)
    private data class Prepared(val chart: Chart, val selection: Selection, val epoch: Long, val viewport: Viewport)

    fun run() {
        require(candidates.isNotEmpty()) { "请先导入 Phira ZIP/PEZ 谱面包" }
        val recognizer = SongIdentifier(OcrConfig(settings.titleRoi, settings.difficultyRoi))
        RuntimeState.log(if (preview) "Phira 只识别：匹配曲名和自定义难度，不会点击"
            else "Phira 谱面模式：确认后暂停并点中央重试。请使用正常模式、1x，关闭镜像和翻转")
        try {
            while (!stop.get()) {
                val selection = recognizeAndConfirm(recognizer) ?: return
                val afterHide = overlay.hide()
                if (stop.get()) return
                val chart = library.load(selection.candidate.id, selection.candidate.level)
                PhiraRestartClock.epoch(0L, settings.phiraRestartDelayMs, chart.offsetSeconds,
                    settings.phiraGlobalOffsetMs, settings.touchOffsetMs)
                val prepared = prepareRetry(recognizer, selection, chart, afterHide) ?: return
                RuntimeState.log("从重试时钟演奏，不进行音符对齐；设备延迟须在设置中调整")
                play(prepared, recognizer)
                if (!stop.get()) awaitNextOpportunity(recognizer)
            }
        } finally { runCatching { overlay.hide() }; recognizer.close() }
    }

    private fun recognizeAndConfirm(recognizer: SongIdentifier): Selection? {
        var consensus = FreshFrameConsensus()
        val confirmation = RecognitionConfirmation(if (selectionPolicy.manual == null) 4000 else 600)
        var selection: Selection? = null
        var ignoredUntil = 0L
        var foreground = true
        overlay.status(selectionPolicy.manual?.let {
            "Phira 手选：" + it.title + " · " + it.level + "\n请打开相同谱面，等待游戏画面或暂停菜单…"
        } ?: "Phira：等待曲名和自定义难度…\n请导入与游戏一致的谱面")
        while (!stop.get()) {
            if (!preview && !isForeground()) {
                if (foreground) {
                    confirmation.invalidate(); selection = null
                    consensus = FreshFrameConsensus()
                    manualGameDetector?.reset()
                    manualMenuEvidence = FreshFrameConsensus(requiredFrames = 3, minimumSpanMs = 240)
                    overlay.hide()
                }
                foreground = false; sleep(150); continue
            }
            if (!foreground) overlay.status(if (selectionPolicy.manual != null)
                "Phira：等待游戏画面，核对手选谱面…" else "Phira：重新识别曲名和难度…")
            foreground = true
            val now = SystemClock.uptimeMillis()
            val choice = overlay.consumeChoice(); val selected = selection
            if (choice != null && selected != null && confirmation.pending(now)?.token == choice.token) {
                if (choice.start && !preview && confirmation.accept(choice.token, now)) {
                    RuntimeState.log("已确认 Phira：" + selected.candidate.title + " · " + selected.candidate.level)
                    return selected
                }
                confirmation.invalidate(); selection = null; consensus = FreshFrameConsensus()
                ignoredUntil = now + 3000; overlay.status("Phira：继续识别，尚未开始演奏")
            }
            val frame = freshFrame()
            if (frame == null) {
                if (selection != null && confirmation.pending(now) == null) {
                    selection = null; consensus = FreshFrameConsensus()
                    overlay.status("识别已过期，重新核对曲名和难度…")
                }; sleep(90); continue
            }
            frame.use {
                val labels = recognizer.readLabels(it.bitmap)
                val candidate = selectCandidate(labels, it)
                val old = selection
                val invalid = labels.resultScreen ||
                    (selectionPolicy.manual != null && candidate == null) || (old != null &&
                    (it.screenWidth != old.width || it.screenHeight != old.height))
                confirmation.observe(candidate?.let(::key), it.uptimeMs, invalid)
                if (selection != null && confirmation.pending(SystemClock.uptimeMillis()) == null) {
                    selection = null; consensus = FreshFrameConsensus()
                    overlay.status("画面或曲目已变化，重新识别…")
                }
                if (selection == null && SystemClock.uptimeMillis() >= ignoredUntil &&
                    consensus.observe(candidate?.let(::key), it.uptimeMs) && candidate != null &&
                    SystemClock.uptimeMillis() - it.uptimeMs < 900) {
                    selection = Selection(candidate, it.screenWidth, it.screenHeight)
                    val offer = confirmation.propose(key(candidate), it.uptimeMs)
                    overlay.offer(offer.token, candidate.title, candidate.level +
                        if (selectionPolicy.manual != null) "（手选，请核对）" else "", preview)
                    RuntimeState.log((if (selectionPolicy.manual != null) "Phira 手选：" else "Phira 已识别：") +
                        candidate.title + " · " + candidate.level +
                        if (preview) "（只识别）" else "；请确认与当前游戏谱面一致")
                }
                diagnostic(if (candidate == null) "Phira 尚未唯一匹配；文字：" +
                    (labels.titleTexts + labels.difficultyTexts).distinct().take(4).joinToString(" / ").take(140)
                    else "Phira 匹配：" + candidate.title + " · " + candidate.level)
            }; sleep(120)
        }; return null
    }

    private fun prepareRetry(recognizer: SongIdentifier, selection: Selection,
                             chart: Chart, afterHide: Long): Prepared? {
        val viewport = viewportFor(selection)
        pauseMenu = pauseMenuFor(selection)
        val currentIdentity = FreshFrameConsensus(afterFrameMs = afterHide)
        val alreadyPaused = FreshFrameConsensus(requiredFrames = 3, minimumSpanMs = 240, afterFrameMs = afterHide)
        val gameplay = GameplayDetector(pauseRoiFor(selection))
        val deadline = SystemClock.uptimeMillis() + 20_000
        var pausedAt = 0L
        RuntimeState.log("悬浮窗已隐藏，复核 Phira 曲目与暂停控件")
        while (!stop.get() && SystemClock.uptimeMillis() < deadline && pausedAt == 0L) {
            requireForeground()
            val frame = freshFrame(); if (frame == null) { sleep(60); continue }
            frame.use {
                checkDimensions(it, selection); if (it.uptimeMs <= afterHide) return@use
                val labels = recognizer.readLabels(it.bitmap); verifyIdentity(labels, selection)
                val same = match(labels)?.let(::key) == key(selection.candidate)
                val identityReady = currentIdentity.observe(if (same) key(selection.candidate) else null, it.uptimeMs)
                val paused = pauseMenu.visible(it.bitmap)
                val menuReady = alreadyPaused.observe(if (paused) "phira-pause" else null, it.uptimeMs)
                val hit = if (!paused) gameplay.observe(it.bitmap, it.uptimeMs) else { gameplay.reset(); null }
                if (SystemClock.uptimeMillis() - it.uptimeMs >= 900) return@use
                if (menuReady) { pausedAt = it.uptimeMs; RuntimeState.log("已确认 Phira 三按钮暂停菜单") }
                else if (hit != null && (identityReady || selectionPolicy.manual != null)) {
                    RuntimeState.log(if (selectionPolicy.manual != null)
                        "Phira 游戏控件已复核，按已确认手选谱面双击暂停键"
                        else "Phira 曲目已复核，双击暂停键")
                    control(hit.pauseX, hit.pauseY, 2, selection); pausedAt = SystemClock.uptimeMillis()
                }
            }; sleep(60)
        }
        if (stop.get()) return null
        check(pausedAt > 0L) { "未确认 Phira 曲目或暂停菜单；可手动暂停后重新启动识别" }
        val settledMenu = FreshFrameConsensus(requiredFrames = 3, minimumSpanMs = 240,
            afterFrameMs = pausedAt + settings.pauseSettleMs)
        val retryDeadline = pausedAt + 12_000
        var retryTouchDown = -1L
        while (!stop.get() && SystemClock.uptimeMillis() <= retryDeadline && retryTouchDown < 0) {
            requireForeground()
            val frame = freshFrame(); if (frame == null) { sleep(60); continue }
            frame.use {
                checkDimensions(it, selection)
                verifyIdentity(recognizer.readLabels(it.bitmap), selection)
                if (SystemClock.uptimeMillis() - it.uptimeMs >= 900) return@use
                if (settledMenu.observe(if (pauseMenu.visible(it.bitmap)) "phira-pause" else null, it.uptimeMs)) {
                    retryTouchDown = control(settings.phiraRestartPoint.x, settings.phiraRestartPoint.y, 1, selection)
                    RuntimeState.log("已点击 Phira 中央重试一次；继续按钮会倒回 3 秒，不作为谱面起点")
                } else diagnostic("等待 Phira 暂停菜单；请检查玩法视口和重试坐标")
            }; sleep(60)
        }
        if (stop.get()) return null
        check(retryTouchDown >= 0) { "未确认三按钮暂停菜单，未点击重试；请检查玩法视口和重试坐标" }
        val epoch = PhiraRestartClock.epoch(retryTouchDown, settings.phiraRestartDelayMs,
            chart.offsetSeconds, settings.phiraGlobalOffsetMs, settings.touchOffsetMs)
        val resumed = GameplayDetector(pauseRoiFor(selection))
        val runningDeadline = retryTouchDown + maxOf(8000L, settings.phiraRestartDelayMs + 5000L)
        var nextRead = 0L
        while (!stop.get() && SystemClock.uptimeMillis() < runningDeadline) {
            requireForeground()
            val frame = freshFrame(); if (frame == null) { sleep(50); continue }
            var ready = false
            frame.use {
                checkDimensions(it, selection); if (it.uptimeMs <= retryTouchDown) return@use
                if (SystemClock.uptimeMillis() >= nextRead) {
                    verifyIdentity(recognizer.readLabels(it.bitmap), selection)
                    nextRead = SystemClock.uptimeMillis() + 600
                }
                if (pauseMenu.visible(it.bitmap)) resumed.reset()
                else ready = resumed.observe(it.bitmap, it.uptimeMs) != null
                if (SystemClock.uptimeMillis() - it.uptimeMs >= 900) ready = false
            }
            if (ready) {
                RuntimeState.log("已确认重试返回游戏；起始延迟 " + settings.phiraRestartDelayMs +
                    " ms，全局偏移 " + settings.phiraGlobalOffsetMs + " ms；错过的开头音符会跳过")
                return Prepared(chart, selection, epoch, viewport)
            }; sleep(50)
        }
        if (stop.get()) return null
        error("点击重试后未确认回到游戏，已停止；不会重复点击")
    }

    private fun play(prepared: Prepared, recognizer: SongIdentifier) {
        val selection = prepared.selection
        val service = TouchService.current ?: error("无障碍服务已断开")
        val touchStop = AtomicBoolean(stop.get()); val owner = RuntimeState.ActiveTouch(stop, touchStop)
        RuntimeState.activeTouchStop.set(owner)
        if (stop.get()) touchStop.set(true)
        val failure = AtomicReference<Throwable?>(null)
        val player = Thread({
            try { service.play(prepared.chart, prepared.epoch, prepared.viewport, touchStop, RuntimeState::log) }
            catch (e: Throwable) { failure.set(e) }
        }, "phira-touch").apply { start() }
        var lastEvidence = SystemClock.uptimeMillis(); var nextRead = 0L
        try {
            while (player.isAlive && !stop.get() && !touchStop.get()) {
                if (!isForeground()) { touchStop.set(true); break }
                freshFrame()?.use {
                    lastEvidence = it.uptimeMs
                    if (it.screenWidth != selection.width || it.screenHeight != selection.height ||
                        pauseMenu.visible(it.bitmap)) {
                        RuntimeState.log("检测到 Phira 暂停或尺寸变化，停止演奏"); touchStop.set(true)
                    } else if (SystemClock.uptimeMillis() >= nextRead) {
                        val labels = recognizer.readLabels(it.bitmap); val candidate = match(labels)
                        if (labels.resultScreen || labels.pauseOrResult ||
                            (candidate != null && key(candidate) != key(selection.candidate))) {
                            RuntimeState.log("检测到菜单、结算或曲目变化，停止本次演奏"); touchStop.set(true)
                        }; nextRead = SystemClock.uptimeMillis() + 700
                    }
                }
                if (SystemClock.uptimeMillis() - lastEvidence > 1500) {
                    RuntimeState.log("截图超过 1.5 秒未更新，停止 Phira 演奏"); touchStop.set(true)
                }; sleep(100)
            }
        } finally {
            touchStop.set(true); player.join(3000); RuntimeState.activeTouchStop.compareAndSet(owner, null)
            if (player.isAlive) { stop.set(true); RuntimeState.log("触控线程未及时退出，结束会话") }
        }
        failure.get()?.let { RuntimeState.log("Phira 演奏停止：" + it.message) }
    }
    private fun awaitNextOpportunity(recognizer: SongIdentifier) {
        RuntimeState.log("Phira 本次演奏结束；等待暂停、结算或离开游戏，下次须重新确认")
        val exit = FreshFrameConsensus()
        while (!stop.get()) {
            if (!isForeground()) return
            val frame = freshFrame(); if (frame == null) { sleep(120); continue }
            var done = false
            frame.use {
                val labels = recognizer.readLabels(it.bitmap)
                val state = when {
                    labels.resultScreen -> "result"; pauseMenu.visible(it.bitmap) -> "phira-pause"; else -> null
                }
                done = exit.observe(state, it.uptimeMs)
            }; if (done) return
            sleep(250)
        }
    }
    private fun viewportFor(selection: Selection): Viewport {
        if (settings.phiraAutomaticViewport) {
            val entry = library.entries.single { it.id == selection.candidate.id }
            return PhiraViewport.bounds(selection.width, selection.height, entry.aspectRatio, entry.forceAspectRatio)
        }
        val rect = settings.viewport
        return Viewport(rect.left * selection.width, rect.top * selection.height,
            rect.width() * selection.width, rect.height() * selection.height)
    }
    private fun selectCandidate(labels: OcrLabels, frame: CapturedFrame): PhiraCandidate? {
        val automatic = match(labels).takeUnless { labels.resultScreen }
        val manual = selectionPolicy.manual ?: return automatic
        if (labels.resultScreen) return null
        val dimensions = frame.screenWidth to frame.screenHeight
        if (manualDimensions != dimensions) {
            val selection = Selection(manual, frame.screenWidth, frame.screenHeight)
            manualPauseMenu = pauseMenuFor(selection)
            manualGameDetector = GameplayDetector(pauseRoiFor(selection))
            manualMenuEvidence = FreshFrameConsensus(requiredFrames = 3, minimumSpanMs = 240)
            manualDimensions = dimensions
        }
        val paused = manualPauseMenu?.visible(frame.bitmap) == true
        val menuReady = manualMenuEvidence.observe(if (paused) "phira-pause" else null, frame.uptimeMs)
        val gameplayReady = if (paused) { manualGameDetector?.reset(); false }
            else manualGameDetector?.observe(frame.bitmap, frame.uptimeMs) != null
        return selectionPolicy.choose(automatic, menuReady || gameplayReady)
    }
    private fun pauseRoiFor(selection: Selection): RectF {
        val roi = settings.phiraPauseRoi
        if (!settings.phiraAutomaticViewport || roi.left != 0f || roi.top != 0f ||
            roi.right != 0.18f || roi.bottom != 0.24f) return RectF(roi)
        val viewport = viewportFor(selection)
        return RectF(viewport.left / selection.width, viewport.top / selection.height,
            (viewport.left + viewport.width * 0.18f) / selection.width,
            (viewport.top + viewport.height * 0.24f) / selection.height)
    }
    private fun pauseMenuFor(selection: Selection): PhiraPauseDetector {
        val viewport = viewportFor(selection)
        return PhiraPauseDetector(RectF(viewport.left / selection.width, viewport.top / selection.height,
            viewport.right / selection.width, viewport.bottom / selection.height), settings.phiraRestartPoint)
    }
    private fun match(labels: OcrLabels) = PhiraMatcher.match(labels.titleTexts, labels.difficultyTexts, candidates)
    private fun verifyIdentity(labels: OcrLabels, selection: Selection) {
        check(!labels.resultScreen) { "已到结算画面，未开始 Phira 演奏" }
        val candidate = match(labels)
        check(candidate == null || key(candidate) == key(selection.candidate)) {
            "Phira 曲目或难度变化，确认已取消；请重新启动识别"
        }
    }
    private fun control(x: Float, y: Float, count: Int, selection: Selection): Long {
        requireForeground()
        return (TouchService.current ?: error("无障碍服务已断开"))
            .tapNormalized(x, y, count, settings.doubleTapIntervalMs, selection.width, selection.height, stop)
    }
    private fun freshFrame(): CapturedFrame? {
        val frame = frames.snapshot() ?: return null; val now = SystemClock.uptimeMillis()
        if (frame.uptimeMs <= lastFrame || frame.uptimeMs > now || now - frame.uptimeMs > 900 ||
            frame.bitmap.width <= frame.bitmap.height) { frame.close(); return null }
        lastFrame = frame.uptimeMs; return frame
    }
    private fun isForeground() = TouchService.current?.foregroundPackage == TouchService.PHIRA_PACKAGE
    private fun requireForeground() { check(!stop.get() && isForeground()) { "会话已停止或 Phira 已离开前台" } }
    private fun checkDimensions(frame: CapturedFrame, selection: Selection) {
        check(frame.screenWidth == selection.width && frame.screenHeight == selection.height) { "屏幕尺寸变化，请重新开始" }
    }
    private fun key(candidate: PhiraCandidate) = candidate.id + "/" + candidate.level
    private fun diagnostic(message: String) {
        val now = SystemClock.uptimeMillis()
        if (now >= nextDiagnostic) { RuntimeState.log(message); nextDiagnostic = now + 4000 }
    }
    private fun sleep(ms: Long) { if (!stop.get()) Thread.sleep(ms) }
}
