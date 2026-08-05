package com.cap.haptics.core.backend

import android.os.Vibrator
import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.TierSelector
import com.cap.haptics.core.util.HLog

/**
 * Turns a capability snapshot into a live backend.
 *
 * The *decision* half ([resolveTier]) is pure and exhaustively tested; the *instantiation*
 * half needs a real `Vibrator` and cannot be. Splitting them is what lets the interesting
 * logic be verified on a machine with no phone attached.
 */
internal object BackendFactory {

    /**
     * Resolves the tier actually used, given what the device supports and what the caller
     * asked for.
     *
     * **The override can only ever go down.** Forcing a tier above the device's natural one
     * would mean calling platform APIs that are absent or unsupported -- the failure would
     * land inside the platform, far from the mistake. Requests above the natural tier are
     * clamped instead.
     *
     * Forcing [HapticTier.NONE] is allowed and useful: it simulates a device with no motor.
     */
    fun resolveTier(natural: HapticTier, forced: HapticTier?): HapticTier = when {
        forced == null -> natural
        // Nothing to simulate on a device that genuinely cannot vibrate.
        natural == HapticTier.NONE -> HapticTier.NONE
        forced.level > natural.level -> natural
        else -> forced
    }

    fun create(
        vibrator: Vibrator?,
        capabilities: HapticCapabilities,
        forcedTier: HapticTier?,
    ): HapticBackend {
        val natural = TierSelector.select(capabilities)
        val resolved = resolveTier(natural, forcedTier)

        if (forcedTier != null && forcedTier != resolved) {
            HLog.w(
                "Forced tier T${forcedTier.level} clamped to T${resolved.level}: " +
                    "device supports at most T${natural.level}"
            )
        }

        if (vibrator == null || resolved == HapticTier.NONE) return NoOpBackend

        return when (resolved) {
            HapticTier.NONE -> NoOpBackend
            HapticTier.WAVEFORM -> WaveformBackend(vibrator, capabilities)

            // A3 and A4 replace these. Until then they degrade to T1 rather than failing --
            // and say so, so a missing backend never masquerades as a working one.
            HapticTier.PREDEFINED, HapticTier.COMPOSED -> {
                HLog.w("T${resolved.level} backend not implemented yet; falling back to T1")
                WaveformBackend(vibrator, capabilities)
            }
        }
    }
}
