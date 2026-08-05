package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.Waveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntensityScalerTest {

    private val composed = Rendering.Composed(
        listOf(CompositionStep(HapticPrimitive.CLICK, scale = 0.8f))
    )

    private val wave = Rendering.Wave(
        Waveform.create(longArrayOf(0, 30, 60, 40), intArrayOf(0, 200, 0, 100))!!
    )

    @Test
    fun `full intensity is the identity`() {
        assertEquals(composed, IntensityScaler.scale(composed, 1f))
    }

    @Test
    fun `the dial bottoms out at the perceptual floor rather than at silence`() {
        // Linear scaling toward zero does not fade -- it falls off a cliff, and the bottom
        // half of the dial becomes dead travel where everything feels equally absent.
        assertEquals(
            IntensityScaler.PERCEPTUAL_FLOOR,
            IntensityScaler.effectiveFactor(0f),
            0.001f,
        )
        assertEquals(1f, IntensityScaler.effectiveFactor(1f), 0.001f)
    }

    @Test
    fun `the dial is monotonic`() {
        var previous = 0f
        (0..100).forEach { step ->
            val factor = IntensityScaler.effectiveFactor(step / 100f)
            assertTrue("factor decreased at $step", factor >= previous)
            previous = factor
        }
    }

    @Test
    fun `half the dial is well above half the strength`() {
        // Sensation is compressive: half amplitude feels far more than half as weak, so a
        // linear dial spends most of its travel in territory that all feels equally faint.
        assertTrue(IntensityScaler.effectiveFactor(0.5f) > 0.6f)
    }

    @Test
    fun `composed steps weaken monotonically without vanishing`() {
        val authored = composed.steps.single().scale
        var previous = 0f

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { intensity ->
            val scale = (IntensityScaler.scale(composed, intensity) as Rendering.Composed)
                .steps.single().scale

            assertTrue("scale exceeded the authored value", scale <= authored + 0.001f)
            assertTrue("scale was not monotonic", scale >= previous)
            assertTrue("scale collapsed to nothing", scale > 0f)
            previous = scale
        }
    }

    @Test
    fun `wave amplitudes weaken but silence stays silent`() {
        val scaled = IntensityScaler.scale(wave, 0.5f) as Rendering.Wave
        val original = wave.waveform.amplitudes!!
        val amplitudes = scaled.waveform.amplitudes!!

        assertEquals(0, amplitudes[0])
        assertEquals(0, amplitudes[2])
        assertTrue(amplitudes[1] in 1 until original[1])
        assertTrue(amplitudes[3] in 1 until original[3])
    }

    @Test
    fun `wave timings are never touched`() {
        // Scaling should change weight, not identity. Stretching the rhythm would make a
        // quiet Heartbeat into a different pattern rather than a softer one.
        val scaled = IntensityScaler.scale(wave, 0.3f) as Rendering.Wave
        assertEquals(
            (wave.waveform.timingsMs).toList(),
            scaled.waveform.timingsMs.toList(),
        )
    }

    @Test
    fun `an audible step never scales away to silence`() {
        val scaled = IntensityScaler.scale(wave, 0.001f) as Rendering.Wave
        assertTrue(scaled.waveform.amplitudes!![1] >= 1)
    }

    @Test
    fun `effects step down the impact ladder`() {
        val heavy = Rendering.Effect(PredefinedEffect.HEAVY_CLICK)

        assertEquals(
            PredefinedEffect.TICK,
            (IntensityScaler.scale(heavy, 0.2f) as Rendering.Effect).effect,
        )
        assertEquals(
            PredefinedEffect.CLICK,
            (IntensityScaler.scale(heavy, 0.5f) as Rendering.Effect).effect,
        )
    }

    @Test
    fun `scaling an effect never makes it stronger`() {
        // Turning the dial down must not turn a light tap into a heavy one. Without the cap
        // at the authored rung, TICK at 0.5 would become CLICK.
        val tick = Rendering.Effect(PredefinedEffect.TICK)
        listOf(0f, 0.2f, 0.5f, 0.9f).forEach { intensity ->
            assertEquals(
                PredefinedEffect.TICK,
                (IntensityScaler.scale(tick, intensity) as Rendering.Effect).effect,
            )
        }
    }

    @Test
    fun `rhythm effects are left alone`() {
        // DOUBLE_CLICK is a rhythm, not a weight; there is no lighter rung to move to.
        val doubleClick = Rendering.Effect(PredefinedEffect.DOUBLE_CLICK)
        assertEquals(
            PredefinedEffect.DOUBLE_CLICK,
            (IntensityScaler.scale(doubleClick, 0.1f) as Rendering.Effect).effect,
        )
    }

    @Test
    fun `out of range intensity is clamped rather than rejected`() {
        val negative = IntensityScaler.scale(composed, -5f) as Rendering.Composed
        assertTrue(negative.steps.single().scale >= 0f)

        assertEquals(composed, IntensityScaler.scale(composed, 99f))
        assertEquals(composed, IntensityScaler.scale(composed, Float.NaN))
    }

    @Test
    fun `scaling keeps every registry rendering playable`() {
        // Guards the whole matrix against a scale that produces something the platform would
        // reject -- an out-of-range amplitude, say -- at any intensity.
        listOf(HapticTier.WAVEFORM, HapticTier.PREDEFINED, HapticTier.COMPOSED).forEach { tier ->
            HapticPattern.entries.forEach { pattern ->
                val rendering = PatternRegistry.renderingFor(pattern, tier)!!
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { intensity ->
                    when (val scaled = IntensityScaler.scale(rendering, intensity)) {
                        is Rendering.Composed -> assertTrue(
                            "$pattern at $tier x$intensity produced an invalid scale",
                            CompositionStep.validate(scaled.steps) == null,
                        )

                        is Rendering.Wave -> assertTrue(
                            "$pattern at $tier x$intensity produced an invalid waveform",
                            Waveform.validate(
                                scaled.waveform.timingsMs,
                                scaled.waveform.amplitudes,
                                scaled.waveform.repeatIndex,
                            ) == null,
                        )

                        is Rendering.Effect -> Unit
                    }
                }
            }
        }
    }
}
