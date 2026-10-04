package io.github.phiscript

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import io.github.phiscript.assets.ApkAccess
import io.github.phiscript.input.AccessibilityStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class PhigrosApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Diagnostics.install(this)
    }
    override fun onCreate() {
        super.onCreate()
        Diagnostics.record(this, "应用初始化完成")
        Diagnostics.observeAccessibility(this)
    }
}
object Diagnostics {
    private const val STORE = "diagnostics"
    private var installed = false
    private var observing = false
    private val capturing = AtomicBoolean(false)
    val isCapturing: Boolean get() = capturing.get()
    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date())

    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                app.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                    .putString("last_crash", now() + " thread=" + thread.name + "\n" +
                        error.stackTraceToString().take(24000)).commit()
            }
            try { previous?.uncaughtException(thread, error) }
            finally { Process.killProcess(Process.myPid()); exitProcess(10) }
        }
        record(app, "应用进程启动 pid=" + Process.myPid() + " uid=" + Process.myUid())
    }

    @Synchronized fun record(context: Context, event: String, error: Throwable? = null) {
        runCatching {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val line = now() + "  " + event + (error?.let { "\n" + it.stackTraceToString().take(6000) } ?: "")
            prefs.edit().putString("events", ((prefs.getString("events", "") ?: "") + "\n" + line).takeLast(16000)).apply()
        }
    }

    /** Observe changes, not their author. Never persist the other enabled component names. */
    @Synchronized fun observeAccessibility(context: Context) {
        if (observing) return
        val app = context.applicationContext ?: context
        runCatching {
            var last = settingsSummary(app)
            record(app, "无障碍设置初始快照 " + last)
            val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    val next = settingsSummary(app)
                    if (last != next) {
                        record(app, "无障碍设置发生变化（不代表已知修改方） " + next)
                        last = next
                    }
                }
            }
            val resolver = app.contentResolver
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, watcher)
            try {
                resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.ACCESSIBILITY_ENABLED), false, watcher)
            } catch (e: RuntimeException) {
                resolver.unregisterContentObserver(watcher)
                throw e
            }
            observing = true
        }.onFailure { record(app, "无法观察无障碍设置", it) }
    }

    private fun settingsSummary(context: Context): String {
        val count = runCatching {
            (Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .split(':').count { it.isNotBlank() }.toString()
        }.getOrElse { "unknown(" + it.javaClass.simpleName + ")" }
        return AccessibilityStatus.read(context).toString() + " enabledComponentCount=" + count
    }

    /** The UI explains the scope before this opt-in capture; results remain in private app storage. */
    fun startSystemCapture(context: Context): Boolean {
        if (!capturing.compareAndSet(false, true)) return false
        val app = context.applicationContext ?: context
        val prefs = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("system_capture_pending", true)
            .putString("system_capture", "系统诊断采集中，开始于 " + now()).apply()
        record(app, "用户开始系统诊断采集 " + settingsSummary(app))
        RuntimeState.log("系统诊断采集中：请重新开启无障碍，退出设置后返回；约 30 秒后完成。")
        val task = Thread({
            try {
                val result = ApkAccess.collectAccessibilityDiagnostics()
                prefs.edit().putString("system_capture", "采集完成 " + now() + "\n" + result)
                    .putBoolean("system_capture_pending", false).apply()
                record(app, "系统诊断采集结束 " + settingsSummary(app))
                RuntimeState.log("系统诊断采集结束，请点“无障碍自查 / 复制诊断”查看结果。")
            } catch (e: Exception) {
                prefs.edit().putString("system_capture", "采集失败 " + now() + "\n" +
                    e.javaClass.simpleName + ": " + e.message)
                    .putBoolean("system_capture_pending", false).apply()
                record(app, "系统诊断采集失败", e)
                RuntimeState.log("系统诊断采集失败，请检查 Shizuku 连接并查看诊断。")
            } finally { capturing.set(false) }
        }, "PhigrosSystemDiagnostic").apply { isDaemon = true }
        try { task.start() }
        catch (e: RuntimeException) {
            capturing.set(false)
            prefs.edit().putBoolean("system_capture_pending", false)
                .putString("system_capture", "无法开始采集：" + e.javaClass.simpleName).apply()
            record(app, "无法开始系统诊断", e)
            return false
        }
        return true
    }

    fun report(context: Context): String {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        return buildString {
            appendLine("Phigros Script " + BuildConfig.VERSION_NAME + " 诊断 " + now())
            appendLine("Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT)
            appendLine("设备 " + Build.MANUFACTURER + " " + Build.MODEL)
            appendLine("系统版本 " + Build.DISPLAY)
            appendLine("应用 uid=" + Process.myUid())
            appendLine("无障碍：" + settingsSummary(context))
            appendLine("安装来源：" + runCatching {
                if (Build.VERSION.SDK_INT >= 30)
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName ?: "未提供"
                else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName) ?: "未提供"
                }
            }.getOrElse { "读取失败 " + it.javaClass.simpleName })
            appendLine("最近崩溃（无记录不能排除系统结束进程）：")
            appendLine(prefs.getString("last_crash", "未记录"))
            if (Build.VERSION.SDK_INT >= 30) {
                appendLine("系统进程退出记录：")
                runCatching {
                    val exits = context.getSystemService(ActivityManager::class.java)
                        .getHistoricalProcessExitReasons(context.packageName, 0, 3)
                    if (exits.isEmpty()) appendLine("系统未提供记录")
                    exits.forEach {
                        appendLine("time=" + it.timestamp + " reason=" + it.reason + " " + it.description)
                    }
                }.onFailure { appendLine("无法读取：" + it.javaClass.simpleName) }
            }
            appendLine("服务与设置事件：")
            appendLine(prefs.getString("events", "未记录"))
            appendLine("最近一次主动系统诊断：")
            if (prefs.getBoolean("system_capture_pending", false) && !capturing.get())
                appendLine("上一轮未完成，可能因进程重启或采集中断；请重新采集。")
            appendLine(prefs.getString("system_capture", "尚未采集"))
            appendLine("当前会话：")
            appendLine(RuntimeState.logText())
        }
    }
}
