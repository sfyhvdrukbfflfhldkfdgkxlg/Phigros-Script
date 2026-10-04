package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test

class SongMatcherTest {
    private val songs = listOf(SongCandidate("Rrharil.TeamGrimoire", "Rrhar'il"),
        SongCandidate("Spasmodic.姜米條", "痉挛", listOf("Spasmodic")))
    @Test fun matchesNormalizedTitleAndBoundedDifficulty() {
        assertEquals(Identity("Rrharil.TeamGrimoire", "Rrhar'il", "IN"),
            SongMatcher.match(listOf("ＲＲＨＡＲ’ＩＬ"), listOf("IN 15"), songs))
    }
    @Test fun allowsKnownAliasAndMultilineTitle() {
        assertEquals("Spasmodic.姜米條", SongMatcher.match(listOf("Spas", "modic"), listOf("AT"), songs)?.songId)
    }
    @Test fun matchesTitleAndLevelInOneOcrLine() {
        for (line in listOf("Rrhar'il IN15", "Rrhar'il IN 15", "IN15 Rrhar'il", "Rrhar'il IN"))
            assertEquals(line, Identity("Rrharil.TeamGrimoire", "Rrhar'il", "IN"),
                SongMatcher.match(listOf(line), listOf(line), songs))
        assertEquals("HD", SongMatcher.match(listOf("Rrharil"), listOf("难度：HD12"), songs)?.difficulty)
    }
    @Test fun rejectsMultipleSongsAndDifficultySelectors() {
        assertNull(SongMatcher.match(listOf("Rrharil", "痉挛"), listOf("IN"), songs))
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("EZ HD IN AT"), songs))
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("EZ HD IN15 AT"), songs))
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("IN", "HD"), songs))
    }
    @Test fun allowsOneOcrEditInLongNames() {
        for (text in listOf("Rrhari1", "Rrharilx", "Rrhril"))
            assertEquals(text, "Rrharil.TeamGrimoire", SongMatcher.match(listOf(text), listOf("IN"), songs)?.songId)
        assertNull(SongMatcher.match(listOf("RrhaXXl"), listOf("IN"), songs))
    }
    @Test fun rejectsShortApproximateNamesAndArbitrarySubstrings() {
        val short = listOf(SongCandidate("Short.Composer", "Short"))
        assertNull(SongMatcher.match(listOf("Sh0rt"), listOf("IN"), short))
        assertNull(SongMatcher.match(listOf("Now playing Rrharil by Composer"), listOf("IN"), songs))
    }
    @Test fun rejectsNumericOnlyApproximateNames() {
        assertNull(SongMatcher.match(listOf("123457"), listOf("IN"),
            listOf(SongCandidate("123456.Composer", "123456"))))
    }
    @Test fun rejectsAmbiguousAliasesAndExactVersusFuzzyConflicts() {
        assertNull(SongMatcher.match(listOf("Rrharil"), listOf("IN"),
            songs + SongCandidate("Other.Composer", "Other", listOf("Rrharil"))))
        val similar = listOf(SongCandidate("a", "ABCDEF"), SongCandidate("b", "ABCDEG"))
        assertNull(SongMatcher.match(listOf("ABCDEF"), listOf("IN"), similar))
        assertNull(SongMatcher.match(listOf("ABCDEH"), listOf("IN"), similar))
    }
    @Test fun duplicateAliasesOfOneSongDoNotCreateAmbiguity() {
        val aliases = listOf(SongCandidate("Rrharil.TeamGrimoire", "Rrhar'il", listOf("Rrharil", "ＲＲＨＡＲＩＬ")))
        assertEquals("Rrharil.TeamGrimoire", SongMatcher.match(listOf("Rrhari1"), listOf("IN"), aliases)?.songId)
    }
    @Test fun difficultyMustHaveWordBoundariesAndEvidence() {
        for (text in listOf("INFINITE", "Artist at home", "BEGIN15", "Origin15", "IN15notes"))
            assertNull(text, SongMatcher.match(listOf("Rrharil"), listOf(text), songs))
    }
    @Test fun keepsTitlesBeginningWithLevelLikeWords() {
        val names = listOf(SongCandidate("InTheEnd.Composer", "In The End"))
        assertEquals("InTheEnd.Composer", SongMatcher.match(listOf("In The End"), listOf("HD 12"), names)?.songId)
        assertNull(SongMatcher.match(listOf("In The End"), listOf("In The End"), names))
    }
    @Test fun pauseAndResultNeedDistinctiveText() {
        assertTrue(SongMatcher.isPauseOrResult(listOf("继续游戏")))
        assertTrue(SongMatcher.isPauseOrResult(listOf("Continue")))
        assertTrue(SongMatcher.isPauseOrResult(listOf("MAX COMBO", "ACCURACY")))
        assertTrue(SongMatcher.isResult(listOf("MAX COMBO 123", "ACCURACY 98.12%")))
        assertFalse(SongMatcher.isPauseOrResult(listOf("COMBO", "Rrharil", "IN")))
        assertFalse(SongMatcher.isPauseOrResult(listOf("MAX COMBO")))
    }
}
