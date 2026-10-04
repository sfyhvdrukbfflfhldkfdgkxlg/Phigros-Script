package io.github.phiscript.assets

import java.util.ArrayDeque
import java.util.Locale

/** Deliberately narrow: absence of a match never proves absence of a policy change. */
internal class AccessibilityLogFilter(clientUid: Int) {
    init { require(clientUid >= 0) }

    private val uid = Regex(
        "(?i)(?<![A-Za-z0-9_])(?:uid|appUid|targetUid|callingUid)\\s*(?:[=:]\\s*|\\s+)" +
            clientUid + "(?![A-Za-z0-9_.])"
    )

    fun keep(line: String): String? {
        if (line.length > MAX_LINE_CHARS || line.any { it < ' ' && it != '\t' }) return null
        val parsed = THREADTIME.matchEntire(line) ?: return null
        val tag = parsed.groupValues[1].trim().lowercase(Locale.ROOT)
        val message = parsed.groupValues[2]
        // Some platform debug messages embed window text or AccessibilityEvent payloads.
        if (PRIVATE_PAYLOAD.containsMatchIn(message)) return null
        val ownPackage = PACKAGE.containsMatchIn(message) || COMPONENT.containsMatchIn(message)
        if (tag == "activitymanager") {
            return line.takeIf { ownPackage && LIFECYCLE.containsMatchIn(message) }
        }
        val policyTag = tag.contains("accessibility") || tag.contains("permission") ||
            tag.contains("appops") || tag.contains("enhancedconfirmation") ||
            tag == "packagemanager" || tag == "packagemanagerservice"
        return line.takeIf { policyTag && (ownPackage || uid.containsMatchIn(message)) }
    }

    companion object {
        const val PACKAGE_NAME = "io.github.phiscript"
        const val MAX_LINE_CHARS = 4096
        private val THREADTIME = Regex(
            "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+([^:]{1,96}):\\s?(.*)$"
        )
        private val PACKAGE = Regex("(?<![A-Za-z0-9_.$])io\\.github\\.phiscript(?![A-Za-z0-9_.$])")
        private val COMPONENT = Regex(
            "(?<![A-Za-z0-9_.$])io\\.github\\.phiscript\\.input\\.TouchService(?![A-Za-z0-9_.$])"
        )
        private val PRIVATE_PAYLOAD = Regex(
            "(?i)(?:accessibilityevent|beforetext|contentdescription|\\btext\\s*[:=]|" +
                "\\bextras\\s*[:=]|\\bocr\\b|recognizedtext|recognitionresult|clipdata|" +
                "password|parcelable|\\bdata\\s*[:=]|\\bdat=)"
        )
        private val LIFECYCLE = Regex(
            "(?i)(?:\\bkilling\\b|\\bforce stopping\\b|\\bstart proc\\b|\\bhas died\\b|" +
                "\\bcrash\\b|\\banr\\b|\\bbinding\\b|\\bunbinding\\b|\\bservice\\b)"
        )
    }
}

/** Keeps the most recent complete lines. Memory and UTF-8 output are bounded. */
internal class BoundedUtf8Lines(private val maxBytes: Int) {
    init { require(maxBytes in 1..(48 * 1024)) }
    private val lines = ArrayDeque<Pair<String, Int>>()
    private var bytes = 0
    @Volatile var droppedLines: Long = 0
        private set

    @Synchronized fun append(line: String) {
        val prefix = utf8Prefix(line, maxBytes - 1)
        if (prefix.length != line.length) droppedLines++
        val value = prefix + "\n"
        val size = value.toByteArray(Charsets.UTF_8).size
        while (bytes + size > maxBytes && lines.isNotEmpty()) {
            bytes -= lines.removeFirst().second
            droppedLines++
        }
        lines.addLast(value to size)
        bytes += size
    }

    @Synchronized fun snapshot(): String = buildString { lines.forEach { append(it.first) } }

    companion object {
        /** Does not encode/copy an arbitrarily large input before applying the bound. */
        fun utf8Prefix(text: String, limit: Int): String {
            require(limit >= 0)
            var index = 0
            var bytes = 0
            while (index < text.length) {
                val c = text[index]
                val pair = Character.isHighSurrogate(c) && index + 1 < text.length &&
                    Character.isLowSurrogate(text[index + 1])
                val size = when {
                    pair -> 4
                    c.code <= 0x7f -> 1
                    c.code <= 0x7ff -> 2
                    else -> 3
                }
                if (bytes + size > limit) break
                bytes += size
                index += if (pair) 2 else 1
            }
            return text.substring(0, index)
        }
    }
}
