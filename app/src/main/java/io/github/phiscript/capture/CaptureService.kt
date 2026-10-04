package io.github.phiscript.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import io.github.phiscript.AppSettings
import io.github.phiscript.MainActivity
import io.github.phiscript.RuntimeState
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

class CaptureService : Service() {
    private val stop = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)
    private val frames = FrameStore()
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var engineThread: Thread? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var lastGrab = 0L
    private var displayManager: DisplayManager? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { finish("录屏已结束") }
        override fun onCapturedContentResize(width: Int, height: Int) {
            captureHandler?.post { runCatching { configure(width, height) }
                .onFailure { finish("画面调整失败：" + it.message) } }
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY) {
                val dm = metrics()
                runCatching { configure(dm.widthPixels, dm.heightPixels) }
                    .onFailure { finish("画面调整失败：" + it.message) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish("已停止")
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (RuntimeState.running.get()) return START_NOT_STICKY
        try {
            foreground()
            val result = intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_DATA)
            }
            require(result == Activity.RESULT_OK && data != null) { "缺少录屏授权" }
            val settings = AppSettings(this).snapshot()
            val library = RuntimeState.library ?: error("请先扫描谱库")
            RuntimeState.running.set(true)
            captureThread = HandlerThread("phi-capture").also { it.start() }
            captureHandler = Handler(captureThread!!.looper)
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(result, data)
            projection!!.registerCallback(projectionCallback, Handler(mainLooper))
            displayManager = getSystemService(DisplayManager::class.java)
            displayManager!!.registerDisplayListener(displayListener, captureHandler)
            val dm = metrics()
            captureHandler!!.post {
                try { configure(dm.widthPixels, dm.heightPixels) }
                catch (e: Exception) { finish("截屏启动失败：" + e.message) }
            }
            val preview = intent.getBooleanExtra(EXTRA_PREVIEW, true)
            engineThread = Thread({
                try { SessionEngine(frames, library, settings, preview, stop).run() }
                catch (e: InterruptedException) { Thread.currentThread().interrupt() }
                catch (e: Exception) { RuntimeState.log("会话停止：" + e.message) }
                finally { Handler(mainLooper).post { finish(null) } }
            }, "phi-recognition").apply { start() }
        } catch (e: Exception) { finish("启动失败：" + e.message) }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun metrics(): DisplayMetrics = DisplayMetrics().also {
        getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(it)
    }

    private fun configure(realWidth: Int, realHeight: Int) {
        if (stop.get() || projection == null || realWidth <= 0 || realHeight <= 0) return
        if (realWidth == sourceWidth && realHeight == sourceHeight && reader != null) return
        val activeProjection = projection ?: return
        val factor = minOf(1.0, 1920.0 / max(realWidth, realHeight))
        val w = max(2, (realWidth * factor).roundToInt())
        val h = max(2, (realHeight * factor).roundToInt())
        val next = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val packedPixels = ByteBuffer.allocateDirect(w * h * 4)
        next.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull()
                ?: return@setOnImageAvailableListener
            image.use {
                val captured = SystemClock.uptimeMillis()
                if (stop.get() || captured - lastGrab < 90) return@use
                lastGrab = captured
                try {
                    val plane = it.planes[0]
                    require(plane.pixelStride == 4) { "不支持的屏幕像素格式" }
                    val buffer = plane.buffer
                    val bytes = packedPixels
                    bytes.clear()
                    val origin = buffer.position()
                    for (row in 0 until h) {
                        val slice = buffer.duplicate()
                        slice.position(origin + row * plane.rowStride)
                        slice.limit(origin + row * plane.rowStride + w * 4)
                        bytes.put(slice)
                    }
                    bytes.flip()
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(bytes)
                    frames.publish(CapturedFrame(bitmap, captured, realWidth, realHeight))
                } catch (e: Exception) { finish("读取截屏失败：" + e.message) }
            }
        }, captureHandler)
        val old = reader
        reader = next
        sourceWidth = realWidth
        sourceHeight = realHeight
        if (display == null) {
            display = activeProjection.createVirtualDisplay("PhigrosScript",
                w, h, metrics().densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                next.surface, null, captureHandler)
        } else {
            display!!.resize(w, h, metrics().densityDpi)
            display!!.surface = next.surface
        }
        old?.setOnImageAvailableListener(null, null)
        old?.close()
    }

    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "识别运行状态",
            NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val halt = PendingIntent.getService(this, 1,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Phigros Script 正在识别")
            .setContentText("点按停止可结束截屏和触控")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", halt).build()).build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification)
    }

    private fun finish(message: String?) {
        RuntimeState.activeTouchStop.get()?.takeIf { it.owner === stop }?.cancel?.set(true)
        if (stop.compareAndSet(false, true) && message != null) RuntimeState.log(message)
        Handler(mainLooper).post { stopSelf() }
    }

    override fun onDestroy() {
        if (!destroyed.compareAndSet(false, true)) return
        stop.set(true)
        RuntimeState.activeTouchStop.get()?.takeIf { it.owner === stop }?.cancel?.set(true)
        RuntimeState.running.set(false)
        displayManager?.unregisterDisplayListener(displayListener)
        // Teardown on the capture looper so no frame callback can race reader.close().
        val handler = captureHandler
        val cleanup = Runnable {
            reader?.setOnImageAvailableListener(null, null)
            display?.release()
            display = null
            reader?.close()
            reader = null
            frames.close()
            captureThread?.quitSafely()
        }
        if (handler != null) handler.post(cleanup) else cleanup.run()
        projection?.unregisterCallback(projectionCallback)
        runCatching { projection?.stop() }
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_START = "io.github.phiscript.START"
        const val ACTION_STOP = "io.github.phiscript.STOP"
        const val EXTRA_RESULT = "projection_result"
        const val EXTRA_DATA = "projection_data"
        const val EXTRA_PREVIEW = "preview"
        private const val CHANNEL = "recognition"
    }
}
