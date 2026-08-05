package com.cap.haptics.core.backend

import android.os.VibrationEffect
import android.os.Vibrator
import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.Waveform
import com.cap.haptics.core.util.HLog

/**
 * T1 -- `VibrationEffect.createWaveform`. The API 26 floor, so this always works.
 *
 * Deliberately `open`: a T3 device can do everything a T1 device can, so A3's and A4's
 * backends extend this one and add their tier's capability on top rather than duplicating
 * raw waveform playback. That mirrors how the platform itself layers.
 */
internal open class WaveformBackend(
    protected val vibrator: Vibrator,
    protected val capabilities: HapticCapabilities,
) : HapticBackend {

    override val tier: HapticTier get() = HapticTier.WAVEFORM

    override fun playWaveform(waveform: Waveform): HapticResult {
        // The motor decides whether amplitude data is meaningful. Dropping it keeps the
        // rhythm intact, which carries most of a pattern's identity.
        val effective =
            if (waveform.amplitudes != null && !capabilities.hasAmplitudeControl) {
                waveform.withoutAmplitudes()
            } else {
                waveform
            }

        return try {
            val amplitudes = effective.amplitudes
            val effect = if (amplitudes != null) {
                VibrationEffect.createWaveform(
                    effective.timingsMs,
                    amplitudes,
                    effective.repeatIndex,
                )
            } else {
                VibrationEffect.createWaveform(effective.timingsMs, effective.repeatIndex)
            }
            vibrator.vibrate(effect)
            HLog.d("T${tier.level} waveform: $effective")
            HapticResult.OK
        } catch (t: Throwable) {
            HLog.e("Waveform playback failed for $effective", t)
            HapticResult.PLATFORM_ERROR
        }
    }

    override fun cancel() {
        runCatching { vibrator.cancel() }
            .onFailure { HLog.w("cancel() failed", it) }
    }
}
