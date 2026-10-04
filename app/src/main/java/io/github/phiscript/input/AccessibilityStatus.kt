package io.github.phiscript.input

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object AccessibilityStatus {
    data class Snapshot(val systemEnabled: Boolean?, val serviceEnabled: Boolean?,
                        val managerEnabled: Boolean?, val connected: Boolean) {
        val summary: String get() = when {
            connected -> "无障碍服务已连接"
            systemEnabled == false -> "系统无障碍总开关已关闭"
            serviceEnabled == true || managerEnabled == true -> "系统已启用，服务尚未连接"
            serviceEnabled == false && managerEnabled == false -> "系统未启用本应用的无障碍服务"
            else -> "无法确定系统开关状态，服务尚未连接"
        }
        override fun toString() = summary + "\nsystemEnabled=" + systemEnabled + " serviceEnabled=" +
            serviceEnabled + " managerEnabled=" + managerEnabled + " connected=" + connected
    }
    fun read(context: Context): Snapshot {
        val component = ComponentName(context, TouchService::class.java)
        val system = runCatching { Settings.Secure.getInt(context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 }.getOrNull()
        val enabled = runCatching {
            (Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .split(':').any { ComponentName.unflattenFromString(it) == component }
        }.getOrNull()
        val manager = runCatching {
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info -> info.resolveInfo.serviceInfo.let { ComponentName(it.packageName, it.name) == component } }
        }.getOrNull()
        return Snapshot(system, enabled, manager, TouchService.current != null)
    }
}
