package io.github.phiscript.assets

/** One explicit request only. There is no retry, observer, background restore, or rollback. */
object SelfAccessibilityToggle {
    const val COMPONENT = "io.github.phiscript/io.github.phiscript.input.TouchService"
    private const val MAX_LIST_BYTES = 32 * 1024 - 1

    interface SettingsPort {
        fun readServices(): String?
        fun readEnabled(): String?
        fun writeServices(value: String)
        fun writeEnabled(value: String)
    }

    data class Result(
        val settingsEnabled: Boolean,
        val listWriteAttempted: Boolean,
        val globalWriteAttempted: Boolean,
        val message: String
    )

    private data class Snapshot(val services: String?, val enabled: String?)

    fun enable(port: SettingsPort): Result {
        var listAttempted = false
        var globalAttempted = false
        try {
            val first = read(port)
            val second = read(port)
            check(first == second) { "无障碍设置正在变化，请等待设置稳定后手动重试。" }
            val original = components(first.services)
            val originallyEnabled = enabled(first)
            var latest = second

            if (COMPONENT !in original) {
                val raw = first.services.orEmpty()
                val appended = when {
                    raw.isEmpty() || raw.endsWith(':') -> raw + COMPONENT
                    else -> raw + ":" + COMPONENT
                }
                checkSize(appended)
                listAttempted = true
                port.writeServices(appended)
                latest = read(port)
                verifyComponents(latest, original)
                if (originallyEnabled && !enabled(latest)) {
                    error("全局无障碍开关在操作期间被关闭，已停止后续写入。")
                }
            }

            if (!enabled(latest)) {
                val immediatelyBeforeGlobalWrite = read(port)
                check(immediatelyBeforeGlobalWrite == latest) {
                    "无障碍设置在提交期间发生变化，已停止后续写入。"
                }
                verifyComponents(immediatelyBeforeGlobalWrite, original)
                globalAttempted = true
                port.writeEnabled("1")
            }

            val finalState = read(port)
            verifyComponents(finalState, original)
            check(enabled(finalState)) { "读回的全局无障碍开关未开启。" }
            val message = if (listAttempted || globalAttempted) {
                "已提交一次开启请求，系统设置已读回确认；实际连接请以主页状态为准。本次不会自动重试。"
            } else {
                "系统设置已启用本服务，本次未写入；实际连接请以主页状态为准。"
            }
            return Result(true, listAttempted, globalAttempted, message)
        } catch (e: Exception) {
            val detail = (e.message ?: e.javaClass.simpleName).take(240)
            val prefix = if (listAttempted || globalAttempted) {
                "未能确认启用。可能已有设置写入，未执行回滚或再次开启。"
            } else {
                "本次未写入系统设置。"
            }
            return Result(false, listAttempted, globalAttempted, prefix + "原因：" + detail)
        }
    }

    private fun read(port: SettingsPort) = Snapshot(port.readServices(), port.readEnabled())

    private fun enabled(snapshot: Snapshot): Boolean = when (snapshot.enabled) {
        "0" -> false
        "1" -> true
        else -> error("全局无障碍设置不是可识别的 0 或 1，已停止操作。")
    }

    private fun verifyComponents(snapshot: Snapshot, original: Set<String>) {
        val current = components(snapshot.services)
        check(COMPONENT in current) { "读回列表中没有本服务，设置可能未生效或已被撤销。" }
        check(current.containsAll(original)) { "其他无障碍服务列表发生变化，已停止操作。" }
    }

    /** Preserve the stored text for writes; normalize only for identity checks. */
    private fun components(rawValue: String?): Set<String> {
        val raw = rawValue.orEmpty()
        checkSize(raw)
        check(raw.none { it.isWhitespace() || it.isISOControl() }) {
            "无障碍服务列表格式异常，已停止操作。"
        }
        return raw.split(':').filter { it.isNotEmpty() }.mapTo(linkedSetOf()) { component ->
            val slash = component.indexOf('/')
            check(slash > 0 && slash == component.lastIndexOf('/') && slash < component.lastIndex) {
                "无障碍服务列表格式异常，已停止操作。"
            }
            val packageName = component.substring(0, slash)
            val className = component.substring(slash + 1)
            packageName + "/" + if (className.startsWith('.')) packageName + className else className
        }
    }

    private fun checkSize(raw: String) {
        check(raw.toByteArray(Charsets.UTF_8).size <= MAX_LIST_BYTES) {
            "无障碍服务列表超过读取上限，已停止操作。"
        }
    }
}
