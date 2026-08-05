package com.cap.haptics.core.backend

import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.Waveform

/**
 * Used when the device has no vibrator.
 *
 * Playback stays callable and returns a truthful result code rather than forcing every
 * caller to null-check. An SDK that is unsafe to call on some devices is a worse SDK.
 */
internal object NoOpBackend : HapticBackend {

    override val tier: HapticTier get() = HapticTier.NONE

    override fun playWaveform(waveform: Waveform): HapticResult = HapticResult.NO_VIBRATOR

    override fun cancel() = Unit
}
