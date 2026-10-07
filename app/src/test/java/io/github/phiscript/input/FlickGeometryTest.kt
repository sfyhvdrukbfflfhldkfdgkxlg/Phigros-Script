package io.github.phiscript.input
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
class FlickGeometryTest {
    @Test fun interiorFlickKeepsItsDirection() {
        val p = FlickGeometry.endpoint(160f, 100f, 0f, -12f, 320, 200)
        assertEquals(160f, p.x, 0.001f); assertEquals(88f, p.y, 0.001f)
    }
    @Test fun topEdgeFlickReversesInsteadOfBecomingTap() {
        val p = FlickGeometry.endpoint(100f, 0f, 0f, -12f, 320, 200); assertEquals(12f, p.y, 0.001f)
    }
    @Test fun cornerFlickRemainsInBoundsAndHasMotion() {
        val p = FlickGeometry.endpoint(0f, 0f, -10f, -10f, 320, 200)
        assertTrue(p.x >= 0f && p.y >= 0f); assertTrue(hypot(p.x.toDouble(), p.y.toDouble()) >= 7)
    }
    @Test fun zeroVectorGetsVisibleSwipe() {
        val p = FlickGeometry.endpoint(160f, 100f, 0f, 0f, 320, 200)
        assertTrue(hypot((p.x - 160f).toDouble(), (p.y - 100f).toDouble()) >= 6.9)
    }
    @Test fun tinyVectorGetsMinimumMotion() {
        val p = FlickGeometry.endpoint(160f, 100f, 0f, -0.1f, 320, 200); assertTrue(100f - p.y >= 6.9f)
    }
}
