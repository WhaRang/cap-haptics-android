package com.cap.haptics.core.backend

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import com.cap.haptics.core.capability.platformId
import com.cap.haptics.core.feedback.ViewFeedbackChannel
import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.SupportLevel
import com.cap.haptics.core.util.HLog

/**
 * T2 -- `VibrationEffect.createPredefined`. API 29+.
 *
 * Extends [WaveformBackend] rather than reimplementing it: a device that can play predefined
 * effects can also play raw waveforms, so inheriting the T1 path both avoids duplication and
 * gives this tier a place to fall back *to* when an individual effect is unsupported.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal open class PredefinedBackend(
    vibrator: Vibrator,
    capabilities: HapticCapabilities,
    viewFeedback: ViewFeedbackChannel? = null,
) : WaveformBackend(vibrator, capabilities, viewFeedback) {

    override val tier: HapticTier get() = HapticTier.PREDEFINED

    // WrongConstant: the @IntDef is erased by the enum mapping -- see PlatformIds.
    @SuppressLint("WrongConstant")
    override fun playEffect(effect: PredefinedEffect): HapticResult {
        // Per-effect, not per-tier. areEffectsSupported can report NO for one effect on an
        // otherwise perfectly capable device, and UNKNOWN (API 29) is worth attempting.
        if (capabilities.supportOf(effect) == SupportLevel.NO) {
            HLog.d("$effect reported unsupported; falling back to approximation")
            return super.playEffect(effect)
        }

        return try {
            vibrator.vibrate(VibrationEffect.createPredefined(effect.platformId()))
            HLog.d("T${tier.level} predefined $effect")
            HapticResult.OK
        } catch (t: Throwable) {
            // Degrade rather than report failure: the user should feel *something*.
            HLog.e("Predefined playback failed for $effect; approximating", t)
            super.playEffect(effect)
        }
    }
}
