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
enum class HapticPrimitive(val id: Int, val minApi: Int) {
    CLICK(id = 0, minApi = 30),
    TICK(id = 1, minApi = 30),
    QUICK_RISE(id = 2, minApi = 30),
    SLOW_RISE(id = 3, minApi = 30),
    QUICK_FALL(id = 4, minApi = 30),
    LOW_TICK(id = 5, minApi = 31),
    THUD(id = 6, minApi = 31),
    SPIN(id = 7, minApi = 31);

    companion object {
        /** Parses an id arriving over JNI. Null rather than throwing on garbage. */
        fun fromId(id: Int): HapticPrimitive? = entries.firstOrNull { it.id == id }

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
