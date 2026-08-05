package com.cap.haptics.core.model

/**
 * Composition primitives behind `VibrationEffect.Composition` (T3).
 *
 * Note the two-tier [minApi]: the original five landed in API 30, the remaining three in
 * API 31. A device can therefore support half this list -- which is why support is probed
 * per-primitive rather than as a single "composition works" boolean.
 *
 * Like [PredefinedEffect], this holds no platform constant so the model layer stays pure.
 */
enum class HapticPrimitive(val minApi: Int) {
    CLICK(30),
    TICK(30),
    QUICK_RISE(30),
    SLOW_RISE(30),
    QUICK_FALL(30),
    LOW_TICK(31),
    THUD(31),
    SPIN(31);

    companion object {
        /**
         * The primitives T3 cannot do without.
         *
         * If a device cannot produce a CLICK and a TICK there is nothing composition can
         * express that the predefined-effect tier does not already do better, so the tier
         * gate requires exactly these two and treats the rest as optional garnish handled
         * by substitution (A4).
         */
        val CORE: Set<HapticPrimitive> = setOf(CLICK, TICK)
    }
}
