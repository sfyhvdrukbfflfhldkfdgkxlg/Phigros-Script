package io.github.phiscript.input

import android.content.Context
import android.os.SystemClock
import io.github.phiscript.Diagnostics
import io.github.phiscript.RuntimeState
import io.github.phiscript.assets.ApkAccess
import java.util.concurrent.atomic.AtomicBoolean

/** Single user request; the following observation performs no writes or recovery attempts. */
object AccessibilityActivation {
    private val busy = AtomicBoolean(false)
    val isRunning: Boolean get() = busy.get()
    @Volatile var status: String = "等待手动启用"
        private set

    fun start(context: Context): Boolean {
        if (RuntimeState.running.get() || Diagnostics.isCapturing) return false
        if (!busy.compareAndSet(false, true)) return false
        val app = context.applicationContext
        fun record(message: String) {
            status = message
            RuntimeState.log(message)
            Diagnostics.record(app, message)
        }
        val task = Thread({
            try {
                record("用户主动通过 Shizuku 启用本服务（单次请求）")
                val result = ApkAccess.enableSelfAccessibility()
                record(result)
                status = "启用请求已返回 · 正在核对连接（10 秒）"
                val until = SystemClock.elapsedRealtime() + 10_000L
                var previous: AccessibilityStatus.Snapshot? = null
                var readySince: Long? = null
                var last = AccessibilityStatus.read(app)
                do {
                    last = AccessibilityStatus.read(app)
                    val now = SystemClock.elapsedRealtime()
                    if (last != previous) {
                        Diagnostics.record(app, "手动启用后的系统状态 " + last)
                        previous = last
                    }
                    readySince = if (last.ready) readySince ?: now else null
                    if (now >= until) break
                    Thread.sleep(minOf(400L, until - now))
                } while (true)
                val held = readySince?.let { SystemClock.elapsedRealtime() - it } ?: 0L
                when {
                    last.ready && held >= 1000L ->
                        record("10 秒核对结束：无障碍服务已连接，可以启动演奏。")
                    last.ready ->
                        record("服务刚刚连接，请在主页继续观察状态后再启动。")
                    last.serviceEnabled == false || last.systemEnabled == false ->
                        record("系统未保留本服务的启用状态；已停止，不会自动重试。请复制诊断。")
                    else ->
                        record("启用请求已结束，但服务尚未连接：" + last.summary + "；请复制诊断。")
                }
            } catch (e: Exception) {
                record("手动启用未完成：" + (e.message ?: e.javaClass.simpleName) +
                    "。请求可能已部分生效，请查看当前状态；不会自动重试。")
                Diagnostics.record(app, "手动启用异常", e)
            } finally { busy.set(false) }
        }, "PhigrosAccessibilityEnable").apply { isDaemon = true }
        try { task.start() }
        catch (e: RuntimeException) {
            busy.set(false)
            record("无法开始启用请求：" + e.javaClass.simpleName)
            return false
        }
        return true
    }
}
