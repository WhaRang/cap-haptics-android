package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.PredefinedEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two properties the approximation table has to hold, neither of which the
 * compiler can check.
 */
class EffectApproximationTest {

    @Test
    fun `every effect has an approximation`() {
        // The "never silently no-op" rule. A missing entry would surface on a user's device
        // as nothing happening, which is indistinguishable from a broken motor.
        PredefinedEffect.entries.forEach { effect ->
            assertNotNull("no approximation for $effect", EffectApproximation.of(effect))
        }
    }

    @Test
    fun `every approximation survives losing amplitude control`() {
        // Even indices must be silent, or stripping amplitudes on a motor without amplitude
        // control would reinterpret the envelope instead of degrading it.
        PredefinedEffect.entries.forEach { effect ->
            val waveform = EffectApproximation.of(effect)!!
            assertTrue(
                "$effect breaks the alternating convention",
                waveform.followsAlternatingConvention,
            )
        }
    }

    @Test
    fun `impact effects get progressively stronger`() {
        // Encodes intent: whatever the tuned numbers become, TICK must stay lighter than
        // CLICK, which must stay lighter than HEAVY_CLICK.
        val tick = EffectApproximation.of(PredefinedEffect.TICK)!!
        val click = EffectApproximation.of(PredefinedEffect.CLICK)!!
        val heavy = EffectApproximation.of(PredefinedEffect.HEAVY_CLICK)!!

        assertTrue(tick.amplitudes!!.max() < click.amplitudes!!.max())
        assertTrue(click.amplitudes!!.max() < heavy.amplitudes!!.max())
        assertTrue(tick.totalDurationMs < heavy.totalDurationMs)
    }

    @Test
    fun `double click is two pulses with a gap`() {
        val doubleClick = EffectApproximation.of(PredefinedEffect.DOUBLE_CLICK)!!

        assertEquals(4, doubleClick.timingsMs.size)
        assertTrue("second pulse must be separated by silence", doubleClick.timingsMs[2] > 0)
    }
}
