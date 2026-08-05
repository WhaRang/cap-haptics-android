package com.cap.haptics.core

import com.cap.haptics.core.model.HapticTier

/**
 * Init-time options for [Haptics].
 *
 * @param verboseLogging routes debug logging to `adb logcat -s CapHaptics:V`. Off by
 *   default so a shipping app sees warnings and errors only.
 * @param forcedTier pins playback to a specific tier instead of the one the device would
 *   naturally get. **Debug affordance, not a production knob.** With a single modern test
 *   device, forcing T1 and T2 is the only way to exercise the fallback paths by hand.
 *
 *   Note this simulates the *code path*, not the device: the phone underneath is still
 *   whatever it is, so a forced T1 tells you what our waveform backend emits, not how that
 *   would feel through a 2018 ERM motor. Requests above the device's natural tier are
 *   clamped -- see `BackendFactory.resolveTier`.
 */
data class HapticsConfig(
    val verboseLogging: Boolean = false,
    val forcedTier: HapticTier? = null,
)
