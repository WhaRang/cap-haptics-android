package com.cap.haptics.core.model

/**
 * Whether the device supports a given effect or primitive.
 *
 * The [UNKNOWN] state is not hedging -- it is a real platform condition. `createPredefined`
 * arrives in API 29 but `areEffectsSupported` only in API 30, so on API 29 exactly there is
 * no way to ask. The platform substitutes a generic fallback internally, so attempting the
 * effect is still the right move; we simply cannot promise it will feel as intended.
 *
 * Encoding that as UNKNOWN rather than guessing YES or NO keeps the diagnostics honest.
 */
enum class SupportLevel {
    YES,
    NO,
    UNKNOWN;

    /**
     * True when it is worth attempting. [UNKNOWN] counts as usable -- see the API 29 case
     * above; refusing to try would be strictly worse than trying and getting the platform's
     * generic fallback.
     */
    val usable: Boolean get() = this != NO
}
