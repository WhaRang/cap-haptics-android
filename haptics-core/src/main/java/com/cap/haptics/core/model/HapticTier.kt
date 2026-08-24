package com.cap.haptics.core.model

/**
 * The playback strategy chosen for this device.
 *
 * Selected once at init from a [HapticCapabilities] snapshot, never re-decided per call.
 *
 * [level] is the stable integer form. It crosses the JNI boundary and drives the
 * forced-tier debug override, so these numbers are part of the ABI -- append new
 * tiers, never renumber existing ones.
 */
enum class HapticTier(val level: Int) {

    /** No vibrator on this device. Playback is a well-behaved no-op. */
    NONE(0),

    /** T1 -- `createWaveform`, amplitude only if the motor supports it. The API 26 floor. */
    WAVEFORM(1),

    /** T2 -- `createPredefined`, OEM-tuned constants. API 29+. */
    PREDEFINED(2),

    /** T3 -- `Composition`, crisp and hardware-tuned. API 30+ *and* primitive support. */
    COMPOSED(3);

    companion object {
        /** Returns null rather than throwing: this parses untrusted input from the bridge. */
        fun fromLevel(level: Int): HapticTier? = entries.firstOrNull { it.level == level }
    }
}
