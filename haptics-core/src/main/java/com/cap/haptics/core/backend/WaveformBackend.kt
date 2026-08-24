package com.cap.haptics.core.backend

import android.os.VibrationEffect
import android.os.Vibrator
import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.Waveform
import com.cap.haptics.core.feedback.ViewFeedbackChannel
import com.cap.haptics.core.pattern.CompositionApproximation
import com.cap.haptics.core.pattern.EffectApproximation
import com.cap.haptics.core.pattern.IntensityScaler
import com.cap.haptics.core.pattern.PatternRegistry
import com.cap.haptics.core.pattern.Rendering
import com.cap.haptics.core.util.HLog

/**
 * T1 -- `VibrationEffect.createWaveform`. The API 26 floor, so this always works.
 *
 * Deliberately `open`: a T3 device can do everything a T1 device can, so the higher-tier
 * backends extend this one and add their tier's capability on top rather than duplicating
 * raw waveform playback. That mirrors how the platform itself layers.
 */
internal open class WaveformBackend(
    protected val vibrator: Vibrator,
    protected val capabilities: HapticCapabilities,
    /** Null when the SDK was initialised without an Activity. */
    protected val viewFeedback: ViewFeedbackChannel? = null,
) : HapticBackend {

    override val tier: HapticTier get() = HapticTier.WAVEFORM

    override fun playWaveform(waveform: Waveform): HapticResult {
        // The motor decides whether amplitude data is meaningful. Dropping it keeps the
        // rhythm intact, which carries most of a pattern's identity.
        val effective =
            if (waveform.amplitudes != null && !capabilities.hasAmplitudeControl) {
                waveform.withoutAmplitudes()
            } else {
                waveform
            }

        return try {
            val amplitudes = effective.amplitudes
            val effect = if (amplitudes != null) {
                VibrationEffect.createWaveform(
                    effective.timingsMs,
                    amplitudes,
                    effective.repeatIndex,
                )
            } else {
                VibrationEffect.createWaveform(effective.timingsMs, effective.repeatIndex)
            }
            vibrator.vibrate(effect)
            HLog.d("T${tier.level} waveform: $effective")
            HapticResult.OK
        } catch (t: Throwable) {
            HLog.e("Waveform playback failed for $effective", t)
            HapticResult.PLATFORM_ERROR
        }
    }

    /**
     * No predefined API at this tier, so the effect becomes its waveform approximation.
     * Subclasses override to use the real thing and call back here when the device reports
     * a specific effect unsupported.
     */
    override fun playEffect(effect: PredefinedEffect): HapticResult {
        val approximation = EffectApproximation.of(effect)
        if (approximation == null) {
            HLog.e("No approximation defined for $effect")
            return HapticResult.UNSUPPORTED_PATTERN
        }
        HLog.d("T${tier.level} approximating $effect as a waveform")
        return playWaveform(approximation)
    }

    /**
     * No composition API at this tier, so the sequence is flattened into one waveform.
     * A single `vibrate` call keeps the timing accurate -- scheduling separate calls with a
     * Handler would let system load smear the rhythm.
     */
    override fun playComposition(steps: List<CompositionStep>): HapticResult {
        val problem = CompositionStep.validate(steps)
        if (problem != null) {
            HLog.w("Rejected composition: $problem")
            return HapticResult.INVALID_ARGUMENT
        }

        val flattened = CompositionApproximation.toWaveform(steps)
        if (flattened == null) {
            HLog.e("Could not flatten composition: $steps")
            return HapticResult.UNSUPPORTED_PATTERN
        }
        HLog.d("T${tier.level} flattening ${steps.size}-step composition to a waveform")
        return playWaveform(flattened)
    }

    /**
     * Implemented once, for every tier. [tier] is overridden by each subclass, so the
     * registry hands back that tier's rendering and the `when` dispatches to the method the
     * subclass has already specialised -- T3 composes, T2 uses tuned effects, T1 plays
     * waveforms, with no duplication and no tier-specific branching here.
     */
    override fun playPattern(pattern: HapticPattern, intensity: Float): HapticResult {
        // The system channel obeys the user's haptic settings and is OEM-tuned per gesture,
        // but offers no intensity control -- so it is only the right answer when the caller
        // wants the pattern exactly as authored.
        if (intensity >= 1f) {
            PatternRegistry.viewFeedbackFor(pattern)?.let { feedback ->
                when (val result = viewFeedback?.perform(feedback)) {
                    // SUPPRESSED is returned, not routed around. The user switched haptics
                    // off; reaching for the Vibrator to buzz anyway would be overriding a
                    // preference the platform just told us about.
                    HapticResult.OK, HapticResult.SUPPRESSED -> return result

                    else -> HLog.d("View feedback unavailable for $pattern; using T${tier.level}")
                }
            }
        }

        val rendering = PatternRegistry.renderingFor(pattern, tier)
        if (rendering == null) {
            // Unreachable: PatternRegistryTest asserts every cell of the matrix is filled.
            HLog.e("No rendering for $pattern at T${tier.level}")
            return HapticResult.UNSUPPORTED_PATTERN
        }

        return when (val scaled = IntensityScaler.scale(rendering, intensity)) {
            is Rendering.Composed -> playComposition(scaled.steps)
            is Rendering.Effect -> playEffect(scaled.effect)
            is Rendering.Wave -> playWaveform(scaled.waveform)
        }
    }

    override fun cancel() {
        runCatching { vibrator.cancel() }
            .onFailure { HLog.w("cancel() failed", it) }
    }
}
