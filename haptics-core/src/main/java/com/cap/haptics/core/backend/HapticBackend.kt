package com.cap.haptics.core.backend

import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.Waveform

/**
 * One playback strategy. Chosen once at init by [BackendFactory], never per call.
 *
 * Every method is implementable at every tier -- a lower tier renders an approximation
 * rather than refusing. That is the "never silently no-op" rule expressed as a type:
 * there is no way to write a backend that simply cannot play something.
 */
internal interface HapticBackend {

    /** The tier this instance actually implements -- not what the device could support. */
    val tier: HapticTier

    fun playWaveform(waveform: Waveform): HapticResult

    /** Rendered natively at T2 and above; approximated as a waveform below. */
    fun playEffect(effect: PredefinedEffect): HapticResult

    /** Composed natively at T3; flattened to a waveform below. */
    fun playComposition(steps: List<CompositionStep>): HapticResult

    /**
     * The SDK's main entry point: a semantic pattern, rendered however this tier can.
     *
     * @param intensity 0..1. Reduces the authored rendering; never strengthens it.
     */
    fun playPattern(pattern: HapticPattern, intensity: Float): HapticResult

    fun cancel()
}
