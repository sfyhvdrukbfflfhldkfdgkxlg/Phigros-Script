package io.github.phiscript

import android.content.Context
import android.graphics.RectF

data class SessionSettings(
    val viewport: RectF,
    val titleRoi: RectF,
    val difficultyRoi: RectF,
    val captureLagMs: Int,
    val touchOffsetMs: Int
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun text(key: String, fallback: String): String = prefs.getString(key, fallback) ?: fallback
    fun save(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun alias(id: String): String = text("alias:" + id, "")
    fun setAlias(id: String, value: String) = save("alias:" + id, value.trim())
    fun snapshot() = SessionSettings(
        rect(text("view", "0,0,1,1")),
        rect(text("title", "0,0,1,1")),
        rect(text("difficulty", "0,0,1,1")),
        text("captureLag", "0").toInt().also { require(it in -500..500) },
        text("touchOffset", "0").toInt().also { require(it in -500..500) }
    )
    companion object {
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
