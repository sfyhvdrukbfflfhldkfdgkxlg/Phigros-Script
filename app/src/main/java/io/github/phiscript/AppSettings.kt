package io.github.phiscript

import android.content.Context
import android.graphics.RectF
import io.github.phiscript.vision.NormalizedPoint

data class SessionSettings(
    val viewport: RectF,
    val titleRoi: RectF,
    val difficultyRoi: RectF,
    val captureLagMs: Int,
    val touchOffsetMs: Int,
    val pauseRoi: RectF = RectF(0f, 0f, 0.18f, 0.24f),
    val doubleTapIntervalMs: Int = 140,
    val restartPoint: NormalizedPoint = NormalizedPoint(0.5f, 0.5f),
    val pauseSettleMs: Int = 1000
)
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun text(key: String, fallback: String): String = prefs.getString(key, fallback) ?: fallback
    fun save(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun flag(key: String, fallback: Boolean): Boolean = prefs.getBoolean(key, fallback)
    fun saveFlag(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun alias(id: String): String = text("alias:" + id, "")
    fun setAlias(id: String, value: String) = save("alias:" + id, value.trim())
    fun snapshot() = SessionSettings(
        rect(text("view", "0,0,1,1")),
        rect(text("title", "0,0,1,1")),
        rect(text("difficulty", "0,0,1,1")),
        text("captureLag", "0").toInt().also { require(it in -500..500) },
        text("touchOffset", "0").toInt().also { require(it in -500..500) },
        rect(text("pauseRegion", "0,0,0.18,0.24")),
        text("doubleTapInterval", "140").toInt().also { require(it in 80..300) },
        point(text("restartPoint", "0.5,0.5")),
        text("pauseSettle", "1000").toInt().also { require(it in 900..4000) }
    )
    companion object {
        fun point(value: String): NormalizedPoint {
            val n = value.split(',').map { it.trim().toFloat() }
            require(n.size == 2 && n.all { it.isFinite() && it > 0f && it < 1f }) {
                "按钮位置格式为 x,y，两个数都须大于 0 且小于 1"
            }
            return NormalizedPoint(n[0], n[1])
        }
        fun rect(value: String): RectF {
            val n = value.split(',').map { it.trim().toFloat() }
            require(n.size == 4 && n.all { it.isFinite() }) { "区域格式为 x,y,宽,高" }
            require(n[0] >= 0 && n[1] >= 0 && n[2] > 0 && n[3] > 0 &&
                n[0] + n[2] <= 1f && n[1] + n[3] <= 1f) {
                "区域须处于 0 到 1 之间，并且不超出屏幕"
            }
            return RectF(n[0], n[1], n[0] + n[2], n[1] + n[3])
        }
    }
}
