package io.github.phiscript.assets

import org.junit.Assert.*
import org.junit.Test

class AccessibilityLogFilterTest {
    private val filter = AccessibilityLogFilter(10123)
    private fun line(tag: String, message: String, pid: Int = 1234): String =
        "10-05 03:52:12.123 " + pid + " 1235 I " + tag + ": " + message

    @Test fun exactPackageAndFlattenedComponentAreAccepted() {
        assertNotNull(filter.keep(line("AccessibilityManagerService", "Removed io.github.phiscript/.input.TouchService")))
        assertNotNull(filter.keep(line("AppOps", "setMode package=io.github.phiscript mode=ignore")))
        assertNotNull(filter.keep(line("AccessibilityManager", "Unbind io.github.phiscript.input.TouchService")))
    }

    @Test fun lookalikePackagesAndComponentsAreRejected() {
        for (name in listOf("io.github.phiscript2", "io.github.phiscript.debug", "xio.github.phiscript",
            "io.github.phiscript.input.TouchServiceExtra", "io.github.phiscript.input.OtherService")) {
            assertNull(name, filter.keep(line("AccessibilityManagerService", "Removed " + name)))
        }
    }

    @Test fun uidRequiresAnExplicitField() {
        for (message in listOf("reject uid=10123", "reject uid 10123", "reject uid:10123",
            "reject appUid = 10123", "reject targetUid: 10123")) {
            assertNotNull(message, filter.keep(line("PermissionManagerService", message)))
        }
        for (message in listOf("reject 10123", "pid=10123", "uid=101230", "uid=910123",
            "uid=10123x", "someuid=10123", "uid=10123.1")) {
            assertNull(message, filter.keep(line("PermissionManagerService", message)))
        }
    }

    @Test fun uidInThreadtimePidDoesNotMatch() {
        assertNull(filter.keep(line("AppOpsService", "unrelated mode change", pid = 10123)))
    }

    @Test fun onlyPolicyTagsMayMatchUidAlone() {
        assertNull(filter.keep(line("ActivityManager", "Killing uid=10123")))
        assertNull(filter.keep(line("SongIdentifier", "uid=10123 io.github.phiscript")))
        assertNull(filter.keep(line("SomeApp", "io.github.phiscript")))
        assertNotNull(filter.keep(line("VivoPermissionManagerService", "reject uid=10123")))
        assertNotNull(filter.keep(line("VivoAccessibilityManager", "unbind io.github.phiscript")))
    }

    @Test fun activityManagerRequiresLifecycleAndExactPackage() {
        assertNotNull(filter.keep(line("ActivityManager", "Force stopping io.github.phiscript appid=10123")))
        assertNotNull(filter.keep(line("ActivityManager", "Process io.github.phiscript has died")))
        assertNull(filter.keep(line("ActivityManager", "START io.github.phiscript activity")))
        assertNull(filter.keep(line("ActivityManager", "Killing io.github.phiscript2")))
    }

    @Test fun payloadsWithVisibleTextAreDroppedEvenForOurPackage() {
        for (payload in listOf("AccessibilityEvent type=32", "text=[private message]", "beforeText=secret",
            "contentDescription=secret", "extras={private}", "OCR result=secret", "ClipData=secret",
            "data=content://private", "dat=https://private.example")) {
            assertNull(payload, filter.keep(line("AccessibilityManagerService", "io.github.phiscript " + payload)))
        }
    }

    @Test fun malformedAndUnboundedLinesAreRejected() {
        assertNull(filter.keep("AccessibilityManagerService: io.github.phiscript"))
        assertNull(filter.keep(line("AccessibilityManagerService", "io.github.phiscript\u0000")))
        assertNull(filter.keep(line("AccessibilityManagerService", "io.github.phiscript " + "x".repeat(4096))))
    }

    @Test fun ringRetainsLatestLinesWithinItsByteBudget() {
        val buffer = BoundedUtf8Lines(8)
        buffer.append("abc")
        buffer.append("def")
        buffer.append("ghi")
        assertEquals("def\nghi\n", buffer.snapshot())
        assertEquals(1L, buffer.droppedLines)
        assertTrue(buffer.snapshot().toByteArray(Charsets.UTF_8).size <= 8)
    }

    @Test fun utf8BudgetHandlesChineseAndSupplementaryCharacters() {
        assertEquals("中", BoundedUtf8Lines.utf8Prefix("中文", 5))
        assertEquals("😀", BoundedUtf8Lines.utf8Prefix("😀文", 6))
        assertEquals("", BoundedUtf8Lines.utf8Prefix("😀", 3))
        val buffer = BoundedUtf8Lines(8)
        buffer.append("😀中文")
        assertEquals("😀中\n", buffer.snapshot())
        assertTrue(buffer.snapshot().toByteArray(Charsets.UTF_8).size <= 8)
        assertEquals(1L, buffer.droppedLines)
    }

    @Test fun hugeInputAndSustainedWritesCannotExceedTheBudget() {
        val buffer = BoundedUtf8Lines(1024)
        buffer.append("中".repeat(200_000))
        assertTrue(buffer.snapshot().toByteArray(Charsets.UTF_8).size <= 1024)
        repeat(10_000) { buffer.append("line " + it + " permission changed") }
        assertTrue(buffer.snapshot().toByteArray(Charsets.UTF_8).size <= 1024)
        assertTrue(buffer.snapshot().contains("line 9999 "))
        assertTrue(buffer.droppedLines > 0)
    }

    @Test fun minimumBudgetAndEmptyPrefixStayBounded() {
        val buffer = BoundedUtf8Lines(1)
        buffer.append("private payload")
        assertEquals("\n", buffer.snapshot())
        assertEquals("", BoundedUtf8Lines.utf8Prefix("test", 0))
    }
}
