package io.github.phiscript.assets

import org.junit.Assert.*
import org.junit.Test

class SelfAccessibilityToggleTest {
    private val target = SelfAccessibilityToggle.COMPONENT
    private val other = "example.other/.Assist"

    @Test fun missingAndEmptyListsAppendOnlyOurService() {
        for (initial in listOf<String?>(null, "")) {
            val port = FakePort(initial, "1")
            val result = SelfAccessibilityToggle.enable(port)
            assertTrue(result.settingsEnabled)
            assertEquals(target, port.services)
            assertEquals(listOf("services" to target), port.writes)
        }
    }

    @Test fun fullAndShortNamesAlreadyEnabledAreNotRewritten() {
        for (name in listOf(target, "io.github.phiscript/.input.TouchService")) {
            val port = FakePort(other + ":" + name, "1")
            assertTrue(SelfAccessibilityToggle.enable(port).settingsEnabled)
            assertTrue(port.writes.isEmpty())
        }
    }

    @Test fun existingTextOrderAndDuplicateEntriesArePreserved() {
        val original = other + ":sample.app/sample.app.Reader:" + other + ":"
        val port = FakePort(original, "1")
        assertTrue(SelfAccessibilityToggle.enable(port).settingsEnabled)
        assertEquals(original + target, port.services)
        assertEquals(1, port.writes.size)
    }

    @Test fun changedBaselineAbortsWithoutWriting() {
        val port = FakePort(other, "1")
        port.beforeRead = { fake, count ->
            if (count == 2) fake.services = "another.app/.Service"
        }
        assertFalse(SelfAccessibilityToggle.enable(port).settingsEnabled)
        assertTrue(port.writes.isEmpty())
    }

    @Test fun globalZeroIsEnabledAfterAddingOurService() {
        val port = FakePort(other, "0")
        val result = SelfAccessibilityToggle.enable(port)
        assertTrue(result.settingsEnabled)
        assertEquals(listOf("services" to other + ":" + target, "enabled" to "1"), port.writes)
        assertEquals("1", port.enabled)
    }

    @Test fun alreadyListedServiceOnlyNeedsGlobalWrite() {
        val port = FakePort("io.github.phiscript/.input.TouchService", "0")
        assertTrue(SelfAccessibilityToggle.enable(port).settingsEnabled)
        assertEquals(listOf("enabled" to "1"), port.writes)
    }

    @Test fun rejectedListWriteIsNotRetriedOrRolledBack() {
        val port = FakePort(other, "0")
        port.refuseList = true
        val result = SelfAccessibilityToggle.enable(port)
        assertFalse(result.settingsEnabled)
        assertTrue(result.listWriteAttempted)
        assertFalse(result.globalWriteAttempted)
        assertTrue(result.message.contains("可能已有设置写入"))
        assertEquals(1, port.writes.size)
        assertEquals(other, port.services)
        assertEquals("0", port.enabled)
    }

    @Test fun systemRemovingOurServiceStopsBeforeGlobalWrite() {
        val port = FakePort(other, "0")
        port.afterListWrite = { it.services = other }
        val result = SelfAccessibilityToggle.enable(port)
        assertFalse(result.settingsEnabled)
        assertFalse(result.globalWriteAttempted)
        assertEquals(1, port.writes.size)
        assertEquals(other, port.services)
    }

    @Test fun rejectedGlobalWriteKeepsListAndDoesNotRollback() {
        val port = FakePort(other, "0")
        port.refuseGlobal = true
        val result = SelfAccessibilityToggle.enable(port)
        assertFalse(result.settingsEnabled)
        assertTrue(result.listWriteAttempted)
        assertTrue(result.globalWriteAttempted)
        assertEquals(2, port.writes.size)
        assertEquals(other + ":" + target, port.services)
        assertEquals("0", port.enabled)
    }

    @Test fun concurrentGlobalDisableIsNotOverridden() {
        val port = FakePort(other, "1")
        port.afterListWrite = { it.enabled = "0" }
        val result = SelfAccessibilityToggle.enable(port)
        assertFalse(result.settingsEnabled)
        assertFalse(result.globalWriteAttempted)
        assertEquals("0", port.enabled)
        assertEquals(1, port.writes.size)
    }

    @Test fun lostOtherComponentIsDetectedWithoutRestoringWholeList() {
        val port = FakePort(other, "1")
        port.afterListWrite = { it.services = target }
        assertFalse(SelfAccessibilityToggle.enable(port).settingsEnabled)
        assertEquals(target, port.services)
        assertEquals(1, port.writes.size)
    }

    @Test fun changeBeforeGlobalWriteStopsSecondWrite() {
        val port = FakePort(other, "0")
        port.beforeRead = { fake, count -> if (count == 4) fake.enabled = "1" }
        val result = SelfAccessibilityToggle.enable(port)
        assertFalse(result.settingsEnabled)
        assertFalse(result.globalWriteAttempted)
        assertEquals(1, port.writes.size)
    }

    @Test fun unrecognizedGlobalValueDoesNotCauseAnyWrite() {
        for (value in listOf<String?>(null, "", "2", "Error")) {
            val port = FakePort(other, value)
            assertFalse(SelfAccessibilityToggle.enable(port).settingsEnabled)
            assertTrue(port.writes.isEmpty())
        }
    }

    @Test fun unconfirmedGlobalWriteIsNeverRetried() {
        val port = FakePort(other, "0")
        port.afterGlobalWrite = { it.enabled = "0" }
        assertFalse(SelfAccessibilityToggle.enable(port).settingsEnabled)
        assertEquals(2, port.writes.size)
        assertEquals(1, port.writes.count { it.first == "enabled" })
    }

    @Test fun malformedOrOversizedListIsNotWrittenBack() {
        for (value in listOf("Error: access denied", "example.app/" + "名".repeat(12_000))) {
            val port = FakePort(value, "1")
            assertFalse(SelfAccessibilityToggle.enable(port).settingsEnabled)
            assertTrue(port.writes.isEmpty())
        }
    }

    private class FakePort(var services: String?, var enabled: String?) :
        SelfAccessibilityToggle.SettingsPort {
        val writes = mutableListOf<Pair<String, String>>()
        var beforeRead: ((FakePort, Int) -> Unit)? = null
        var afterListWrite: ((FakePort) -> Unit)? = null
        var afterGlobalWrite: ((FakePort) -> Unit)? = null
        var refuseList = false
        var refuseGlobal = false
        private var readCount = 0

        override fun readServices(): String? {
            readCount++
            beforeRead?.invoke(this, readCount)
            return services
        }

        override fun readEnabled(): String? = enabled

        override fun writeServices(value: String) {
            writes.add("services" to value)
            if (refuseList) throw SecurityException("系统拒绝写入。")
            services = value
            afterListWrite?.invoke(this)
        }

        override fun writeEnabled(value: String) {
            writes.add("enabled" to value)
            if (refuseGlobal) throw SecurityException("系统拒绝写入。")
            enabled = value
            afterGlobalWrite?.invoke(this)
        }
    }
}
