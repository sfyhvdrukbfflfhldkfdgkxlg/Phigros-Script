package io.github.phiscript.capture

import android.os.SystemClock
import io.github.phiscript.RuntimeState
import io.github.phiscript.SessionSettings
import io.github.phiscript.assets.ChartLibrary
import io.github.phiscript.engine.Viewport
import io.github.phiscript.input.TouchService
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
    fun run() {
        val recognizer = SongIdentifier(OcrConfig(settings.titleRoi, settings.difficultyRoi))
        var lastIdentity = ""
        var consistent = 0
        var lastFrameTime = -1L
        RuntimeState.log(if (preview) "只识别模式：请进入歌曲" else "自动演奏待命：请进入歌曲")
        try {
            while (!stop.get()) {
                if (!preview && TouchService.current?.gameForeground != true) {
                    consistent = 0
                    lastIdentity = ""
                    sleep(250)
                    continue
                }
                val frame = frames.snapshot()
                if (frame == null) { sleep(100); continue }
                val identity = frame.use {
                    if (it.bitmap.width <= it.bitmap.height || it.uptimeMs == lastFrameTime) null
                    else {
                        lastFrameTime = it.uptimeMs
                        recognizer.identify(it.bitmap, library.songs())
                    }
                }
                if (stop.get()) break
                if (identity == null) {
                    consistent = 0
                    lastIdentity = ""
                    sleep(400)
                    continue
                }
                val key = identity.songId + "/" + identity.difficulty
                if (key == lastIdentity) consistent++ else { lastIdentity = key; consistent = 1 }
                if (consistent < 2) { sleep(300); continue }
                if (!library.hasChart(identity.songId, identity.difficulty)) {
                    RuntimeState.log("本机谱库没有此难度：" + key)
                    consistent = 0
                    sleep(700)
                    continue
                }
                RuntimeState.log("识别：" + identity.title + " " + identity.difficulty)
                val chart = library.load(identity.songId, identity.difficulty)
                if (stop.get()) break
                RuntimeState.log("谱面已读取，正在根据音符对齐…")
                val synchronizer = VisualSynchronizer(chart, settings.viewport)
                val deadline = SystemClock.uptimeMillis() + 35000
                var epoch: Long? = null
                var width = 0
                var height = 0
                var lastSyncFrame = -1L
                var nextDiagnostic = SystemClock.uptimeMillis() + 4000
                while (!stop.get() && SystemClock.uptimeMillis() < deadline) {
                    if (!preview && TouchService.current?.gameForeground != true) break
                    val image = frames.snapshot()
                    if (image == null) { sleep(80); continue }
                    image.use {
                        if (it.uptimeMs != lastSyncFrame && it.bitmap.width > it.bitmap.height) {
                            lastSyncFrame = it.uptimeMs
                            epoch = synchronizer.observe(it.bitmap,
                                it.uptimeMs - settings.captureLagMs)
                            width = it.screenWidth
                            height = it.screenHeight
                        }
                    }
                    if (epoch != null) break
                    if (SystemClock.uptimeMillis() >= nextDiagnostic) {
                        RuntimeState.log("对齐中：" + synchronizer.lastDiagnostic)
                        nextDiagnostic = SystemClock.uptimeMillis() + 4000
                    }
                    sleep(60)
                }
                if (stop.get()) break
                consistent = 0
                lastIdentity = ""
                if (epoch == null) {
                    RuntimeState.log("未能对齐；可重开歌曲或校准识别区域/视口")
                    sleep(700)
                    continue
                }
                RuntimeState.log("已对齐。此前经过的音符不会补打")
                if (preview) {
                    RuntimeState.log("识别测试完成，未发送任何触摸")
                    return
                }

                val service = TouchService.current ?: error("无障碍服务已断开")
                val touchStop = AtomicBoolean(false)
                val activeTouch = RuntimeState.ActiveTouch(stop, touchStop)
                RuntimeState.activeTouchStop.set(activeTouch)
                if (stop.get()) touchStop.set(true)
                val failure = AtomicReference<Throwable?>(null)
                val rect = settings.viewport
                val viewport = Viewport(rect.left * width, rect.top * height,
                    rect.width() * width, rect.height() * height)
                val targetEpoch = epoch!! + settings.touchOffsetMs
                val player = Thread({
                    try { service.play(chart, targetEpoch, viewport, touchStop, RuntimeState::log) }
                    catch (e: Throwable) { failure.set(e) }
                }, "phi-touch").apply { start() }
                try {
                    while (player.isAlive && !stop.get()) {
                        if (!service.gameForeground) {
                            touchStop.set(true)
                            break
                        }
                        frames.snapshot()?.use {
                            if (it.screenWidth != width || it.screenHeight != height ||
                                recognizer.detectPaused(it.bitmap)) {
                                RuntimeState.log("检测到暂停、结算或画面尺寸变化，停止本次演奏")
                                touchStop.set(true)
                            }
                        }
                        if (touchStop.get()) break
                        sleep(450)
                    }
                } finally {
                    touchStop.set(true)
                    player.join(3000)
                    RuntimeState.activeTouchStop.compareAndSet(activeTouch, null)
                    if (player.isAlive) {
                        stop.set(true)
                        RuntimeState.log("触控线程未及时退出，结束会话")
                    }
                }
                failure.get()?.let { RuntimeState.log("演奏停止：" + it.message) }
                sleep(700)
            }
        } finally {
            recognizer.close()
        }
    }

    private fun sleep(ms: Long) {
        if (!stop.get()) Thread.sleep(ms)
    }
}
