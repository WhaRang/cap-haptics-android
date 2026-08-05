package com.cap.haptics.core.backend

import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.Waveform

/**
 * One playback strategy. Chosen once at init by [BackendFactory], never per call.
 *
 * A5 adds `play(Rendering)` for semantic patterns; raw waveform playback stays here because
 * it is available at every tier from the API 26 floor upward.
 */
internal interface HapticBackend {

    /** The tier this instance actually implements -- not what the device could support. */
    val tier: HapticTier

    fun playWaveform(waveform: Waveform): HapticResult

    fun cancel()
}
