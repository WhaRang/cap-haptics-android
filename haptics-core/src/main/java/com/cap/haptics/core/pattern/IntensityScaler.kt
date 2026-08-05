package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.Waveform
import kotlin.math.pow

/**
 * Applies a 0..1 intensity to a rendering.
 *
 * Scaling happens here rather than in each backend so the meaning of "half intensity" stays
 * consistent across tiers, and so it can be tested without hardware.
 *
 * **Scaling can only ever reduce.** An intensity below 1 must never produce something
 * stronger than the authored rendering -- otherwise a caller turning the dial down could
 * make a light tap heavier, which is worse than the dial doing nothing.
 *
 * **Scaling is perceptual, not linear.** Two facts about vibration motors make a plain
 * multiply the wrong thing:
 *
 * 1. There is a floor. Below roughly a third of a pattern's authored strength most motors
 *    produce something a hand cannot reliably detect, so linear scaling does not fade -- it
 *    falls off a cliff and the bottom half of the dial is dead travel.
 * 2. Sensation is compressive. Half the amplitude feels far more than half as weak, so a
 *    linear dial spends most of its range in territory that all feels equally faint.
 *
 * So intensity maps onto `[PERCEPTUAL_FLOOR, 1]` through a [CURVE] exponent below 1, which
 * expands the quiet end where the interesting differences are.
 *
 * A consequence worth knowing: **intensity 0 is the weakest thing you can still feel, not
 * silence.** A caller that wants nothing should not call `playPattern` at all. A dial whose
 * low end silently does nothing is the bug this exists to prevent.
 */
internal object IntensityScaler {

    /** Fraction of authored strength below which most motors stop being perceptible. */
    const val PERCEPTUAL_FLOOR: Float = 0.35f

    /** Exponent below 1 expands the lower half of the dial. */
    private const val CURVE: Float = 0.6f

    /**
     * Maps a raw 0..1 dial position onto the usable strength range.
     *
     * Exposed for testing: the curve's properties (monotonic, bounded, never below the
     * floor) are what matter, and pinning them beats asserting arithmetic that changes
     * every time the constants are retuned.
     */
    fun effectiveFactor(intensity: Float): Float {
        val clamped = if (intensity.isNaN()) 1f else intensity.coerceIn(0f, 1f)
        if (clamped >= 1f) return 1f
        return PERCEPTUAL_FLOOR + (1f - PERCEPTUAL_FLOOR) * clamped.pow(CURVE)
    }

    /**
     * The predefined effects that form an intensity ladder. DOUBLE_CLICK is absent
     * deliberately: it is a rhythm, not a weight, and has no place on this scale.
     */
    private val IMPACT_LADDER = listOf(
        PredefinedEffect.TICK,
        PredefinedEffect.CLICK,
        PredefinedEffect.HEAVY_CLICK,
    )

    fun scale(rendering: Rendering, intensity: Float): Rendering {
        val factor = effectiveFactor(intensity)
        if (factor >= 1f) return rendering

        return when (rendering) {
            is Rendering.Composed -> Rendering.Composed(
                rendering.steps.map { it.copy(scale = (it.scale * factor).coerceIn(0f, 1f)) }
            )

            is Rendering.Wave -> scaleWave(rendering, factor)

            // The ladder is a three-way choice, so it reads the raw dial position -- running
            // it through the curve would just bias every request toward the heavy rung.
            is Rendering.Effect -> scaleEffect(
                rendering,
                if (intensity.isNaN()) 1f else intensity.coerceIn(0f, 1f),
            )
        }
    }

    private fun scaleWave(rendering: Rendering.Wave, factor: Float): Rendering {
        // No amplitude data means the motor has nothing to scale -- the timings alone carry
        // the pattern, and stretching those would change its identity rather than its weight.
        val amplitudes = rendering.waveform.amplitudes ?: return rendering

        val scaled = IntArray(amplitudes.size) { index ->
            val value = amplitudes[index]
            // Silence stays silence; anything audible stays audible.
            if (value == 0) 0 else (value * factor).toInt().coerceIn(1, Waveform.MAX_AMPLITUDE)
        }

        val rebuilt = Waveform.create(
            rendering.waveform.timingsMs,
            scaled,
            rendering.waveform.repeatIndex,
        )
        return rebuilt?.let { Rendering.Wave(it) } ?: rendering
    }

    /**
     * The predefined API exposes no intensity parameter, so the only lever is picking a
     * lighter effect. Patterns outside the impact ladder are returned unchanged -- at T2,
     * intensity genuinely does nothing for them, and that is a real property of the tier
     * rather than something worth faking.
     */
    private fun scaleEffect(rendering: Rendering.Effect, intensity: Float): Rendering {
        val authored = IMPACT_LADDER.indexOf(rendering.effect)
        if (authored < 0) return rendering

        val requested = when {
            intensity < 0.34f -> 0
            intensity < 0.67f -> 1
            else -> 2
        }
        return Rendering.Effect(IMPACT_LADDER[minOf(requested, authored)])
    }
}
