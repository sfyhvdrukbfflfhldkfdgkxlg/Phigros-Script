package io.github.phiscript.vision
import io.github.phiscript.input.VisualTouchKind
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class VisualNoteTrackerTest {
    private val line = VisualJudgeLine(160.0, 100.0, 1.0, 0.0, 300.0, 2.0)
    private fun scene(y: Double, kind: Int = 1, extent: Double = 0.0) =
        VisualScene(320, 200, listOf(line), listOf(VisualNoteBlob(kind, 100.0, y, 0, 0.0, extent)))
    private fun cross(tracker: VisualNoteTracker, kind: Int = 1) =
        listOf(70.0, 80.0, 90.0, 100.0).mapIndexed { i, y ->
            tracker.observe(scene(y, kind), 1000L + i * 33, 1000L + i * 33)
        }
    @Test fun newOrStationaryNoteOnLineDoesNotFire() {
        val t = VisualNoteTracker()
        for (i in 0..8) assertTrue(t.observe(scene(100.0), 1000L + i * 33, 1000L + i * 33).isEmpty())
    }
    @Test fun movingTrajectoryProducesNormalizedContact() {
        val frames = cross(VisualNoteTracker())
        assertTrue(frames.take(3).all { it.isEmpty() })
        val touch = frames.last().single()
        assertEquals(VisualTouchKind.TAP, touch.kind)
        assertEquals(100f / 320f, touch.x, 0.001f); assertEquals(0.5f, touch.y, 0.001f)
    }
    @Test fun sameNoteKeepsIdForShotDeduplication() {
        val t = VisualNoteTracker(); val first = cross(t).last().single()
        assertEquals(first.id, t.observe(scene(110.0), 1132, 1132).single().id)
        assertTrue(t.observe(scene(130.0), 1231, 1231).isEmpty())
    }
    @Test fun predictsBoundedCaptureAge() {
        val t = VisualNoteTracker()
        t.observe(scene(70.0), 1000, 1000); t.observe(scene(80.0), 1033, 1033)
        assertEquals(1, t.observe(scene(90.0), 1066, 1094).size)
    }
    @Test fun staleFrameDropsTrajectory() {
        val t = VisualNoteTracker(); assertEquals(1, cross(t).last().size)
        assertTrue(t.observe(scene(110.0), 1132, 1400).isEmpty())
        assertTrue(t.observe(scene(100.0), 1433, 1433).isEmpty())
    }
    @Test fun duplicateAndFutureFramesCannotFire() {
        val t = VisualNoteTracker(); t.observe(scene(70.0), 1000, 1000)
        assertTrue(t.observe(scene(100.0), 1000, 1000).isEmpty())
        assertTrue(t.observe(scene(100.0), 1100, 1099).isEmpty())
    }
    @Test fun gapRequiresFreshTrajectory() {
        val t = VisualNoteTracker()
        t.observe(scene(70.0), 1000, 1000); t.observe(scene(80.0), 1033, 1033)
        assertTrue(t.observe(scene(100.0), 1300, 1300).isEmpty())
    }
    @Test fun invisibleLineReleasesHold() {
        val t = VisualNoteTracker()
        t.observe(scene(30.0, 3, 30.0), 1000, 1000); t.observe(scene(50.0, 3, 30.0), 1033, 1033)
        assertEquals(VisualTouchKind.HOLD, t.observe(scene(70.0, 3, 30.0), 1066, 1066).single().kind)
        assertTrue(t.observe(VisualScene(320, 200, emptyList(), emptyList()), 1099, 1099).isEmpty())
    }
    @Test fun holdStartsAtHeadAndEndsAtTail() {
        val t = VisualNoteTracker()
        t.observe(scene(30.0, 3, 30.0), 1000, 1000); t.observe(scene(50.0, 3, 30.0), 1033, 1033)
        val touch = t.observe(scene(70.0, 3, 30.0), 1066, 1066).single()
        assertEquals(touch.id, t.observe(scene(90.0, 3, 30.0), 1099, 1099).single().id)
        assertEquals(touch.id, t.observe(scene(105.0, 3, 10.0), 1132, 1132).single().id)
        assertTrue(t.observe(scene(112.0, 3, 10.0), 1165, 1165).isEmpty())
    }
    @Test fun lostHoldExpiresWhileLineRemains() {
        val t = VisualNoteTracker()
        t.observe(scene(30.0, 3, 30.0), 1000, 1000); t.observe(scene(50.0, 3, 30.0), 1033, 1033)
        t.observe(scene(70.0, 3, 30.0), 1066, 1066)
        val empty = VisualScene(320, 200, listOf(line), emptyList())
        assertEquals(1, t.observe(empty, 1099, 1099).size)
        assertTrue(t.observe(empty, 1198, 1198).isEmpty())
    }
    @Test fun resetPreservesUniqueIds() {
        val t = VisualNoteTracker(); val first = cross(t).last().single().id
        t.reset(); assertNotEquals(first, cross(t).last().single().id)
    }
    @Test fun flickUsesApproachNormal() {
        val touch = cross(VisualNoteTracker(), 4).last().single()
        assertEquals(VisualTouchKind.FLICK, touch.kind); assertTrue(touch.flickDy < 0f)
    }
    @Test fun dragDoesNotBecomeTap() {
        assertEquals(VisualTouchKind.DRAG, cross(VisualNoteTracker(), 2).last().single().kind)
    }
    @Test fun rotatedLineProjectsOntoActualLine() {
        val angle = Math.PI / 6.0
        val rotated = VisualJudgeLine(160.0, 100.0, cos(angle), sin(angle), 280.0, 2.0)
        val t = VisualNoteTracker(); var touches = emptyList<io.github.phiscript.input.VisualTouch>()
        for (i in 0..3) {
            val d = 30.0 - i * 10
            val note = VisualNoteBlob(1, 160.0 + sin(angle) * d, 100.0 - cos(angle) * d, 0)
            touches = t.observe(VisualScene(320, 200, listOf(rotated), listOf(note)),
                1000L + i * 33, 1000L + i * 33)
        }
        assertEquals(0.5f, touches.single().x, 0.001f); assertEquals(0.5f, touches.single().y, 0.001f)
    }
    @Test fun invalidAssociationCannotTrigger() {
        val bad = VisualScene(320, 200, listOf(line), listOf(VisualNoteBlob(1, 100.0, 80.0, 7)))
        assertTrue(VisualNoteTracker().observe(bad, 1000, 1000).isEmpty())
    }
    @Test fun oldUnderlyingObservationCannotKeepHold() {
        val t = VisualNoteTracker()
        t.observe(scene(30.0, 3, 30.0), 1000, 1000); t.observe(scene(50.0, 3, 30.0), 1033, 1033)
        t.observe(scene(70.0, 3, 30.0), 1066, 1066)
        assertTrue(t.observe(VisualScene(320, 200, listOf(line), emptyList()), 1120, 1260).isEmpty())
    }
    @Test fun negativeLeadDelaysTriggerWithoutRejectingFreshFrames() {
        val t = VisualNoteTracker()
        for (i in 0..4) assertTrue(t.observe(scene(70.0 + i * 10), 1000L + i * 33,
            1000L + i * 33, -50).isEmpty())
        assertEquals(1, t.observe(scene(120.0), 1165, 1165, -50).size)
    }
    @Test fun forwardPredictionCannotReviveStaleFrames() {
        val t = VisualNoteTracker()
        t.observe(scene(70.0), 1000, 1000); t.observe(scene(80.0), 1033, 1033)
        assertTrue(t.observe(scene(90.0), 1066, 1400, 120).isEmpty())
    }
    @Test fun fastTapCrossingInTwoFramesFiresOnce() {
        val t = VisualNoteTracker()
        assertTrue(t.observe(scene(62.0), 1000, 1000).isEmpty())
        val hit = t.observe(scene(112.0), 1033, 1033).single()
        assertEquals(VisualTouchKind.TAP, hit.kind)
        assertEquals(hit.id, t.observe(scene(124.0), 1066, 1066).single().id)
    }
    @Test fun fastFlickCrossingInTwoFramesFires() {
        val t = VisualNoteTracker()
        t.observe(scene(65.0, 4), 1000, 1000)
        assertEquals(VisualTouchKind.FLICK, t.observe(scene(116.0, 4), 1033, 1033).single().kind)
    }
    @Test fun lineDirectionFlipKeepsTheApproachingTrack() {
        val t = VisualNoteTracker()
        t.observe(scene(70.0), 1000, 1000)
        val flipped = line.copy(dx = -1.0, dy = 0.0)
        t.observe(VisualScene(320, 200, listOf(flipped), listOf(VisualNoteBlob(1, 100.0, 80.0, 0))), 1033, 1033)
        t.observe(scene(90.0), 1066, 1066)
        assertEquals(1, t.observe(scene(100.0), 1099, 1099).size)
    }
    @Test fun twoFrameRecedingNoteIsNotClicked() {
        val t = VisualNoteTracker()
        t.observe(scene(112.0), 1000, 1000)
        assertTrue(t.observe(scene(136.0), 1033, 1033).isEmpty())
    }
    @Test fun frameGapCannotBecomeAFalseFastCrossing() {
        val t = VisualNoteTracker()
        t.observe(scene(65.0), 1000, 1000)
        assertTrue(t.observe(scene(105.0), 1160, 1160).isEmpty())
    }
}
