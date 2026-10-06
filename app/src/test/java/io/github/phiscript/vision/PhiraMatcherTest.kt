package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test
class PhiraMatcherTest {
    private val in15 = PhiraCandidate("a", "My Song", "IN 15")
    private val hd10 = PhiraCandidate("b", "My Song", "HD 10")
    @Test fun requiresTitleAndCustomLevel() {
        assertEquals(in15, PhiraMatcher.match(listOf("My Song"), listOf("IN 15"), listOf(in15, hd10)))
        assertNull(PhiraMatcher.match(listOf("My Song"), emptyList(), listOf(in15)))
        assertNull(PhiraMatcher.match(listOf("Other"), listOf("IN 15"), listOf(in15)))
    }
    @Test fun customLevelCanContainSigns() {
        val c = PhiraCandidate("c", "Hello", "SP 20+")
        assertEquals(c, PhiraMatcher.match(listOf("Hello"), listOf("Level: SP 20+"), listOf(c)))
    }
    @Test fun duplicateCandidatesStayAmbiguous() {
        assertNull(PhiraMatcher.match(listOf("My Song"), listOf("IN15"), listOf(in15, in15.copy(id = "c"))))
        assertNull(PhiraMatcher.match(listOf("My Song"), listOf("IN 15", "HD 10"), listOf(in15, hd10)))
    }
    @Test fun splitWordsAndCombinedTextWork() {
        assertEquals(in15, PhiraMatcher.match(listOf("My", "Song"), listOf("IN", "15"), listOf(in15)))
        assertEquals(in15, PhiraMatcher.match(listOf("My Song IN 15"), emptyList(), listOf(in15)))
    }
    @Test fun decimalIsDifferentFromInteger() {
        val d = in15.copy(id = "d", level = "IN15.5"); val i = in15.copy(id = "i", level = "IN155")
        assertEquals(d, PhiraMatcher.match(listOf("My Song"), listOf("IN15.5"), listOf(d, i)))
        assertNull(PhiraMatcher.match(listOf("My Song"), listOf("IN15"), listOf(d)))
    }
    @Test fun missingOrWrongMetadataDoesNotGuess() {
        assertNull(PhiraMatcher.match(listOf("My Song"), listOf("IN15"), listOf(in15.copy(level = ""))))
        assertNull(PhiraMatcher.match(listOf("My Song"), listOf("IN15"), listOf(in15.copy(title = ""))))
        assertNull(PhiraMatcher.match(listOf("My S0ng"), listOf("IN15"), listOf(in15)))
    }
    @Test fun aliasStillRequiresLevel() {
        val c = in15.copy(aliases = listOf("别名"))
        assertEquals(c, PhiraMatcher.match(listOf("别名"), listOf("IN15"), listOf(c)))
        assertNull(PhiraMatcher.match(listOf("别名"), listOf("HD10"), listOf(c)))
    }
}
