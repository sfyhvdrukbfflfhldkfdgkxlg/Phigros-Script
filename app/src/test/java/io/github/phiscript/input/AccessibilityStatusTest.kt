package io.github.phiscript.input

import org.junit.Assert.*
import org.junit.Test

class AccessibilityStatusTest {
    @Test fun removedAuthorizationIsNotReadyDuringUnbind() {
        val snapshot = AccessibilityStatus.Snapshot(true, false, false, true)
        assertFalse(snapshot.ready)
        assertTrue(snapshot.summary.contains("正在断开"))
    }

    @Test fun bindingAloneDoesNotOverrideManagerPendingState() {
        val snapshot = AccessibilityStatus.Snapshot(true, true, false, true)
        assertFalse(snapshot.ready)
        assertTrue(snapshot.summary.contains("等待系统确认"))
    }

    @Test fun connectedAndEnabledIsReady() {
        val snapshot = AccessibilityStatus.Snapshot(true, true, true, true)
        assertTrue(snapshot.ready)
        assertEquals("无障碍服务已连接", snapshot.summary)
    }

    @Test fun enabledButUnboundIsNotReady() {
        val snapshot = AccessibilityStatus.Snapshot(true, true, true, false)
        assertFalse(snapshot.ready)
        assertEquals("系统已启用，服务尚未连接", snapshot.summary)
    }

    @Test fun globalOffInvalidatesBoundInstance() {
        assertFalse(AccessibilityStatus.Snapshot(false, true, true, true).ready)
    }
}
