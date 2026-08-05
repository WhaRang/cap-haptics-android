package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.Waveform

/**
 * A concrete way to play something, at one tier.
 *
 * The registry maps `(pattern, tier)` to one of these. Note there is no requirement that a
 * given tier always uses its "own" rendering type -- see [PatternRegistry] for why T2 plays
 * several patterns as waveforms.
 */
internal sealed interface Rendering {

    /** T3. */
    data class Composed(val steps: List<CompositionStep>) : Rendering

    /** T2 -- a single platform-tuned effect. */
    data class Effect(val effect: PredefinedEffect) : Rendering

    /** T1, and any tier where rhythm matters more than the tier's native mechanism. */
    data class Wave(val waveform: Waveform) : Rendering
}
