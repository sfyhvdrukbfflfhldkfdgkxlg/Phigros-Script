package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test
class PhiraSelectionPolicyTest {
    private val first = PhiraCandidate("first", "自定义曲名", "SPECIAL 16+")
    private val second = PhiraCandidate("second", "Other", "IN 15")
    @Test fun automaticModeKeepsUniqueOcrRequirement() {
        val p = PhiraSelectionPolicy("", listOf(first, second))
        assertNull(p.choose(null, true)); assertEquals(second, p.choose(second, false))
    }
    @Test fun manualChoiceCannotOfferOnAnUnprovenScreen() {
        val p = PhiraSelectionPolicy("first", listOf(first, second))
        assertNull(p.choose(null, false)); assertNull(p.choose(first, false))
    }
    @Test fun provenGameplayCanUseManualChoiceWithoutReadableTitle() {
        val p = PhiraSelectionPolicy("first", listOf(first, second))
        assertEquals(first, p.choose(null, true)); assertEquals(first, p.choose(first, true))
    }
    @Test fun knownDifferentSongOrDifficultyCancelsManualChoice() {
        val p = PhiraSelectionPolicy("first", listOf(first, second))
        assertTrue(p.conflicts(second)); assertNull(p.choose(second, true)); assertNull(p.choose(first.copy(level = "OTHER 10"), true))
    }
    @Test(expected = IllegalStateException::class) fun missingManualChartRequiresReselection() {
        PhiraSelectionPolicy("deleted", listOf(first))
    }
    @Test(expected = IllegalStateException::class) fun duplicateManualIdsAreNotSelectedArbitrarily() {
        PhiraSelectionPolicy("first", listOf(first, first.copy(level = "Other")))
    }
}
