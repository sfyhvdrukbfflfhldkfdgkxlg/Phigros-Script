package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test

class PauseMenuTest {
    private fun label(text: String, top: Float = 0.4f) = TextRegion(text, 0.3f, top, 0.7f, top + 0.1f)
    private val retry = label("Retry", 0.6f)
    @Test fun locatesLocalizedResumeLabelsWithoutDeviceCoordinates() {
        for (text in listOf("继续", "继续游戏", "恢复游戏", "Resume", "Continue")) {
            val menu = SongMatcher.pauseMenu(listOf(label(text), retry))
            assertNotNull(text, menu); assertEquals(0.5f, menu!!.resume.x, 0.0001f)
            assertEquals(0.45f, menu.resume.y, 0.0001f)
        }
    }
    @Test fun requiresSeparatePauseMenuEvidence() {
        assertNull(SongMatcher.pauseMenu(listOf(label("Resume"))))
        assertNull(SongMatcher.pauseMenu(listOf(label("Resume"), label("Retry count", 0.6f))))
        assertNull(SongMatcher.pauseMenu(listOf(label("Continue playing"), retry)))
        assertNotNull(SongMatcher.pauseMenu(listOf(label("继续"), label("暂停", 0.2f))))
    }
    @Test fun mergesLineAndElementBoundsForTheSameButton() {
        val whole = TextRegion("继续游戏", 0.3f, 0.4f, 0.7f, 0.5f)
        val element = TextRegion("继续", 0.3f, 0.4f, 0.5f, 0.5f)
        val menu = SongMatcher.pauseMenu(listOf(whole, whole, element, retry))
        assertNotNull(menu); assertEquals(0.5f, menu!!.resume.x, 0.0001f)
    }
    @Test fun rejectsTwoSeparateResumeButtons() {
        assertNull(SongMatcher.pauseMenu(listOf(label("Resume"), label("Continue", 0.1f), retry)))
    }
    @Test fun resultEvidencePreventsResumeSelection() {
        assertNull(SongMatcher.pauseMenu(listOf(label("Continue"), retry,
            label("MAX COMBO", 0.1f), label("ACCURACY", 0.8f))))
    }
    @Test fun ignoresInvalidOrOutOfImageBounds() {
        val invalid = listOf(TextRegion("Resume", Float.NaN, 0.4f, 0.7f, 0.5f),
            TextRegion("Resume", -0.1f, 0.4f, 0.7f, 0.5f), TextRegion("Resume", 0.3f, 0.4f, 1.1f, 0.5f),
            TextRegion("Resume", 0.3f, 0.4f, 0.3f, 0.5f))
        for (region in invalid) assertNull(SongMatcher.pauseMenu(listOf(region, retry)))
    }
}
