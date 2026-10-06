package io.github.phiscript.vision
import org.junit.Assert.*
import org.junit.Test
class PhiraMenuGlyphsTest {
    private fun rendered(kind: PhiraMenuGlyphs.Kind): IntArray {
        val s = PhiraMenuGlyphs.SIDE
        return IntArray(s * s) { i ->
            if (PhiraMenuGlyphs.template(i % s / (s - 1.0), i / s / (s - 1.0), kind))
                0xffeeeeee.toInt() else 0xff202020.toInt()
        }
    }
    @Test fun acceptsDistinctGlyphShapes() {
        for (k in PhiraMenuGlyphs.Kind.values()) assertTrue(k.name, PhiraMenuGlyphs.matches(rendered(k), k))
    }
    @Test fun flatBackgroundCannotBeMenu() {
        for (c in listOf(0xff101010, 0xff606060, 0xffeeeeee))
            for (k in PhiraMenuGlyphs.Kind.values())
                assertFalse(PhiraMenuGlyphs.matches(IntArray(33 * 33) { c.toInt() }, k))
    }
    @Test fun iconsCannotSubstituteForEachOther() {
        assertFalse(PhiraMenuGlyphs.matches(rendered(PhiraMenuGlyphs.Kind.RESUME), PhiraMenuGlyphs.Kind.BACK))
        assertFalse(PhiraMenuGlyphs.matches(rendered(PhiraMenuGlyphs.Kind.BACK), PhiraMenuGlyphs.Kind.RESUME))
        assertFalse(PhiraMenuGlyphs.matches(rendered(PhiraMenuGlyphs.Kind.RESUME), PhiraMenuGlyphs.Kind.RETRY))
    }
    @Test fun retryRequiresHole() {
        val p = rendered(PhiraMenuGlyphs.Kind.RETRY)
        for (i in p.indices) if (kotlin.math.abs(i % 33 - 16) < 5 && kotlin.math.abs(i / 33 - 16) < 5)
            p[i] = 0xffeeeeee.toInt()
        assertFalse(PhiraMenuGlyphs.matches(p, PhiraMenuGlyphs.Kind.RETRY))
    }
}
