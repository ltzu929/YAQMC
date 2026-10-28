package org.yaqmc.android.nativeui

import org.junit.Assert.*
import org.junit.Test

class NativeLegacyBlurTest {
    @Test fun blurPreservesSolidColorsAndDimensions() {
        val color = 0xff2468acf.toInt()
        assertArrayEquals(IntArray(25) { color }, nativeLegacyBoxBlur(IntArray(25) { color }, 5, 5, 3))
        assertArrayEquals(intArrayOf(1, 2, 3), nativeLegacyBoxBlur(intArrayOf(1, 2, 3), 3, 1, 0))
    }

    @Test fun blurSoftensHardEdgesWithoutChangingTheOuterAlpha() {
        val src = IntArray(9) { if (it < 4) 0xffff0000.toInt() else 0xff0000ff.toInt() }
        val blurred = nativeLegacyBoxBlur(src, 9, 1, 2)
        assertEquals(0xff, blurred[4] ushr 24)
        assertTrue(blurred[4] != src[4])
        assertEquals(src[0], blurred[0])
        assertEquals(src[8], blurred[8])
    }

    @Test fun invalidShapeIsRejectedAndLargeRadiusIsBounded() {
        try { nativeLegacyBoxBlur(intArrayOf(1, 2), 2, 2, 1); fail("Invalid bitmap geometry") }
        catch (_: IllegalArgumentException) { }
        assertEquals(4, nativeLegacyBoxBlur(IntArray(4), 2, 2, 999).size)
    }
}
