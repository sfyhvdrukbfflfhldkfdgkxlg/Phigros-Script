package io.github.phiscript.input
import org.junit.Assert.*
import org.junit.Test
class VisualTouchStateTest {
    private fun touch(id: Long = 1, kind: VisualTouchKind = VisualTouchKind.TAP,
        x: Float = 0.5f, y: Float = 0.5f) = VisualTouch(id, kind, x, y)
    private fun frame(time: Long = 100, vararg touches: VisualTouch) =
        VisualTouchFrame(time, 1920, 1080, touches.toList())
    @Test fun repeatedVisibleTapFiresOnce() {
        val state = VisualTouchState()
        assertEquals(1, state.update(frame(100, touch()), 100).shots.size)
        assertTrue(state.update(frame(100, touch()), 120).shots.isEmpty())
        assertTrue(state.update(frame(150, touch()), 150).shots.isEmpty())
    }
    @Test fun briefMissingFrameCannotRetapTheSameIdentity() {
        val state = VisualTouchState()
        state.update(frame(100, touch()), 100)
        state.update(frame(130), 130)
        assertTrue(state.update(frame(160, touch()), 160).shots.isEmpty())
    }
    @Test fun separateFlickIdentitiesEachFireOnce() {
        val state = VisualTouchState()
        assertEquals(listOf(1L), state.update(frame(100, touch(1, VisualTouchKind.FLICK)), 100).shots.map { it.id })
        assertEquals(listOf(2L), state.update(frame(130,
            touch(1, VisualTouchKind.FLICK), touch(2, VisualTouchKind.FLICK)), 130).shots.map { it.id })
    }
    @Test fun holdsKeepMovingInsteadOfBeingDeduplicated() {
        val state = VisualTouchState()
        assertEquals(0.4f, state.update(frame(100, touch(kind = VisualTouchKind.HOLD, x = 0.4f)), 100).holds.single().x)
        assertEquals(0.6f, state.update(frame(130, touch(kind = VisualTouchKind.HOLD, x = 0.6f)), 130).holds.single().x)
        assertTrue(state.update(frame(160), 160).holds.isEmpty())
    }
    @Test fun dragAndTapCanCoexistWithIndependentLifetime() {
        val state = VisualTouchState()
        val first = state.update(frame(100, touch(1, VisualTouchKind.DRAG), touch(2)), 100)
        assertEquals(listOf(1L), first.holds.map { it.id })
        assertEquals(listOf(2L), first.shots.map { it.id })
        val second = state.update(frame(130, touch(1, VisualTouchKind.DRAG), touch(2)), 130)
        assertEquals(1, second.holds.size)
        assertTrue(second.shots.isEmpty())
    }
    @Test(expected = IllegalStateException::class) fun staleFramesStopInsteadOfMaintainingBlindHolds() {
        VisualTouchState().update(frame(100, touch(kind = VisualTouchKind.HOLD)), 451)
    }
    @Test(expected = IllegalStateException::class) fun futureTimestampIsRejected() {
        VisualTouchState().update(frame(101, touch()), 100)
    }
    @Test(expected = IllegalStateException::class) fun olderFrameCannotReplaceNewerContacts() {
        val state = VisualTouchState()
        state.update(frame(150, touch()), 150)
        state.update(frame(140, touch()), 160)
    }
    @Test(expected = IllegalStateException::class) fun changedScreenCannotReuseOldCoordinates() {
        val state = VisualTouchState()
        state.update(frame(100, touch()), 100)
        state.update(VisualTouchFrame(130, 1080, 1920, listOf(touch())), 130)
    }
    @Test(expected = IllegalArgumentException::class) fun conflictingDuplicateIdentityIsRejected() {
        VisualTouchState().update(frame(100, touch(), touch(kind = VisualTouchKind.HOLD)), 100)
    }
    @Test fun malformedCoordinatesAndVectorsAreRejected() {
        val invalid = listOf(touch(x = Float.NaN), touch(y = Float.POSITIVE_INFINITY), touch(x = -0.01f),
            touch(y = 1.01f), touch(id = -1), touch().copy(flickDx = Float.NaN), touch().copy(flickDy = 2f))
        invalid.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { VisualTouchState().update(frame(100, value), 100) }
        }
    }
    @Test(expected = IllegalArgumentException::class) fun excessiveSimultaneousContactsAreRejected() {
        VisualTouchState().update(frame(100, *(1L..11L).map { touch(it) }.toTypedArray()), 100)
    }
    @Test fun emptyFreshFrameReleasesAllDesiredContacts() {
        val state = VisualTouchState()
        state.update(frame(100, touch(kind = VisualTouchKind.HOLD)), 100)
        val empty = state.update(frame(130), 130)
        assertTrue(empty.holds.isEmpty())
        assertTrue(empty.shots.isEmpty())
    }
    @Test fun identityHistoryExpiresWithoutGrowingForAnEntireSong() {
        val state = VisualTouchState()
        state.update(frame(100, touch()), 100)
        assertEquals(1, state.update(frame(10101, touch()), 10101).shots.size)
    }
}
