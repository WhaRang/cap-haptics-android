package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.Waveform

/**
 * How each T2 effect is rendered when the predefined API is unavailable or the device
 * reports the effect unsupported.
 *
 * This is one slice of the degradation matrix -- semantic pattern rendering reuses these
 * values. Pure Kotlin so the numbers are
 * unit-testable and so the "every rendering degrades to something" rule can be asserted
 * rather than hoped for.
 *
 * Every waveform here follows the even-index-is-silent convention, so amplitudes can be
 * stripped on a motor without amplitude control and the rhythm still survives.
 */
internal object EffectApproximation {

    /**
     * Null only if a constant below were authored wrong -- `EffectApproximationTest` asserts
     * that never happens. Nullable rather than `!!` so a mistake degrades to a result code
     * instead of an NPE inside a consumer's app.
     */
    fun of(effect: PredefinedEffect): Waveform? = when (effect) {
        PredefinedEffect.TICK -> Waveform.create(
            timingsMs = longArrayOf(0, 12),
            amplitudes = intArrayOf(0, 110),
        )

        PredefinedEffect.CLICK -> Waveform.create(
            timingsMs = longArrayOf(0, 28),
            amplitudes = intArrayOf(0, 200),
        )

        PredefinedEffect.HEAVY_CLICK -> Waveform.create(
            timingsMs = longArrayOf(0, 45),
            amplitudes = intArrayOf(0, Waveform.MAX_AMPLITUDE),
        )

        PredefinedEffect.DOUBLE_CLICK -> Waveform.create(
            timingsMs = longArrayOf(0, 28, 80, 28),
            amplitudes = intArrayOf(0, 200, 0, 200),
        )
    }
}
