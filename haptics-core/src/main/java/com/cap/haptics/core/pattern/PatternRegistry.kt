package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.ViewFeedback
import com.cap.haptics.core.model.Waveform

/**
 * The degradation matrix, in code.
 *
 * Every pattern declares a rendering at **every** tier. `PatternRegistryTest` asserts that
 * exhaustively, which is what turns "never silently no-op" from an intention into a
 * property -- a missing cell would otherwise surface on a user's device as nothing
 * happening, indistinguishable from a broken motor.
 *
 * **T2 does not always use a predefined effect.** The four platform effects cannot be
 * sequenced without scheduling separate `vibrate` calls, and a Handler between beats lets
 * system load smear the rhythm. So multi-beat patterns -- SUCCESS, ERROR, HEARTBEAT,
 * RAMP_UP -- render as a single waveform even at T2, and only single-beat patterns use the
 * tuned effects. That is a real limitation of the predefined API, not a shortcut: T2's
 * advantage over T1 is OEM tuning of individual impacts, and it has nothing to offer a
 * rhythm.
 *
 * Values are a starting point tuned by feel on hardware; the tests pin down the *relations*
 * between them (impacts stay ordered, every cell exists) rather than the numbers, so
 * retuning does not mean rewriting the suite.
 */
internal object PatternRegistry {

    /**
     * Patterns that are genuinely UI gestures, and should therefore go through the system's
     * view-feedback channel when one is available.
     *
     * Deliberately a very short list. It is tempting to route SUCCESS to `CONFIRM` and ERROR
     * to `REJECT`, but that would surrender the tuned compositions, the intensity dial and
     * the whole tier story for patterns a game invents its own meaning for. The system
     * channel earns its place only where the user's own expectation of the gesture -- what a
     * long-press feels like everywhere else on their phone -- outweighs ours.
     */
    fun viewFeedbackFor(pattern: HapticPattern): ViewFeedback? = when (pattern) {
        HapticPattern.LONG_PRESS -> ViewFeedback.LONG_PRESS
        else -> null
    }

    fun renderingFor(pattern: HapticPattern, tier: HapticTier): Rendering? = when (tier) {
        HapticTier.COMPOSED -> composed(pattern)
        HapticTier.PREDEFINED -> predefined(pattern)
        HapticTier.WAVEFORM -> waveform(pattern)
        // Nothing to render on a device with no motor; NoOpBackend never asks.
        HapticTier.NONE -> null
    }

    private fun composed(pattern: HapticPattern): Rendering = Rendering.Composed(
        when (pattern) {
            HapticPattern.SELECTION -> listOf(step(HapticPrimitive.TICK, 0.6f))
            HapticPattern.IMPACT_LIGHT -> listOf(step(HapticPrimitive.CLICK, 0.55f))
            HapticPattern.IMPACT_MEDIUM -> listOf(step(HapticPrimitive.CLICK, 0.8f))
            HapticPattern.IMPACT_HEAVY -> listOf(step(HapticPrimitive.CLICK, 1.0f))
            HapticPattern.LONG_PRESS -> listOf(step(HapticPrimitive.CLICK, 0.85f))

            // Rising then landing: the shape is what makes it read as affirmative.
            HapticPattern.SUCCESS -> listOf(
                step(HapticPrimitive.QUICK_RISE, 0.7f),
                step(HapticPrimitive.CLICK, 1.0f, delayMs = 60),
            )

            // Two beats, second weaker -- unresolved, so it reads as "attention" not "done".
            HapticPattern.WARNING -> listOf(
                step(HapticPrimitive.CLICK, 0.9f),
                step(HapticPrimitive.CLICK, 0.7f, delayMs = 120),
            )

            HapticPattern.ERROR -> listOf(
                step(HapticPrimitive.CLICK, 1.0f),
                step(HapticPrimitive.CLICK, 1.0f, delayMs = 90),
                step(HapticPrimitive.CLICK, 1.0f, delayMs = 90),
            )

            HapticPattern.RAMP_UP -> listOf(step(HapticPrimitive.SLOW_RISE, 1.0f))

            HapticPattern.HEARTBEAT -> listOf(
                step(HapticPrimitive.THUD, 1.0f),
                step(HapticPrimitive.THUD, 0.75f, delayMs = 90),
            )
        }
    )

