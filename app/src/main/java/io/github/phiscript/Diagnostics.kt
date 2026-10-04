package io.github.phiscript

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class PhigrosApplication : Application() {
    override fun onCreate() { super.onCreate(); Diagnostics.install(this) }
}
object Diagnostics {
    private const val STORE = "diagnostics"
    private var installed = false
    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date())
    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
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
        record(app, "应用进程启动 pid=" + Process.myPid())
    }
    @Synchronized fun record(context: Context, event: String, error: Throwable? = null) {
        runCatching {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val line = now() + "  " + event + (error?.let { "\n" + it.stackTraceToString().take(6000) } ?: "")
            prefs.edit().putString("events", ((prefs.getString("events", "") ?: "") + "\n" + line).takeLast(16000)).apply()
        }
    }
    fun report(context: Context): String {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        return buildString {
            appendLine("Phigros Script " + BuildConfig.VERSION_NAME + " 诊断 " + now())
            appendLine("Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT)
            appendLine("设备 " + Build.MANUFACTURER + " " + Build.MODEL)
            appendLine("无障碍：" + io.github.phiscript.input.AccessibilityStatus.read(context))
            appendLine("最近崩溃（无记录不能排除系统结束进程）：")
            appendLine(prefs.getString("last_crash", "未记录"))
            if (Build.VERSION.SDK_INT >= 30) {
                appendLine("系统进程退出记录：")
                runCatching {
                    context.getSystemService(ActivityManager::class.java)
                        .getHistoricalProcessExitReasons(context.packageName, 0, 3).forEach {
                            appendLine("time=" + it.timestamp + " reason=" + it.reason + " " + it.description)
                        }
                }.onFailure { appendLine("无法读取：" + it.javaClass.simpleName) }
            }
            appendLine("服务事件："); appendLine(prefs.getString("events", "未记录"))
            appendLine("当前会话："); appendLine(RuntimeState.logText())
        }
    }
}
