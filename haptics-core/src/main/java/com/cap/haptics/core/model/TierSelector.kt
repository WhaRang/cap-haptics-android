package com.cap.haptics.core.model

/**
 * Decides which playback tier a device should use.
 *
 * A pure function over [HapticCapabilities], deliberately separate from backend
 * construction (`BackendFactory`, which needs a live `Vibrator`). Keeping the
 * *decision* pure and the *instantiation* impure means the interesting half is
 * exhaustively unit-testable without hardware.
 *
 * The ordering rule: version is a precondition, never the decision. Every gate below checks
 * an SDK level *and* a capability, because an API 31 phone with a cheap ERM motor reports no
 * primitive support and belongs on T2 despite passing the version check.
 */
object TierSelector {

    fun select(capabilities: HapticCapabilities): HapticTier {
        if (!capabilities.hasVibrator) return HapticTier.NONE

        // T3: needs the composition API *and* a motor that can actually render the core
        // primitives. Optional primitives (THUD, SPIN, ...) are handled by substitution.
        val coreComposable = HapticPrimitive.CORE.all {
            capabilities.supportOf(it) == SupportLevel.YES
        }
        if (capabilities.sdkInt >= 30 && coreComposable) return HapticTier.COMPOSED

        // T2: UNKNOWN counts here -- on API 29 the query does not exist and the platform
        // substitutes its own fallback, which still beats hand-rolled waveforms.
        val anyEffectUsable = PredefinedEffect.entries.any { capabilities.supportOf(it).usable }
        if (capabilities.sdkInt >= 29 && anyEffectUsable) return HapticTier.PREDEFINED

        // T1: the minSdk 26 floor. VibrationEffect always exists here.
        return HapticTier.WAVEFORM
    }
}
