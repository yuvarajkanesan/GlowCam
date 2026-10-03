package com.glowcam

import com.glowcam.gl.CropRect
import com.glowcam.gl.EffectParams
import com.glowcam.gl.Filters
import com.glowcam.gl.LookStore
import com.glowcam.gl.Looks
import com.glowcam.gl.Mat3
import com.glowcam.ui.NRect
import com.glowcam.ui.ProMath
import com.glowcam.ui.inscribedCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    // ---- Pro mode maths ----
    @Test fun isoSliderCoversTheSensorRange() {
        val r = 50..3200
        assertEquals(50, ProMath.iso(0f, r))
        assertEquals(3200, ProMath.iso(1f, r))
        assertTrue(ProMath.iso(0.5f, r) in 300..500)
    }

    @Test fun isoRoundTrips() {
        val r = 12..3200
        for (iso in listOf(12, 100, 800, 3200)) {
            assertEquals(iso.toDouble(), ProMath.iso(ProMath.isoT(iso, r), r).toDouble(), 1.0)
        }
    }

    @Test fun shutterLabelsAreReadable() {
        assertEquals("1/60", ProMath.shutterLabel(16_666_667L))
        assertEquals("1/1000", ProMath.shutterLabel(1_000_000L))
        assertEquals("1.0s", ProMath.shutterLabel(1_000_000_000L))
    }

    // ---- matrices ----
    @Test fun identityLeavesPointsAlone() {
        val m = Mat3.mul(Mat3.identity(), Mat3.translate(0.2f, 0.3f))
        assertEquals(0.2f, m[2], 1e-6f)
        assertEquals(0.3f, m[5], 1e-6f)
    }

    @Test fun mirrorTwiceIsIdentity() {
        val m = Mat3.mul(Mat3.mirrorX(), Mat3.mirrorX())
        val id = Mat3.identity()
        for (i in 0..8) assertEquals(id[i], m[i], 1e-6f)
    }

    @Test fun rotatingAroundCentreKeepsTheCentre() {
        val m = Mat3.rotateAroundCenter(90)
        // (0.5, 0.5) -> (0.5, 0.5)
        val x = m[0] * 0.5f + m[1] * 0.5f + m[2]
        val y = m[3] * 0.5f + m[4] * 0.5f + m[5]
        assertEquals(0.5f, x, 1e-5f)
        assertEquals(0.5f, y, 1e-5f)
    }

    // ---- crop ----
    @Test fun straightenCropShrinksWithAngle() {
        val flat = inscribedCrop(0f, 0.75f)
        val tilted = inscribedCrop(10f, 0.75f)
        assertEquals(1f, flat.w, 1e-3f)
        assertTrue(tilted.w < flat.w)
        assertTrue(tilted.x > 0f)
    }

    @Test fun cropRectConvertsBetweenOrigins() {
        val n = NRect(0.1f, 0.2f, 0.6f, 0.9f)
        val c = n.toCrop()
        val back = NRect.from(c)
        assertEquals(n.l, back.l, 1e-6f)
        assertEquals(n.t, back.t, 1e-6f)
        assertEquals(n.r, back.r, 1e-6f)
        assertEquals(n.b, back.b, 1e-6f)
        assertTrue(CropRect().isFull)
    }

    // ---- effects ----
    @Test fun untouchedPhotoIsIdentity() {
        assertTrue(EffectParams().isIdentity)
        assertFalse(EffectParams(smooth = 0.3f).isIdentity)
        assertFalse(EffectParams(lip = 0.5f).isIdentity)
        assertFalse(EffectParams(bgMode = 1).isIdentity)
        assertFalse(EffectParams(filter = Filters.all[1]).isIdentity)
    }

    @Test fun faceAndMaskRequirementsFollowTheEffects() {
        assertFalse(EffectParams().needsFaces)
        assertTrue(EffectParams(blush = 0.4f).needsFaces)
        assertFalse(EffectParams().needsMask)
        assertTrue(EffectParams(bgMode = 3).needsMask)
        val splash = Filters.all.first { it.id == "splash" }
        assertTrue(EffectParams(filter = splash).needsMask)
    }

    // ---- filters and looks ----
    @Test fun filterLibraryIsBigAndUnique() {
        assertTrue(Filters.all.size >= 26)
        assertEquals(Filters.all.size, Filters.all.map { it.id }.toSet().size)
    }

    @Test fun everyLookAppliesCleanly() {
        for (look in Looks.presets) {
            val p = look.apply(EffectParams(smooth = 0.9f, lip = 0.9f, bgMode = 2))
            // a look replaces the previous style instead of stacking on it
            assertFalse(look.name, p.bgMode == 2)
            assertTrue(look.name, p.smooth <= 0.6f)
        }
    }

    @Test fun savedLooksRoundTrip() {
        val original = EffectParams(
            smooth = 0.4f, slim = 0.2f, lip = 0.5f, lipColor = 0xFF8E1B4A.toInt(), blush = 0.3f,
            filter = Filters.all.first { it.id == "cinematic" }, filterIntensity = 0.7f, bgMode = 2,
        )
        val restored = LookStore.fromJson(EffectParams(), LookStore.toJson(original))
        assertEquals(original.smooth, restored.smooth, 1e-5f)
        assertEquals(original.slim, restored.slim, 1e-5f)
        assertEquals(original.lip, restored.lip, 1e-5f)
        assertEquals(original.lipColor, restored.lipColor)
        assertEquals(original.filter.id, restored.filter.id)
        assertEquals(original.bgMode, restored.bgMode)
    }
}
