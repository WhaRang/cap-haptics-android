package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.Waveform

/**
 * Renders a T3 composition as a T1 waveform, for devices that cannot compose.
 *
 * Each step becomes a `(silence, pulse)` pair, so the result follows the
 * even-index-is-silent convention and survives a motor with no amplitude control.
 *
 * **What is lost is the envelope shape.** QUICK_RISE and SLOW_RISE become flat pulses of
 * roughly the right length and weight -- a waveform cannot express a hardware-tuned ramp,
 * and pretending otherwise would produce something that feels wrong rather than simpler.
 * The pattern registry gives the patterns that genuinely depend on rising envelopes a purpose-built lower-tier
 * rendering instead of relying on this generic fallback.
 */
internal object CompositionApproximation {

    /** Perceptual stand-ins: duration in ms, and peak amplitude at scale 1.0. */
    private val CHARACTER: Map<HapticPrimitive, Pair<Long, Int>> = mapOf(
        HapticPrimitive.CLICK to (22L to 210),
        HapticPrimitive.TICK to (12L to 140),
        HapticPrimitive.LOW_TICK to (10L to 100),
        HapticPrimitive.THUD to (50L to 255),
        HapticPrimitive.SPIN to (60L to 190),
        HapticPrimitive.QUICK_RISE to (80L to 230),
        HapticPrimitive.SLOW_RISE to (300L to 230),
        HapticPrimitive.QUICK_FALL to (60L to 170),
    )

    fun toWaveform(steps: List<CompositionStep>): Waveform? {
        if (CompositionStep.validate(steps) != null) return null

        val timings = ArrayList<Long>(steps.size * 2)
        val amplitudes = ArrayList<Int>(steps.size * 2)

        steps.forEach { step ->
            val (durationMs, peak) = CHARACTER[step.primitive] ?: (20L to 150)

            timings += step.delayMs.toLong()
            amplitudes += 0

            timings += durationMs
            // Clamp to 1 rather than 0: a zero-amplitude segment of non-zero length is
            // silence that still costs time, which reads as a dropped step.
            amplitudes += (peak * step.scale).toInt().coerceIn(1, Waveform.MAX_AMPLITUDE)
        }

        return Waveform.create(timings.toLongArray(), amplitudes.toIntArray())
    }
}
