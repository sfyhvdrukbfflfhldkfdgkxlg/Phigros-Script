package io.github.phiscript.assets

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

/** Process connection: lifecycle on main thread, asset IO on worker. Permission requests are explicit. */
object ApkAccess {
    private const val REQUEST_CODE = 2048
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var remote: IPhigrosApkReader? = null
    @Volatile private var text = "Shizuku 未连接；优先尝试直接读取安装包。"
    private var attached = false
    private var observer: ((String) -> Unit)? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null
    private var timeout: Runnable? = null
    val isConnected: Boolean get() = remote?.asBinder()?.isBinderAlive == true
    fun status(): String = text
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val died = Shizuku.OnBinderDeadListener {
        clearConnection(); emit("Shizuku 已停止，请重新启动并连接。")
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { request, result ->
        if (request == REQUEST_CODE) {
            if (result == PackageManager.PERMISSION_GRANTED) refresh()
            else emit("未获得 Shizuku 授权；仍可尝试直接读取安装包。")
        }
    }
    fun attach(context: Context, onStatus: (String) -> Unit) {
        requireMainThread()
        if (attached) { observer = onStatus; onStatus(text); refresh(); return }
        attached = true; observer = onStatus
        val app = context.applicationContext
        args = Shizuku.UserServiceArgs(ComponentName(app, ApkReadService::class.java))
            .daemon(false).processNameSuffix("phigros_apk_reader").tag("phigros-apk-reader-v1")
            .version(2).debuggable(false)
        Shizuku.addBinderDeadListener(died, main)
        Shizuku.addRequestPermissionResultListener(permissionResult, main)
        Shizuku.addBinderReceivedListenerSticky(received, main)
        refresh()
    }
    fun requestPermission() {
        requireMainThread()
        if (!attached) { emit("请先打开应用主页。"); return }
        try {
            if (!Shizuku.pingBinder()) {
                emit("请先安装并启动 Shizuku。手机内通过无线调试启动需要 Android 11 以上。")
            } else if (Shizuku.isPreV11() || Shizuku.getVersion() < 13) {
                emit("请将 Shizuku 更新到 13 或更新版本。")
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) refresh()
            else if (Shizuku.shouldShowRequestPermissionRationale()) {
                emit("请在 Shizuku 的“已授权应用”中允许本应用，然后返回重试。")
            } else Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: RuntimeException) { emit("Shizuku 授权失败：" + (e.message ?: e.javaClass.simpleName)) }
    }
    fun detach() {
        requireMainThread()
        if (!attached) return
        attached = false; observer = null
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(died)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        val previous = connection; val serviceArgs = args
        clearConnection()
        if (previous != null && serviceArgs != null && Shizuku.pingBinder()) {
            runCatching { Shizuku.unbindUserService(serviceArgs, previous, true) }
            runCatching { Shizuku.unbindUserService(serviceArgs, previous, false) }
        }
        args = null; text = "Shizuku 连接已关闭。"
    }
    internal fun entries(snapshot: PackageSnapshot): List<String> {
        val descriptor = requireRemote().listEntries(snapshot.versionCode, snapshot.lastUpdateTime)
            ?: throw ApkAccessException("Shizuku 没有返回安装包目录。")
        val bytes = consume(descriptor, ApkFiles.MAX_LIST_BYTES)
        val array = JSONArray(String(bytes, Charsets.UTF_8))
        if (array.length() > ApkFiles.MAX_ENTRIES) throw ApkAccessException("安装包目录过大。")
        return List(array.length()) { array.getString(it) }
    }
    internal fun read(snapshot: PackageSnapshot, entry: String, maxBytes: Int): ByteArray {
        ApkFiles.validateRead(entry, maxBytes)
        val descriptor = requireRemote().readAsset(entry, maxBytes, snapshot.versionCode, snapshot.lastUpdateTime)
            ?: throw ApkAccessException("Shizuku 没有返回资源数据。")
        return consume(descriptor, maxBytes)
    }
    /** Explicit user action only; the remote runs fixed read-only commands with a time/output limit. */
    internal fun collectAccessibilityDiagnostics(): String {
        check(Looper.myLooper() != Looper.getMainLooper())
        return requireRemote().collectAccessibilityDiagnostics()
            ?: throw ApkAccessException("系统诊断没有返回结果。")
    }
    private fun requireRemote(): IPhigrosApkReader {
        check(Looper.myLooper() != Looper.getMainLooper()) { "APK reads must run on a background thread." }
        return remote?.takeIf { it.asBinder().isBinderAlive }
            ?: throw ApkAccessException("Shizuku 尚未连接，请授权并等待连接后重试。")
    }
    private fun consume(descriptor: ParcelFileDescriptor, maxBytes: Int): ByteArray {
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            val buffer = ByteArray(64 * 1024); var count = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                count += n
                if (count > maxBytes) throw ApkAccessException("资源流超过读取上限。")
                output.write(buffer, 0, n)
            }
            descriptor.checkError(); output.toByteArray()
        }
    }
    private fun refresh() {
        if (!attached) return
        try {
            when {
                !Shizuku.pingBinder() -> emit("Shizuku 未启动；优先尝试直接读取安装包。")
                Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> emit("读取备用通道需要 Shizuku 13 或更新版本。")
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                    emit("Shizuku 正在运行，点击授权后可启用备用读取通道。")
                isConnected -> emit("Shizuku 已连接，可读取 Phigros 安装包资源。")
                connection == null -> bind()
            }
        } catch (e: RuntimeException) { emit("Shizuku 连接失败：" + (e.message ?: e.javaClass.simpleName)) }
    }
    private fun bind() {
        val serviceArgs = args ?: return
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val self = this
                main.post {
                    if (!attached || connection !== self) return@post
                    timeout?.let(main::removeCallbacks); timeout = null
                    remote = IPhigrosApkReader.Stub.asInterface(binder)
                    emit("Shizuku 已连接，可读取 Phigros 安装包资源。")
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                val self = this
                main.post {
                    if (connection !== self) return@post
                    clearConnection(); emit("Shizuku 读取服务已断开，点击授权按钮可重新连接。")
                }
            }
        }
        connection = next; emit("正在连接 Shizuku 读取服务…")
        try {
            Shizuku.bindUserService(serviceArgs, next)
            val expiry = Runnable {
                if (connection === next && !isConnected) {
                    clearConnection()
                    runCatching { Shizuku.unbindUserService(serviceArgs, next, true) }
                    runCatching { Shizuku.unbindUserService(serviceArgs, next, false) }
                    emit("Shizuku 读取服务连接超时，请重试。")
                }
            }
            timeout = expiry; main.postDelayed(expiry, 10_000)
        } catch (e: RuntimeException) {
            clearConnection(); runCatching { Shizuku.unbindUserService(serviceArgs, next, false) }
            emit("Shizuku 读取服务启动失败：" + (e.message ?: e.javaClass.simpleName))
        }
    }
    private fun clearConnection() {
        timeout?.let(main::removeCallbacks); timeout = null; remote = null; connection = null
    }
    private fun emit(message: String) { text = message; if (attached) observer?.invoke(message) }
    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Shizuku lifecycle operations must run on the main thread." }
    }
}
