package io.github.phiscript.assets

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Only called after authenticating the Binder caller and clearing Binder calling identity. */
object SelfAccessibilityEnabler {
    fun enable(clientUid: Int): String {
        require(clientUid >= 0) { "Invalid client UID" }
        val result = SelfAccessibilityToggle.enable(ShellSettings(clientUid / 100_000))
        check(result.settingsEnabled) { result.message }
        return result.message
    }

    private class ShellSettings(private val userId: Int) : SelfAccessibilityToggle.SettingsPort {
        override fun readServices(): String? = get("enabled_accessibility_services")
        override fun readEnabled(): String? = get("accessibility_enabled")
        override fun writeServices(value: String) = put("enabled_accessibility_services", value)
        override fun writeEnabled(value: String) = put("accessibility_enabled", value)

        private fun get(key: String): String? {
            val output = run("get", key, null).removeSuffix("\n").removeSuffix("\r")
            check('\n' !in output && '\r' !in output) { "settings 读取结果格式异常。" }
            return output.takeUnless { it == "null" }
        }

        private fun put(key: String, value: String) {
            check(run("put", key, value).isBlank()) { "settings 写入返回异常内容，无法确认结果。" }
        }

        private fun run(operation: String, key: String, value: String?): String {
            check(operation == "get" || operation == "put")
            check(key == "enabled_accessibility_services" || key == "accessibility_enabled")
            val argv = mutableListOf("/system/bin/cmd", "settings", "--user", userId.toString(),
                operation, "secure", key)
            if (value != null) argv.add(value)
            return BoundedCommand.run(argv)
        }
    }

    private object BoundedCommand {
        private const val TIMEOUT_MS = 2_000L
        private const val MAX_OUTPUT_BYTES = 32 * 1024

        fun run(argv: List<String>): String {
            val process = try { ProcessBuilder(argv).start() }
            catch (e: Exception) {
                throw IllegalStateException("无法启动系统 settings 命令：" + e.javaClass.simpleName)
            }
            val total = AtomicInteger()
            val oversized = AtomicBoolean()
            val stopping = AtomicBoolean()
            val readerFailed = AtomicBoolean()
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()

            fun terminate() {
                if (process.isAlive) runCatching { process.destroyForcibly() }
            }

            fun reader(input: InputStream, output: ByteArrayOutputStream, label: String) = Thread({
                try {
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        val previous = total.getAndAdd(count)
                        val keep = (MAX_OUTPUT_BYTES - previous).coerceIn(0, count)
                        if (keep > 0) output.write(buffer, 0, keep)
                        if (keep < count) {
                            oversized.set(true)
                            terminate()
                            break
                        }
                    }
                } catch (_: Exception) {
                    if (!stopping.get()) readerFailed.set(true)
                }
            }, "phiscript-settings-" + label).apply { isDaemon = true }

            val readers = listOf(reader(process.inputStream, stdout, "stdout"),
                reader(process.errorStream, stderr, "stderr"))
            var interrupted = false
            try {
                readers.forEach { it.start() }
                val completed = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (!completed) {
                    terminate()
                    process.waitFor(150, TimeUnit.MILLISECONDS)
                }
                readers.forEach { it.join(150) }
                check(completed) { "settings 命令超时，结果无法确认。" }
                check(!oversized.get()) { "settings 输出超过 32 KiB，已终止命令。" }
                check(!process.isAlive) { "settings 命令未正常结束，结果无法确认。" }
                check(readers.none { it.isAlive } && !readerFailed.get()) {
                    "settings 输出读取不完整，结果无法确认。"
                }
                val text = stdout.toString("UTF-8")
                val errors = stderr.toString("UTF-8")
                val exit = process.exitValue()
                check(exit == 0 && errors.isBlank()) {
                    val denied = (text + errors).let {
                        it.contains("SecurityException", ignoreCase = true) ||
                            it.contains("Permission denial", ignoreCase = true) ||
                            it.contains("Permission denied", ignoreCase = true)
                    }
                    if (denied) "系统拒绝 settings 访问，退出码=" + exit
                    else "settings 命令返回错误，退出码=" + exit
                }
                return text
            } catch (_: InterruptedException) {
                interrupted = true
                throw IllegalStateException("settings 操作被中断，结果无法确认。")
            } finally {
                stopping.set(true)
                terminate()
                // Vendor pipe close can wait for a reader lock: never block the Binder thread on it.
                for (stream in listOf<Closeable>(
                    process.inputStream, process.errorStream, process.outputStream
                )) {
                    Thread({ runCatching { stream.close() } }, "phiscript-settings-close")
                        .apply { isDaemon = true; start() }
                }
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
    }
}
