package io.github.phiscript.assets
import org.junit.Assert.*
import org.junit.Test
class PhiraImportMergeTest {
    private data class Revision(val title: String, val level: String, val fingerprint: String)
    private fun merge(old: List<Revision>, next: List<Revision>) =
        mergePhiraImports(old, next, { it.title to it.level }, { it.fingerprint })
    @Test fun exactNameAndLevelReplaceOldRevisions() {
        val old = listOf(Revision("Song", "IN15", "a"), Revision("Song", "IN15", "b")); val n = Revision("Song", "IN15", "new")
        assertEquals(listOf(n), merge(old, listOf(n))); assertEquals(2, old.size)
    }
    @Test fun otherTitlesAndLevelsRemain() {
        val h = Revision("Song", "HD10", "h"); val o = Revision("Other", "IN15", "o"); val n = Revision("Song", "IN15", "new")
        assertEquals(listOf(h, o, n), merge(listOf(Revision("Song", "IN15", "old"), h, o), listOf(n)))
    }
    @Test fun conflictingBatchPreservesOldLibrary() {
        val old = listOf(Revision("Kept", "IN15", "kept"))
        assertTrue(runCatching { merge(old, listOf(Revision("Song", "IN15", "a"), Revision("Song", "IN15", "b"))) }
            .exceptionOrNull() is IllegalArgumentException)
        assertEquals(listOf(Revision("Kept", "IN15", "kept")), old)
    }
    @Test fun identicalDocumentsDeduplicate() {
        val n = Revision("Song", "IN15", "a"); assertEquals(listOf(n), merge(emptyList(), listOf(n, n)))
    }
    @Test fun similarNamesRemainDistinct() {
        val o = Revision("Song", "IN15", "old"); val n = Revision("song", "IN15", "new")
        assertEquals(listOf(o, n), merge(listOf(o), listOf(n)))
    }
}
