package com.cap.haptics.core.model

/**
 * One primitive in a T3 composition.
 *
 * @param scale intensity in 0..1. The platform rejects anything outside that range.
 * @param delayMs pause before this step, measured from the end of the previous one.
 */
data class CompositionStep(
    val primitive: HapticPrimitive,
    val scale: Float = 1f,
    val delayMs: Int = 0,
) {
    companion object {
        const val MAX_SCALE: Float = 1f

        /** Returns a human-readable problem description, or null when valid. */
        fun validate(steps: List<CompositionStep>): String? = when {
            steps.isEmpty() ->
                "composition must have at least one step"

            steps.any { it.scale < 0f || it.scale > MAX_SCALE } ->
                "scale must be in 0..$MAX_SCALE, got ${steps.map { it.scale }}"

            steps.any { it.scale.isNaN() } ->
                "scale must not be NaN"

            steps.any { it.delayMs < 0 } ->
                "delayMs must be non-negative, got ${steps.map { it.delayMs }}"

            else -> null
        }
    }
}