    private fun predefined(pattern: HapticPattern): Rendering? = when (pattern) {
        // Single-beat: the tuned effect genuinely beats anything we could author.
        HapticPattern.SELECTION -> Rendering.Effect(PredefinedEffect.TICK)
        HapticPattern.IMPACT_LIGHT -> Rendering.Effect(PredefinedEffect.TICK)
        HapticPattern.IMPACT_MEDIUM -> Rendering.Effect(PredefinedEffect.CLICK)
        HapticPattern.IMPACT_HEAVY -> Rendering.Effect(PredefinedEffect.HEAVY_CLICK)
        HapticPattern.LONG_PRESS -> Rendering.Effect(PredefinedEffect.HEAVY_CLICK)

        // The one multi-beat pattern the predefined API can express natively.
        HapticPattern.WARNING -> Rendering.Effect(PredefinedEffect.DOUBLE_CLICK)

        // Rhythm the effects cannot express -- fall through to the waveform rendering.
        HapticPattern.SUCCESS,
        HapticPattern.ERROR,
        HapticPattern.RAMP_UP,
        HapticPattern.HEARTBEAT,
        -> waveform(pattern)
    }

    private fun waveform(pattern: HapticPattern): Rendering? = when (pattern) {
        // Amplitudes start well above the motor's perceptible floor: a nominally "light"
        // pattern that cannot be felt is not light, it is broken. The lightness has to come
        // from duration and rhythm as much as from raw amplitude.
        HapticPattern.SELECTION -> wave(longArrayOf(0, 12), intArrayOf(0, 110))
        HapticPattern.IMPACT_LIGHT -> wave(longArrayOf(0, 18), intArrayOf(0, 150))
        HapticPattern.IMPACT_MEDIUM -> wave(longArrayOf(0, 28), intArrayOf(0, 205))
        HapticPattern.IMPACT_HEAVY -> wave(longArrayOf(0, 45), intArrayOf(0, 255))
        HapticPattern.LONG_PRESS -> wave(longArrayOf(0, 50), intArrayOf(0, 240))

        HapticPattern.SUCCESS -> wave(
            longArrayOf(0, 30, 60, 50),
            intArrayOf(0, 180, 0, 255),
        )

        HapticPattern.WARNING -> wave(
            longArrayOf(0, 40, 80, 40),
            intArrayOf(0, 235, 0, 235),
        )

        HapticPattern.ERROR -> wave(
            longArrayOf(0, 50, 70, 50, 70, 50),
            intArrayOf(0, 255, 0, 255, 0, 255),
        )

        HapticPattern.HEARTBEAT -> wave(
            longArrayOf(0, 60, 90, 40),
            intArrayOf(0, 255, 0, 190),
        )

        HapticPattern.RAMP_UP -> rampUp()
    }

    /**
     * The one rendering that deliberately breaks the even-index-is-silent convention: a ramp
     * is continuously on, so there are no silent segments to preserve. On a motor without
     * amplitude control it collapses to a flat buzz, which is the honest hardware answer --
     * such a device cannot produce a swell at all.
     */
    private fun rampUp(): Rendering? {
        val steps = 16
        val stepMs = 25L
        val timings = LongArray(steps) { stepMs }
        // Starts at 60 rather than near zero: the first few steps of a ramp that begins below
        // the perceptible floor are dead time, which reads as a late start instead of a swell.
        val amplitudes = IntArray(steps) { index ->
            (60 + (195 * index / (steps - 1))).coerceIn(1, Waveform.MAX_AMPLITUDE)
        }
        return wave(timings, amplitudes)
    }

    private fun step(primitive: HapticPrimitive, scale: Float, delayMs: Int = 0) =
        CompositionStep(primitive, scale, delayMs)

    private fun wave(timingsMs: LongArray, amplitudes: IntArray): Rendering? =
        Waveform.create(timingsMs, amplitudes)?.let { Rendering.Wave(it) }
}
