package io.github.phiscript

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.phiscript.assets.ApkAccess
import io.github.phiscript.assets.ChartLibrary
import io.github.phiscript.capture.CaptureService
import io.github.phiscript.input.TouchService
import io.github.phiscript.input.AccessibilityStatus
import io.github.phiscript.input.AccessibilityActivation

class MainActivity : ComponentActivity() {
    private val libraryWork by viewModels<LibraryWork>()
    private val worker get() = libraryWork.worker
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private lateinit var accessibilityView: TextView
    private var nextAccessCheck = 0L
    private var scanning: Boolean
        get() = libraryWork.scanning
        set(value) { libraryWork.scanning = value }
    private var pickerPending = false
    private var preview = true
    private val preferences by lazy { AppSettings(this) }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { }
    private val screenPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, CaptureService::class.java)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_RESULT, result.resultCode)
                .putExtra(CaptureService.EXTRA_DATA, result.data)
                .putExtra(CaptureService.EXTRA_PREVIEW, preview)
            try {
                ContextCompat.startForegroundService(this, intent)
                packageManager.getLaunchIntentForPackage("com.PigeonGames.Phigros")
                    ?.let { startActivity(it) }
            } catch (e: Exception) { message("启动失败：" + e.message) }
        } else message("未授权录屏，未启动")
    }
    private val apkPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pickerPending = false
        if (uris.isNotEmpty()) importApks(uris.distinct())
        else restoreImportedLibrary()
    }
    private val refresh = object : Runnable {
        override fun run() {
            if (!::statusView.isInitialized) return
            val library = RuntimeState.library
            statusView.text = when {
                AccessibilityActivation.isRunning -> AccessibilityActivation.status
                Diagnostics.isCapturing -> "系统诊断采集中 · 约 30 秒后完成"
                RuntimeState.running.get() -> "识别运行中 · 可在通知栏停止"
                scanning -> "正在读取谱库…"
                library != null -> "谱库就绪 · " + library.sourceLabel + " · " + library.snapshot.versionName +
                    " · " + library.locations.size + " 个难度"
                else -> "扫描本机谱库，或选择 Phigros APK 导入"
            }
            val now = android.os.SystemClock.uptimeMillis()
            if (now >= nextAccessCheck) {
                accessibilityView.text = AccessibilityStatus.read(this@MainActivity).summary
                nextAccessCheck = now + 2000
            }
            logView.text = RuntimeState.logText().ifBlank { "运行记录会显示在这里。" }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = savedInstanceState?.getBoolean("pending_preview", true) ?: true
        pickerPending = savedInstanceState?.getBoolean("picker_pending", false) ?: false
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(24))
            setBackgroundColor(Color.rgb(244, 246, 250))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        fun text(value: String, size: Float = 15f): TextView = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.rgb(29, 39, 56))
            setPadding(0, dp(8), 0, dp(8))
            content.addView(this)
        }
        fun button(label: String, action: () -> Unit) {
            content.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5) })
        }
        text("Phigros Script · Alpha", 28f)
        text("读取本机或导入 APK 的谱面，悬浮窗识别后确认演奏。")
        statusView = text("", 16f)
        accessibilityView = text(AccessibilityStatus.read(this).summary, 14f)
        button("扫描本机谱库") { scan() }
        button("导入 Phigros APK 解析谱面") { chooseApks() }
        button("通过系统设置开启无障碍") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("通过 Shizuku 启用本服务") { enableAccessibilityWithShizuku() }
        button("无障碍自查 / 复制诊断") { accessibilityHelp() }
        button("记录无障碍关闭原因（Shizuku）") { captureAccessibilityFailure() }
        button("连接 Shizuku") {
            try { ApkAccess.requestPermission() }
            catch (e: Exception) { message("Shizuku：" + e.message) }
        }
        text("启动悬浮窗 → 识别曲名和难度 → 窗内确认演奏 → 隐藏悬浮窗 → 暂停、恢复并重新对齐。只识别模式不会发送触摸。")
        button("悬浮窗只识别") { startSession(true) }
        button("启动悬浮窗识别 / 确认演奏") { startSession(false) }
        button("停止") {
            if (RuntimeState.running.get())
                startService(Intent(this, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_STOP))
        }
        text("运行时保持游戏横屏。若曲名未出现，可手动暂停帮助识别。演奏时悬浮窗隐藏，通知栏可停止；启用无障碍后，音量减键也可停止。")
        button("校准暂停键、识别区域与延迟") { calibration() }
        button("修正曲名识别") { editAlias() }
        text("这是 Alpha 版本。部分开头或演出谱面无法视觉对齐；无障碍触控不能保证全连。")
        logView = text("", 12f).apply { setTextIsSelectable(true) }
        ApkAccess.attach(applicationContext) { RuntimeState.log(it) }
        restoreImportedLibrary()
        if (Build.VERSION.SDK_INT >= 33)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun scan(after: (() -> Unit)? = null) {
        if (scanning || RuntimeState.running.get() || Diagnostics.isCapturing || AccessibilityActivation.isRunning) {
            message("请先停止当前任务")
            return
        }
        scanning = true
        worker.execute {
            val result = runCatching {
                ChartLibrary.scan(applicationContext, RuntimeState::log)
            }
            runOnUiThread {
                result.onSuccess { RuntimeState.library = it }
                scanning = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onSuccess {
                    RuntimeState.library = it
                    after?.invoke()
                }.onFailure { offerApkImport("扫描失败：" + it.message) }
            }
        }
    }

    private fun taskBusy(): Boolean = scanning || RuntimeState.running.get() ||
        Diagnostics.isCapturing || AccessibilityActivation.isRunning

    private fun chooseApks() {
        if (taskBusy()) { message("请先停止当前任务"); return }
        AlertDialog.Builder(this).setTitle("导入 Phigros APK")
            .setMessage("选择与你手机游戏版本一致的 Phigros 安装包。拆分安装包请同时选择 base.apk 和资源分包。文件会复制到本应用中并在手机上解析，无需上传到服务器。\n\n不支持直接导入 APKS/XAPK 容器；请先解压，再选择其中的 APK 文件。")
            .setNegativeButton("取消", null)
            .setPositiveButton("选择 APK 文件") { _, _ ->
                if (!taskBusy()) {
                    try {
                        pickerPending = true
                        apkPicker.launch(arrayOf("*/*"))
                    } catch (e: RuntimeException) {
                        pickerPending = false
                        message("无法打开文件选择器：" + e.message)
                    }
                }
            }.show()
    }

    private fun importApks(uris: List<Uri>) {
        if (taskBusy()) { message("当前有任务进行中，未导入文件"); return }
        scanning = true
        val app = applicationContext
        worker.execute {
            val result = runCatching { ChartLibrary.importApks(app, uris, RuntimeState::log) }
            runOnUiThread {
                result.onSuccess { RuntimeState.library = it }
                scanning = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onSuccess {
                    message("APK 谱库已导入，共 " + it.locations.size + " 个难度；现在可启动悬浮窗识别")
                }.onFailure { message("导入失败，未替换原有谱库：" + it.message) }
            }
        }
    }

    private fun restoreImportedLibrary() {
        if (pickerPending || RuntimeState.library != null || taskBusy()) return
        scanning = true
        val app = applicationContext
        worker.execute {
            val result = runCatching { ChartLibrary.scanImported(app, RuntimeState::log) }
            runOnUiThread {
                result.onSuccess { library ->
                    if (library != null && RuntimeState.library == null) RuntimeState.library = library
                }
                scanning = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onFailure { message("保存的 APK 谱库暂不可用，请重新导入：" + it.message) }
            }
        }
    }

    private fun offerApkImport(reason: String) {
        message(reason)
        AlertDialog.Builder(this).setTitle("可改用 APK 文件解析")
            .setMessage("无法读取本机安装资源时，可选择同版本的 Phigros APK 在本机解析。\n\n" + reason)
            .setNegativeButton("稍后", null)
            .setPositiveButton("选择 APK") { _, _ -> chooseApks() }
            .show()
    }

    private fun startSession(onlyRecognize: Boolean) {
        if (scanning || RuntimeState.running.get() || Diagnostics.isCapturing || AccessibilityActivation.isRunning) { message("请先等待当前任务完成或停止运行"); return }
        try { preferences.snapshot() }
        catch (e: Exception) { message("校准参数错误：" + e.message); return }
        if (!onlyRecognize && !AccessibilityStatus.read(this).ready) {
            message(AccessibilityStatus.read(this).summary + "；请用主页的 Shizuku 按钮或系统设置启用")
            return
        }
        if (RuntimeState.library == null) { scan { startSession(onlyRecognize) }; return }
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("允许显示悬浮窗")
                .setMessage("悬浮窗用于显示识别结果和确认开始演奏。请允许本应用显示在其他应用上层，返回后再次点击启动。")
                .setNegativeButton("取消", null)
                .setPositiveButton("前往设置") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + packageName)))
                    } catch (e: RuntimeException) {
                        message("无法打开悬浮窗设置，请在系统应用权限中允许：" + e.message)
                    }
                }.show()
            return
        }
        preview = onlyRecognize
        val manager = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34)
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else manager.createScreenCaptureIntent()
        screenPermission.launch(intent)
    }

    private fun calibration() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        layout.addView(TextView(this).apply {
            text = "确认演奏后先暂停，再点击已校准的开始按钮并重新对齐。开始按钮默认位于屏幕正中间。"
        })
        val fields = linkedMapOf<String, EditText>()
        val definitions = listOf(
            Triple("view", "玩法视口 x,y,宽,高", "0,0,1,1"),
            Triple("pauseRegion", "暂停图标搜索区域 x,y,宽,高", "0,0,0.18,0.24"),
            Triple("doubleTapInterval", "双击暂停的间隔（毫秒，80–300）", "140"),
            Triple("restartPoint", "暂停后开始按钮中心 x,y", "0.5,0.5"),
            Triple("pauseSettle", "暂停后等待（毫秒，900–4000）", "1000"),
            Triple("title", "曲名识别区域 x,y,宽,高", "0,0,1,1"),
            Triple("difficulty", "当前难度区域 x,y,宽,高", "0,0,1,1"),
            Triple("captureLag", "截图延迟补偿（毫秒）", "0"),
            Triple("touchOffset", "触摸偏移（正数更晚，毫秒）", "0")
        )
        definitions.forEach { (key, label, fallback) ->
            layout.addView(TextView(this).apply { text = label })
            val edit = EditText(this).apply {
                setSingleLine()
                setText(preferences.text(key, fallback))
            }
            fields[key] = edit
            layout.addView(edit)
        }
        layout.addView(TextView(this).apply {
            text = "区域使用屏幕比例，例如下半屏为 0,0.5,1,0.5。玩法视口不含黑边。" +
                "暂停图标默认在左上角搜索；右上角可设 0.82,0,0.18,0.24。" +
                "暂停后开始按钮默认点击 0.5,0.5（屏幕正中间），只点击一次，不依赖按钮文字。"
        })
        val dialog = AlertDialog.Builder(this).setTitle("设备校准")
            .setView(ScrollView(this).apply { addView(layout) })
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    listOf("view", "title", "difficulty", "pauseRegion").forEach {
                        AppSettings.rect(fields.getValue(it).text.toString())
                    }
                    listOf("captureLag", "touchOffset").forEach {
                        require(fields.getValue(it).text.toString().trim().toInt() in -500..500) {
                            "延迟范围为 -500 到 500 毫秒"
                        }
                    }
                    require(fields.getValue("doubleTapInterval").text.toString().trim().toInt() in 80..300) {
                        "双击间隔范围为 80 到 300 毫秒"
                    }
                    AppSettings.point(fields.getValue("restartPoint").text.toString())
                    require(fields.getValue("pauseSettle").text.toString().trim().toInt() in 900..4000) {
                        "暂停后等待范围为 900 到 4000 毫秒"
                    }
                    fields.forEach { (key, field) -> preferences.save(key, field.text.toString().trim()) }
                    dialog.dismiss()
                    message("已保存，下次启动识别时生效")
                } catch (e: Exception) { message(e.message ?: "参数不正确") }
            }
        }
        dialog.show()
    }

    private fun editAlias() {
        val songs = RuntimeState.library?.songs().orEmpty()
        if (songs.isEmpty()) { message("请先扫描谱库"); return }
        AlertDialog.Builder(this).setTitle("选择歌曲")
            .setItems(songs.map { it.title }.toTypedArray()) { _, index ->
                val song = songs[index]
                val edit = EditText(this).apply {
                    setSingleLine()
                    setText(preferences.alias(song.id).ifBlank { song.title })
                }
                AlertDialog.Builder(this).setTitle("游戏中显示的曲名")
                    .setMessage("资源 ID：" + song.id)
                    .setView(edit).setNegativeButton("取消", null)
                    .setPositiveButton("保存") { _, _ ->
                        preferences.setAlias(song.id, edit.text.toString())
                        message("已保存识别名称")
                    }.show()
            }.show()
    }

    private fun enableAccessibilityWithShizuku() {
        if (RuntimeState.running.get() || scanning || Diagnostics.isCapturing ||
            AccessibilityActivation.isRunning) {
            message("请先等待当前任务结束或停止演奏")
            return
        }
        if (!ApkAccess.isConnected) {
            message("请先连接 Shizuku 并授权，等待显示已连接")
            return
        }
        AlertDialog.Builder(this).setTitle("通过 Shizuku 启用本服务")
            .setMessage("使用你已授权的 Shizuku，启用 Phigros Script 的无障碍触控，并保留列表中已有的服务。\n\n" +
                "这是一次性操作，随后会在本页核对连接 10 秒。若系统再次关闭服务，应用会报告结果，不会在后台反复开启。" +
                "你仍可随时在系统设置中关闭本服务。\n\n" +
                "此入口用于避开返回无障碍列表时关闭的问题，尚未在你的系统上验证效果。" +
                "操作期间请勿同时切换其他无障碍服务；若检测到设置变化，本次会停止。")
            .setNegativeButton("取消", null)
            .setPositiveButton("仅启用本服务一次") { _, _ ->
                if (!AccessibilityActivation.start(applicationContext))
                    message("当前有任务进行中，请稍后再试")
            }.show()
    }
    private fun captureAccessibilityFailure() {
        if (RuntimeState.running.get() || scanning || Diagnostics.isCapturing || AccessibilityActivation.isRunning) {
            message("请先等待当前任务完成或停止演奏")
            return
        }
        if (!ApkAccess.isConnected) {
            message("请先点击“连接 Shizuku”，授权并等待连接后再记录")
            return
        }
        AlertDialog.Builder(this).setTitle("记录无障碍关闭原因")
            .setMessage("接下来会打开系统无障碍设置。请重新开启本应用的服务，然后退出设置回到这里。\n\n" +
                "使用 Shizuku 进行一次约 30 秒的只读采集，记录系统中提及本应用或 UID 的无障碍/权限日志，以及本应用的两项 AppOps 查询结果。" +
                "匹配的系统日志可能包含其他应用名称或标识，复制前可查看内容。结果仅保存在本机，下一次采集会覆盖，不会自动上传。\n\n" +
                "出现“系统诊断采集结束”后，点“无障碍自查 / 复制诊断”。系统可能不提供撤销来源，采集不保证能找到原因。")
            .setNegativeButton("取消", null)
            .setPositiveButton("开始并打开设置") { _, _ ->
                if (Diagnostics.startSystemCapture(applicationContext)) {
                    try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    catch (e: RuntimeException) { message("无法打开设置，请手动打开：" + e.message) }
                } else message("系统诊断已在采集中")
            }.show()
    }
    private fun accessibilityHelp() {
        val report = Diagnostics.report(this)
        val guidance = "如果系统开关仍开着但未连接，可关闭再开启本服务。" +
            "如果系统提示受限制的设置，在应用详情中允许；若系统自动关闭，请复制诊断查看异常。" +
            "若已经允许但仍自动关闭，可返回主页点“记录无障碍关闭原因（Shizuku）”采集一次系统线索。\n\n"
        AlertDialog.Builder(this).setTitle("无障碍自查").setMessage(guidance + report)
            .setNeutralButton("复制诊断") { _, _ ->
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Phigros Script 诊断", report))
                Toast.makeText(this, "诊断已复制", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("系统设置") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("关闭", null).show()
    }
    override fun onResume() {
        super.onResume()
        if (::accessibilityView.isInitialized)
            accessibilityView.text = AccessibilityStatus.read(this).summary
        nextAccessCheck = 0L
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }
    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
    private fun message(value: String) {
        RuntimeState.log(value)
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_preview", preview)
        outState.putBoolean("picker_pending", pickerPending)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        super.onDestroy()
    }
}
