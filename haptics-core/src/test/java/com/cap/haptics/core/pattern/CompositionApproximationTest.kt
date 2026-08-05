package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionApproximationTest {

    @Test
    fun `each step becomes a silence and a pulse`() {
        val waveform = CompositionApproximation.toWaveform(
            listOf(
                CompositionStep(HapticPrimitive.CLICK),
                CompositionStep(HapticPrimitive.CLICK, delayMs = 60),
            )
        )!!

        assertEquals(4, waveform.timingsMs.size)
        assertEquals(60L, waveform.timingsMs[2])
    }

    @Test
    fun `flattened compositions survive losing amplitude control`() {
        val waveform = CompositionApproximation.toWaveform(
            HapticPrimitive.entries.map { CompositionStep(it, scale = 0.7f, delayMs = 20) }
        )!!

        assertTrue(waveform.followsAlternatingConvention)
    }

    @Test
    fun `scale drives amplitude`() {
        val quiet = CompositionApproximation.toWaveform(
            listOf(CompositionStep(HapticPrimitive.CLICK, scale = 0.2f))
        )!!
        val loud = CompositionApproximation.toWaveform(
            listOf(CompositionStep(HapticPrimitive.CLICK, scale = 1f))
        )!!

        assertTrue(quiet.amplitudes!!.max() < loud.amplitudes!!.max())
    }

    @Test
    fun `a zero scale still produces an audible pulse rather than dead time`() {
        // A zero-amplitude segment of non-zero length is silence that costs time, which
        // reads as a dropped step rather than a quiet one.
        val waveform = CompositionApproximation.toWaveform(
            listOf(CompositionStep(HapticPrimitive.CLICK, scale = 0f))
        )!!

        assertTrue(waveform.amplitudes!!.max() >= 1)
    }

    @Test
    fun `every primitive has a character mapping`() {
        HapticPrimitive.entries.forEach { primitive ->
            assertNotNull(
                "no flattening for $primitive",
                CompositionApproximation.toWaveform(listOf(CompositionStep(primitive))),
            )
        }
    }

    @Test
    fun `heavier primitives flatten to stronger pulses`() {
        val tick = CompositionApproximation.toWaveform(
            listOf(CompositionStep(HapticPrimitive.TICK))
        )!!
        val thud = CompositionApproximation.toWaveform(
            listOf(CompositionStep(HapticPrimitive.THUD))
        )!!

        assertTrue(tick.amplitudes!!.max() < thud.amplitudes!!.max())
        assertTrue(tick.totalDurationMs < thud.totalDurationMs)
    }

    @Test
    fun `invalid compositions are rejected`() {
        assertNull(CompositionApproximation.toWaveform(emptyList()))
        assertNull(
            CompositionApproximation.toWaveform(
                listOf(CompositionStep(HapticPrimitive.CLICK, scale = 1.5f))
            )
        )
        assertNull(
            CompositionApproximation.toWaveform(
                listOf(CompositionStep(HapticPrimitive.CLICK, delayMs = -1))
            )
        )
    }
}
