package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.HapticPrimitive

/**
 * Maps an unsupported primitive onto the nearest supported one.
 *
 * Substitution is **per-primitive, not per-pattern**. A motor may render CLICK perfectly and
 * refuse THUD; dropping the whole composition to T2 because of one missing garnish would
 * throw away the crispness of every other step. See PLAN.md section 3.2.
 *
 * Each chain is ordered by perceptual similarity and terminates in [HapticPrimitive.CORE],
 * which the T3 tier gate guarantees is present -- so on any device that reached T3 these
 * lookups always succeed. `PrimitiveSubstitutionTest` asserts that property directly.
 */
internal object PrimitiveSubstitution {

    private val CHAINS: Map<HapticPrimitive, List<HapticPrimitive>> = mapOf(
        // Sharp, short: degrade toward the other sharp primitive.
        HapticPrimitive.CLICK to listOf(HapticPrimitive.TICK),
        HapticPrimitive.TICK to listOf(HapticPrimitive.CLICK),
        HapticPrimitive.LOW_TICK to listOf(HapticPrimitive.TICK, HapticPrimitive.CLICK),

        // Heavy: a strong CLICK is closer than a TICK.
        HapticPrimitive.THUD to listOf(HapticPrimitive.CLICK, HapticPrimitive.TICK),

        // Textured/continuous: try the other envelope shapes before collapsing to a click.
        HapticPrimitive.SPIN to listOf(
            HapticPrimitive.QUICK_RISE,
            HapticPrimitive.CLICK,
            HapticPrimitive.TICK,
        ),
        HapticPrimitive.QUICK_RISE to listOf(
            HapticPrimitive.SLOW_RISE,
            HapticPrimitive.CLICK,
            HapticPrimitive.TICK,
        ),
        HapticPrimitive.SLOW_RISE to listOf(
            HapticPrimitive.QUICK_RISE,
            HapticPrimitive.CLICK,
            HapticPrimitive.TICK,
        ),
        HapticPrimitive.QUICK_FALL to listOf(
            HapticPrimitive.CLICK,
            HapticPrimitive.TICK,
        ),
    )

    /**
     * @return the primitive itself when supported, the nearest supported substitute
     *   otherwise, or null when nothing in its chain is available.
     */
    fun resolve(
        primitive: HapticPrimitive,
        supported: Set<HapticPrimitive>,
    ): HapticPrimitive? {
        if (primitive in supported) return primitive
        return CHAINS[primitive]?.firstOrNull { it in supported }
    }
}
