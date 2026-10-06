package io.github.phiscript

import io.github.phiscript.assets.ChartLibrary
import io.github.phiscript.assets.PhiraLibrary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object RuntimeState {
    val running = AtomicBoolean(false)
    data class ActiveTouch(val owner: AtomicBoolean, val cancel: AtomicBoolean)
    val activeTouchStop = AtomicReference<ActiveTouch?>(null)
    @Volatile var library: ChartLibrary? = null
    @Volatile var phiraLibrary: PhiraLibrary? = null
    private val lines = ArrayDeque<String>()
    @Synchronized fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        lines.addLast(time + "  " + message)
        while (lines.size > 40) lines.removeFirst()
    }
    @Synchronized fun logText(): String = lines.joinToString("\n")
}
