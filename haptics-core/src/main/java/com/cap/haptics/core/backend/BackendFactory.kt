package com.cap.haptics.core.backend

import android.os.Build
import android.os.Vibrator
import com.cap.haptics.core.feedback.ViewFeedbackChannel
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
        viewFeedback: ViewFeedbackChannel? = null,
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
            HapticTier.WAVEFORM -> WaveformBackend(vibrator, capabilities, viewFeedback)
            HapticTier.PREDEFINED -> predefined(vibrator, capabilities, viewFeedback)
            HapticTier.COMPOSED -> composed(vibrator, capabilities, viewFeedback)
        }
    }

    /**
     * The SDK_INT checks below are redundant -- `TierSelector` only returns PREDEFINED at
     * API 29+ and COMPOSED at API 30+ -- but lint cannot see those invariants, and a
     * defensive fallback costs nothing.
     */
    private fun predefined(
        vibrator: Vibrator,
        capabilities: HapticCapabilities,
        viewFeedback: ViewFeedbackChannel?,
    ): HapticBackend = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PredefinedBackend(vibrator, capabilities, viewFeedback)
    } else {
        WaveformBackend(vibrator, capabilities, viewFeedback)
    }

    private fun composed(
        vibrator: Vibrator,
        capabilities: HapticCapabilities,
        viewFeedback: ViewFeedbackChannel?,
    ): HapticBackend = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        ComposedBackend(vibrator, capabilities, viewFeedback)
    } else {
        predefined(vibrator, capabilities, viewFeedback)
    }
}
