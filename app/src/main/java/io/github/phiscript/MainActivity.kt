package io.github.phiscript

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.phiscript.assets.ApkAccess
import io.github.phiscript.assets.ChartLibrary
import io.github.phiscript.capture.CaptureService
import io.github.phiscript.input.TouchService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private var scanning = false
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
    private val refresh = object : Runnable {
        override fun run() {
            if (!::statusView.isInitialized) return
            val library = RuntimeState.library
            statusView.text = when {
                RuntimeState.running.get() -> "识别运行中 · 可在通知栏停止"
                scanning -> "正在扫描安装包…"
                library != null -> "谱库就绪 · " + library.snapshot.versionName +
                    " · " + library.locations.size + " 个难度"
                else -> "等待扫描 Phigros 安装包"
            }
            logView.text = RuntimeState.logText().ifBlank { "运行记录会显示在这里。" }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = savedInstanceState?.getBoolean("pending_preview", true) ?: true
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
        text("Phigros Script", 28f)
        text("从已安装的游戏读取谱面，在手机上识别和演奏。")
        statusView = text("", 16f)
        button("扫描本机谱库") { scan() }
        button("开启无障碍触控") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("连接 Shizuku（读取受限时使用）") {
            try { ApkAccess.requestPermission() }
            catch (e: Exception) { message("Shizuku：" + e.message) }
        }
        text("先使用“只识别”检查曲名、难度与时间对齐。自动演奏需要无障碍权限。")
        button("只识别，不点击") { startSession(true) }
        button("开始自动演奏") { startSession(false) }
        button("停止") {
            if (RuntimeState.running.get())
                startService(Intent(this, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_STOP))
        }
        text("运行时保持游戏横屏。通知栏可停止；启用无障碍后，音量减键也可停止。")
        button("校准识别区域与延迟") { calibration() }
        button("修正曲名识别") { editAlias() }
        text("这是实验版。部分开头或演出谱面无法视觉对齐；无障碍触控不能保证全连。")
        logView = text("", 12f).apply { setTextIsSelectable(true) }
        ApkAccess.attach(applicationContext) { RuntimeState.log(it) }
        handler.post(refresh)
        if (Build.VERSION.SDK_INT >= 33)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun scan(after: (() -> Unit)? = null) {
        if (scanning || RuntimeState.running.get()) {
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
                if (isDestroyed || isFinishing) return@runOnUiThread
                scanning = false
                result.onSuccess {
                    RuntimeState.library = it
                    after?.invoke()
                }.onFailure { message("扫描失败：" + it.message) }
            }
        }
    }

    private fun startSession(onlyRecognize: Boolean) {
        if (RuntimeState.running.get()) { message("请先停止当前任务"); return }
        try { preferences.snapshot() }
        catch (e: Exception) { message("校准参数错误：" + e.message); return }
        if (!onlyRecognize && TouchService.current == null) {
            message("请先开启 Phigros 多指触控无障碍服务")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (RuntimeState.library == null) { scan { startSession(onlyRecognize) }; return }
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
        val fields = linkedMapOf<String, EditText>()
        val definitions = listOf(
            Triple("view", "玩法视口 x,y,宽,高", "0,0,1,1"),
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
                "选曲页同时出现多种难度时，需要限制到当前难度区域。"
        })
        val dialog = AlertDialog.Builder(this).setTitle("设备校准")
            .setView(ScrollView(this).apply { addView(layout) })
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    listOf("view", "title", "difficulty").forEach {
                        AppSettings.rect(fields.getValue(it).text.toString())
                    }
                    listOf("captureLag", "touchOffset").forEach {
                        require(fields.getValue(it).text.toString().trim().toInt() in -500..500) {
                            "延迟范围为 -500 到 500 毫秒"
                        }
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

    private fun message(value: String) {
        RuntimeState.log(value)
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_preview", preview)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        worker.shutdownNow()
        super.onDestroy()
    }
}
