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
import android.widget.CheckBox
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
import io.github.phiscript.assets.PhiraLibrary
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
                TouchService.current?.selectGame(preferences.mode().gamePackage)
                packageManager.getLaunchIntentForPackage(preferences.mode().gamePackage)
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
    private val phiraPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pickerPending = false
        if (uris.isNotEmpty()) importPhira(uris.distinct())
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
                preferences.mode() == PlayMode.VISUAL -> "纯视觉模式 · 无需谱库 · 检测可见判定线与音符"
                RuntimeState.phiraLibrary != null -> "Phira 谱库就绪 · " +
                    RuntimeState.phiraLibrary!!.entries.size + " 个谱面"
                library != null -> "Phigros 谱库就绪 · " + library.sourceLabel + " · " + library.snapshot.versionName +
                    " · " + library.locations.size + " 个难度"
                else -> "请导入 Phira 谱面包（PEZ / ZIP / JSON）"
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
        text("纯视觉演奏 / Phira 谱面演奏 · 在悬浮窗确认后开始。")
        button("选择演奏模式") { chooseMode() }
        button("导入 Phira 谱面包") { choosePhira() }
        statusView = text("", 16f)
        accessibilityView = text(AccessibilityStatus.read(this).summary, 14f)
        button("扫描 Phigros APK 谱库（工具）") { scan() }
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
        text("纯视觉：检测游戏画面和判定线 → 确认 → 隐藏悬浮窗并实时演奏，无需曲名或谱面。Phira：识别导入谱面的曲名和难度 → 确认 → 暂停、点击中央重试后按谱面演奏。只识别模式不会发送触摸。")
        button("悬浮窗只识别") { startSession(true) }
        button("启动悬浮窗识别 / 确认演奏") { startSession(false) }
        button("停止") {
            if (RuntimeState.running.get())
                startService(Intent(this, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_STOP))
        }
        text("游戏须保持横屏。纯视觉在歌曲画面确认，可从当前音符开始；Phira 请使用 1 倍速、关闭镜像，并设置相同的游戏偏移。演奏时悬浮窗隐藏，通知栏及音量减键可停止。")
        button("设置识别区域、Phira 重试与延迟") { calibration() }
        button("修正曲名识别") { editAlias() }
        text("这是 Alpha 版本。纯视觉受截图延迟、音符遮挡、皮肤、隐藏或高速移动判定线影响；Phira 只支持可可靠解析的谱面。无障碍触控不能保证全连。")
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
        if (pickerPending || taskBusy() ||
            (RuntimeState.library != null && RuntimeState.phiraLibrary != null)) return
        scanning = true
        val app = applicationContext
        worker.execute {
            val apk = if (RuntimeState.library == null)
                runCatching { ChartLibrary.scanImported(app, RuntimeState::log) } else null
            val phira = if (RuntimeState.phiraLibrary == null)
                runCatching { PhiraLibrary.restore(app) } else null
            runOnUiThread {
                apk?.onSuccess { if (RuntimeState.library == null) RuntimeState.library = it }
                phira?.onSuccess { if (RuntimeState.phiraLibrary == null) RuntimeState.phiraLibrary = it }
                scanning = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                apk?.onFailure { message("保存的 APK 谱库暂不可用：" + it.message) }
                phira?.onFailure { message("保存的 Phira 谱库暂不可用，请重新导入：" + it.message) }
            }
        }
    }

    private fun chooseMode() {
        if (taskBusy()) { message("请先停止当前任务"); return }
        val modes = PlayMode.entries
        AlertDialog.Builder(this).setTitle("选择演奏模式")
            .setSingleChoiceItems(modes.map { it.label }.toTypedArray(), modes.indexOf(preferences.mode())) { dialog, index ->
                preferences.save("mode", modes[index].name)
                message("已选择 " + modes[index].label)
                dialog.dismiss()
            }.setNegativeButton("取消", null).show()
    }

    private fun choosePhira() {
        if (taskBusy()) { message("请先停止当前任务"); return }
        AlertDialog.Builder(this).setTitle("导入 Phira 谱面")
            .setMessage("选择从 Phira 导出的 PEZ / ZIP 谱面包，或 PGR / 支持的 RPE JSON。文件只在手机上解析。谱面包需要曲名和难度信息；PEC、PBC 及无法可靠解释的特效暂不支持。")
            .setNegativeButton("取消", null)
            .setPositiveButton("选择文件") { _, _ ->
                if (!taskBusy()) {
                    try { pickerPending = true; phiraPicker.launch(arrayOf("*/*")) }
                    catch (e: RuntimeException) { pickerPending = false; message("无法选择文件：" + e.message) }
                }
            }.show()
    }

    private fun importPhira(uris: List<Uri>) {
        if (taskBusy()) { message("请先停止当前任务"); return }
        scanning = true
        val app = applicationContext
        worker.execute {
            val result = runCatching { PhiraLibrary.importFiles(app, uris, RuntimeState::log) }
            runOnUiThread {
                result.onSuccess { RuntimeState.phiraLibrary = it }
                scanning = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onSuccess {
                    preferences.save("mode", PlayMode.PHIRA.name)
                    message("已导入 " + it.entries.size + " 个 Phira 谱面，已切换到 Phira 模式")
                }.onFailure { message("导入失败，原有 Phira 谱库保持可用：" + it.message) }
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
        if (preferences.mode() == PlayMode.PHIRA && RuntimeState.phiraLibrary == null) {
            message("请先导入 Phira 谱面包"); return
        }
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
            text = "纯视觉直接从当前画面演奏，不进行音符对齐。Phira 使用暂停菜单的中央重试键建立起始时钟；请使用 1 倍速、关闭镜像，并保持游戏全局偏移一致。"
        })
        val autoViewport = CheckBox(this).apply {
            text = "Phira 自动使用居中 16:9 玩法视口（默认开启）"
            isChecked = preferences.flag("phiraAutomaticViewport", true)
        }
        layout.addView(autoViewport)
        val fields = linkedMapOf<String, EditText>()
        val definitions = listOf(
            Triple("view", "玩法视口 x,y,宽,高", "0,0,1,1"),
            Triple("pauseRegion", "暂停图标搜索区域 x,y,宽,高", "0,0,0.18,0.24"),
            Triple("doubleTapInterval", "双击暂停的间隔（毫秒，80–300）", "140"),
            Triple("phiraPauseRegion", "Phira 暂停图标区域 x,y,宽,高", "0,0,0.18,0.24"),
            Triple("phiraRestartPoint", "Phira 中央重试键 x,y", "0.5,0.5"),
            Triple("phiraRestartDelay", "Phira 重试起始延迟（毫秒，0–5000）", "700"),
            Triple("phiraGlobalOffset", "Phira 游戏全局偏移（毫秒，-2000–2000）", "0"),
            Triple("pauseSettle", "Phira 暂停过渡等待（毫秒，900–4000）", "1000"),
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
                "Phira 若修改了游戏画面比例，请关闭自动视口并填写实际玩法视口。" +
                "暂停图标默认在左上角搜索；右上角可设 0.82,0,0.18,0.24。" +
                "Phira 默认重试起始延迟来自游戏的 700 毫秒开场动画，可按设备调整；谱面包自带偏移会自动计入。" +
                "纯视觉截图补偿使用 0–120 毫秒，触摸偏移使用 -80–80 毫秒，超出部分按边界处理。"
        })
        val dialog = AlertDialog.Builder(this).setTitle("设备校准")
            .setView(ScrollView(this).apply { addView(layout) })
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    listOf("view", "title", "difficulty", "pauseRegion", "phiraPauseRegion").forEach {
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
                    AppSettings.point(fields.getValue("phiraRestartPoint").text.toString())
                    require(fields.getValue("phiraRestartDelay").text.toString().trim().toInt() in 0..5000) {
                        "Phira 重试延迟范围为 0 到 5000 毫秒"
                    }
                    require(fields.getValue("phiraGlobalOffset").text.toString().trim().toInt() in -2000..2000) {
                        "Phira 全局偏移范围为 -2000 到 2000 毫秒"
                    }
                    require(fields.getValue("pauseSettle").text.toString().trim().toInt() in 900..4000) {
                        "暂停过渡等待范围为 900 到 4000 毫秒"
                    }
                    preferences.saveFlag("phiraAutomaticViewport", autoViewport.isChecked)
                    fields.forEach { (key, field) -> preferences.save(key, field.text.toString().trim()) }
                    dialog.dismiss()
                    message("已保存，下次启动识别时生效")
                } catch (e: Exception) { message(e.message ?: "参数不正确") }
            }
        }
        dialog.show()
    }

    private fun editAlias() {
        val songs = if (preferences.mode() == PlayMode.PHIRA)
            RuntimeState.phiraLibrary?.entries.orEmpty().map { Triple(it.id, it.title, it.level) }
            else RuntimeState.library?.songs().orEmpty().map { Triple(it.id, it.title, "") }
        if (songs.isEmpty()) { message("请先导入或扫描谱库"); return }
        AlertDialog.Builder(this).setTitle("选择谱面")
            .setItems(songs.map { it.second + " " + it.third }.toTypedArray()) { _, index ->
                val song = songs[index]
                val edit = EditText(this).apply {
                    setSingleLine()
                    setText(preferences.alias(song.first).ifBlank { song.second })
                }
                AlertDialog.Builder(this).setTitle("游戏中显示的曲名")
                    .setMessage("难度：" + song.third)
                    .setView(edit).setNegativeButton("取消", null)
                    .setPositiveButton("保存") { _, _ ->
                        preferences.setAlias(song.first, edit.text.toString())
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
