package io.github.phiscript.assets

import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Called only by the authenticated Shizuku Binder method, on a worker thread. */
object AccessibilitySystemCollector {
    private const val LOGCAT_MS = 20_000L
    private const val APP_OP_MS = 2_000L
    private const val MAX_REPORT_BYTES = 48 * 1024
    private val OPS = listOf("ACCESS_RESTRICTED_SETTINGS", "BIND_ACCESSIBILITY_SERVICE")

    fun collect(clientUid: Int): String {
        require(clientUid >= 0) { "Invalid client UID" }
        val started = stamp()
        val log = BoundedUtf8Lines(28 * 1024)
        val operations = BoundedUtf8Lines(12 * 1024)
        val status = BoundedUtf8Lines(4 * 1024)
        val filter = AccessibilityLogFilter(clientUid)
        val matches = AtomicInteger()
        val timer = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "phiscript-diagnostic-deadline").apply { isDaemon = true }
        }
        var logcat: Command? = null
        var interrupted = false
        try {
            // Start before querying AppOps: the user can already be returning to Settings.
            try {
                logcat = Command(
                    listOf("/system/bin/logcat", "-v", "threadtime", "-T", "1", "-b", "system", "-b", "main"),
                    LOGCAT_MS, timer
                ) { line ->
                    filter.keep(line)?.let { matches.incrementAndGet(); log.append(it) }
                }
                status.append("logcat 窗口开始：" + stamp())
            } catch (e: Exception) {
                status.append("logcat 启动失败：" + failure(e))
            }
            for (op in OPS) readOp("开始", op, clientUid / 100_000, timer, operations)
            logcat?.let {
                val outcome = it.await()
                status.append("logcat 窗口结束：" + stamp())
                status.append(outcome.describe("logcat", expectedDeadline = true))
            }
            for (op in OPS) readOp("结束", op, clientUid / 100_000, timer, operations)
        } catch (e: InterruptedException) {
            interrupted = true
            status.append("采集被中断，日志或前后状态不完整。")
        } catch (e: Exception) {
            status.append("采集失败，结果不完整：" + failure(e))
        } finally {
            logcat?.close()
            timer.shutdownNow()
            if (interrupted) Thread.currentThread().interrupt()
        }
        val report = buildString {
            append("无障碍系统只读采集\n开始：").append(started)
            append("\n结束：").append(stamp())
            append("\n目标：").append(AccessibilityLogFilter.PACKAGE_NAME)
            append(" uid=").append(clientUid).append(" userId=").append(clientUid / 100_000)
            append("\nlogcat 计划窗口：20 秒；仅保留目标相关的系统策略/服务行。\n")
            append("未匹配到日志不代表未发生撤销；系统可能不输出或拒绝读取。\n")
            append("AppOps 的 allow/default/无记录不能证明系统增强确认限制已解除。\n")
            append("命中行可能同时提及其他应用，分享前请查看内容。\n\n采集状态：\n")
            append(status.snapshot())
            append("\nAppOps 前后原始结果（只读）：\n").append(operations.snapshot())
            append("\n相关系统日志，命中 ").append(matches.get()).append(" 行：\n")
            if (matches.get() == 0) append("无匹配记录（不能据此排除系统策略）。\n")
            append(log.snapshot())
            append("\n容量淘汰/截断行数：日志=").append(log.droppedLines)
            append("，AppOps=").append(operations.droppedLines)
            append("，状态=").append(status.droppedLines).append('\n')
        }
        return BoundedUtf8Lines.utf8Prefix(report, MAX_REPORT_BYTES)
    }

    private fun readOp(
        phase: String, op: String, userId: Int, timer: ScheduledExecutorService,
        output: BoundedUtf8Lines
    ) {
        output.append("[" + stamp() + "] " + phase + " " + op)
        val body = BoundedUtf8Lines(2 * 1024)
        try {
            val command = Command(
                listOf("/system/bin/cmd", "appops", "get", "--user", userId.toString(),
                    AccessibilityLogFilter.PACKAGE_NAME, op),
                APP_OP_MS, timer, body::append
            )
            val outcome = command.await()
            if (body.snapshot().isBlank()) output.append("（标准输出为空）")
            else output.append(body.snapshot().trimEnd())
            output.append(outcome.describe("AppOps", expectedDeadline = false))
            if (body.droppedLines > 0) output.append("此命令输出已截断：" + body.droppedLines)
        } catch (e: InterruptedException) {
            output.append("此命令被中断，结果不完整。")
            throw e
        } catch (e: Exception) {
            output.append("读取失败：" + failure(e))
        }
    }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(Date())
    private fun failure(e: Exception): String = e.javaClass.simpleName + ": " +
        (e.message ?: "无错误详情").take(240).replace('\n', ' ')

    private data class Outcome(
        val exitCode: Int?, val deadline: Boolean, val stillAlive: Boolean,
        val readerIncomplete: Boolean, val oversized: Int, val errors: String, val stderr: String
    ) {
        fun describe(name: String, expectedDeadline: Boolean): String = buildString {
            append(name).append("：")
            when {
                stillAlive -> append("错误：强制结束后子进程仍未退出")
                deadline && expectedDeadline -> append("已达到 20 秒窗口并停止读取")
                deadline -> append("超时（2 秒），已终止，结果可能不完整")
                expectedDeadline -> append("提前退出，未完成 20 秒采集")
                exitCode == 0 -> append("命令退出码 0（仅说明命令执行完毕）")
                else -> append("命令失败")
            }
            append("；退出码=").append(exitCode ?: "未知")
            if (readerIncomplete) append("；读取线程未及时退出，结果不完整")
            if (oversized > 0) append("；超长行已丢弃=").append(oversized)
            if (errors.isNotBlank()) append("\n读取/清理错误：\n").append(errors.trimEnd())
            if (stderr.isNotBlank()) append("\n标准错误：\n").append(stderr.trimEnd())
        }
    }

    /** Every reader is bounded and daemonized; no unbounded readLine()/join()/waitFor(). */
    private class Command(
        argv: List<String>, private val timeoutMs: Long, timer: ScheduledExecutorService,
        onLine: (String) -> Unit
    ) : AutoCloseable {
        private val process = ProcessBuilder(argv).start()
        private val started = System.nanoTime()
        private val stopping = AtomicBoolean()
        private val finished = AtomicBoolean()
        private val deadline = AtomicBoolean()
        private val oversized = AtomicInteger()
        private val errors = BoundedUtf8Lines(768)
        private val stderr = BoundedUtf8Lines(768)
        private val stdoutThread = reader(process.inputStream, onLine, "stdout")
        private val stderrThread = reader(process.errorStream, stderr::append, "stderr")
        private val watchdog = timer.schedule({ terminate(timedOut = true) }, timeoutMs, TimeUnit.MILLISECONDS)

        init {
            try {
                stdoutThread.start()
                stderrThread.start()
            } catch (e: Exception) {
                close()
                throw e
            }
        }

        fun await(): Outcome {
            try {
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                if (!process.waitFor((timeoutMs - elapsed).coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
                    terminate(timedOut = true)
                }
            } finally {
                close()
            }
            return Outcome(
                runCatching { process.exitValue() }.getOrNull(), deadline.get(), process.isAlive,
                stdoutThread.isAlive || stderrThread.isAlive, oversized.get(), errors.snapshot(), stderr.snapshot()
            )
        }

        private fun terminate(timedOut: Boolean) {
            if (timedOut && process.isAlive) deadline.set(true)
            stopping.set(true)
            if (process.isAlive) {
                try { process.destroyForcibly() }
                catch (e: Exception) { errors.append("终止失败：" + failure(e)) }
            }
        }

        override fun close() {
            if (!finished.compareAndSet(false, true)) return
            watchdog.cancel(false)
            var interrupted = Thread.interrupted()
            if (process.isAlive) terminate(timedOut = false)
            try { process.waitFor(200, TimeUnit.MILLISECONDS) }
            catch (_: InterruptedException) { interrupted = true }
            // Give normal EOF a short chance to drain before closing the pipes.
            for (thread in listOf(stdoutThread, stderrThread)) {
                try { thread.join(100) }
                catch (_: InterruptedException) { interrupted = true }
            }
            stopping.set(true)
            // A vendor stream close can wait on a reader lock; never block the Binder on it.
            for (stream in listOf<java.io.Closeable>(process.inputStream, process.errorStream, process.outputStream)) {
                Thread({ runCatching { stream.close() }.onFailure {
                    errors.append("管道关闭失败：" + it.javaClass.simpleName)
                } }, "phiscript-diagnostic-close").apply {
                    isDaemon = true
                    start()
                }
            }
            for (thread in listOf(stdoutThread, stderrThread)) {
                try { thread.join(100) }
                catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }

        private fun reader(stream: InputStream, receive: (String) -> Unit, label: String): Thread =
            Thread({
                try {
                    InputStreamReader(stream, Charsets.UTF_8).use { input ->
                        val chunk = CharArray(512)
                        val line = StringBuilder()
                        var discard = false
                        while (true) {
                            val count = input.read(chunk)
                            if (count < 0) break
                            for (i in 0 until count) {
                                val c = chunk[i]
                                if (c == '\n') {
                                    if (!discard) receive(line.toString().trimEnd('\r'))
                                    line.setLength(0)
                                    discard = false
                                } else if (!discard) {
                                    if (line.length >= AccessibilityLogFilter.MAX_LINE_CHARS) {
                                        oversized.incrementAndGet()
                                        line.setLength(0)
                                        discard = true
                                    } else line.append(c)
                                }
                            }
                        }
                        if (!discard && line.isNotEmpty()) receive(line.toString().trimEnd('\r'))
                    }
                } catch (e: Exception) {
                    if (!stopping.get()) errors.append(label + "：" + failure(e))
                }
            }, "phiscript-diagnostic-" + label).apply { isDaemon = true }
    }
}
