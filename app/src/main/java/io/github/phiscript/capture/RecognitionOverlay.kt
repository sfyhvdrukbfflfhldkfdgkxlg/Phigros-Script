package io.github.phiscript.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** UI stays on the main thread; capture masks its real screen bounds before publishing frames. */
internal class RecognitionOverlay(context: Context, private val onStop: (String) -> Unit) : AutoCloseable {
    data class Choice(val token: Long, val start: Boolean)
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(WindowManager::class.java)
    private val closed = AtomicBoolean(false)
    private val choice = AtomicReference<Choice?>(null)
    private val bounds = AtomicReference<Rect?>(null)
    private val present = AtomicBoolean(false)
    private val blockCaptureUntil = AtomicLong(0)
    private val maskPaint = Paint().apply { color = Color.BLACK }
    private var panel: LinearLayout? = null
    private var label: TextView? = null
    private var startButton: Button? = null
    private var skipButton: Button? = null
    private var activeToken = 0L

    init { check(Settings.canDrawOverlays(context)) { "请先允许显示悬浮窗，再启动识别" } }

    fun status(message: String) = update {
        activeToken = 0L
        choice.set(null)
        label!!.text = message
        startButton!!.visibility = View.GONE
        skipButton!!.visibility = View.GONE
    }
    fun offer(token: Long, title: String, difficulty: String, preview: Boolean) = update {
        // Cancel any press on the previous identity's controls before installing a new offer.
        removePanel()
        ensurePanel()
        activeToken = token
        choice.set(null)
        label!!.text = title + " · " + difficulty +
            if (preview) "\n已识别（只识别模式，不会点击）" else "\n是否开始自动演奏？"
        startButton!!.visibility = if (preview) View.GONE else View.VISIBLE
        startButton!!.isEnabled = true
        skipButton!!.visibility = View.VISIBLE
        skipButton!!.isEnabled = true
        skipButton!!.text = if (preview) "继续识别" else "暂不开始"
    }
    fun consumeChoice(): Choice? = choice.getAndSet(null)

    private fun update(action: () -> Unit) {
        main.post {
            if (closed.get()) return@post
            try {
                blockCaptureUntil.set(SystemClock.uptimeMillis() + SETTLE_MS)
                ensurePanel()
                action()
                panel!!.post { updateBounds() }
            } catch (e: Exception) {
                close()
                onStop("悬浮窗不可用：" + e.message)
            }
        }
    }

    private fun ensurePanel() {
        if (panel != null) return
        check(Settings.canDrawOverlays(context)) { "悬浮窗权限已撤销" }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 35, 49))
                cornerRadius = dp(12).toFloat()
            }
        }
        label = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            maxLines = 4
        }.also { container.addView(it) }
        val actions = LinearLayout(context)
        fun button(text: String, action: () -> Unit): Button = Button(context).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            setPadding(dp(3), 0, dp(3), 0)
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { action() }
            actions.addView(this, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        startButton = button("开始演奏") { decide(true) }
        skipButton = button("暂不开始") { decide(false) }
        button("停止") { onStop("已从悬浮窗停止") }
        container.addView(actions)
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds() }
        val screenWidth = context.resources.displayMetrics.widthPixels
        val width = minOf(dp(320), (screenWidth * 0.43f).toInt()).coerceAtLeast(dp(180))
        val params = WindowManager.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            x = dp(12); y = dp(12)
        }
        panel = container
        present.set(true)
        try { manager.addView(container, params) }
        catch (e: Exception) { panel = null; present.set(false); throw e }
    }

    private fun decide(start: Boolean) {
        if (activeToken == 0L || closed.get()) return
        choice.compareAndSet(null, Choice(activeToken, start))
        startButton?.isEnabled = false
        skipButton?.isEnabled = false
    }
    private fun updateBounds() {
        val view = panel ?: return
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        // Include the compositor shadow and antialiased edges.
        val margin = dp(12)
        bounds.set(Rect(location[0] - margin, location[1] - margin,
            location[0] + view.width + margin, location[1] + view.height + margin))
    }

    /** Called only on the capture thread, on a mutable bitmap before FrameStore.publish(). */
    fun maskCapture(bitmap: Bitmap, screenWidth: Int, screenHeight: Int, capturedAt: Long): Boolean {
        if (capturedAt < blockCaptureUntil.get()) return false
        val rect = bounds.get() ?: return !present.get()
        val sx = bitmap.width.toFloat() / screenWidth
        val sy = bitmap.height.toFloat() / screenHeight
        Canvas(bitmap).drawRect(rect.left * sx, rect.top * sy, rect.right * sx, rect.bottom * sy, maskPaint)
        return true
    }

    /** Synchronous removal prevents gestures hitting the panel; returns earliest usable frame time. */
    fun hide(): Long {
        if (Looper.myLooper() == Looper.getMainLooper()) removePanel()
        else {
            val done = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>(null)
            main.post {
                try { removePanel() }
                catch (e: Exception) { failure.set(e) }
                finally { done.countDown() }
            }
            check(done.await(2, TimeUnit.SECONDS)) { "隐藏悬浮窗超时，未开始演奏" }
            failure.get()?.let { throw IllegalStateException("无法移除悬浮窗，未开始演奏", it) }
        }
        return blockCaptureUntil.get()
    }
    private fun removePanel() {
        blockCaptureUntil.set(SystemClock.uptimeMillis() + SETTLE_MS)
        activeToken = 0L
        choice.set(null)
        val old = panel
        if (old != null) manager.removeViewImmediate(old)
        panel = null; label = null; startButton = null; skipButton = null
        present.set(false)
        bounds.set(null)
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (Looper.myLooper() == Looper.getMainLooper()) runCatching { removePanel() }
        else main.post { runCatching { removePanel() } }
    }
    private fun dp(value: Int) = (context.resources.displayMetrics.density * value).toInt()
    companion object { private const val SETTLE_MS = 250L }
}
