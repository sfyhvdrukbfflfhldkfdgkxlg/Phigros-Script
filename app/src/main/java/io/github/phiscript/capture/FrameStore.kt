package io.github.phiscript.capture

import android.graphics.Bitmap

data class CapturedFrame(
    val bitmap: Bitmap,
    val uptimeMs: Long,
    val screenWidth: Int,
    val screenHeight: Int
) : AutoCloseable {
    override fun close() { bitmap.recycle() }
}

class FrameStore : AutoCloseable {
    private var latest: CapturedFrame? = null
    private var closed = false
    @Synchronized fun publish(frame: CapturedFrame) {
        if (closed) { frame.close(); return }
        latest?.close()
        latest = frame
    }
    @Synchronized fun snapshot(): CapturedFrame? = latest?.let {
        val copy = it.bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@let null
        CapturedFrame(copy, it.uptimeMs, it.screenWidth, it.screenHeight)
    }
    @Synchronized override fun close() {
        closed = true
        latest?.close()
        latest = null
    }
}
