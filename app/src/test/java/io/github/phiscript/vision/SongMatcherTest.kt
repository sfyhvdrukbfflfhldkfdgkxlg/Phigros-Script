package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test

class SongMatcherTest {
    private val songs = listOf(
        SongCandidate("Rrharil.TeamGrimoire", "Rrhar'il"),
        SongCandidate("Spasmodic.姜米條", "痉挛", listOf("Spasmodic"))
    )
    @Test fun matchesNormalizedTitleAndBoundedDifficulty() {
        assertEquals(Identity("Rrharil.TeamGrimoire", "Rrhar'il", "IN"),
            SongMatcher.match(listOf("ＲＲＨＡＲ’ＩＬ"), listOf("IN 15"), songs))
    }
    @Test fun allowsKnownAliasAndMultilineTitle() {
        assertEquals("Spasmodic.姜米條",
            SongMatcher.match(listOf("Spas", "modic"), listOf("AT"), songs)?.songId)
    }
    @Test fun rejectsMultipleSongsAndDifficultySelectors() {
        assertNull(SongMatcher.match(listOf("Rrharil", "痉挛"), listOf("IN"), songs))
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("EZ HD IN AT"), songs))
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("IN", "HD"), songs))
    }
    @Test fun rejectsAmbiguousAliasesAndApproximateNames() {
        val duplicate = songs + SongCandidate("Other.Composer", "Other", listOf("Rrharil"))
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("IN"), duplicate))
        assertNull(SongMatcher.match(listOf("Rrhari1"), listOf("IN"), songs))
    }
    @Test fun difficultyMustBeAStandaloneLabel() {
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("INFINITE"), songs))
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("Artist at home"), songs))
    }
    @Test fun pauseAndResultNeedDistinctiveText() {
        assertTrue(SongMatcher.isPauseOrResult(listOf("继续游戏")))
        assertTrue(SongMatcher.isPauseOrResult(listOf("MAX COMBO", "ACCURACY")))
        assertFalse(SongMatcher.isPauseOrResult(listOf("COMBO", "Rrharil", "IN")))
        assertFalse(SongMatcher.isPauseOrResult(listOf("MAX COMBO")))
    }
}
