package com.cap.haptics.core

import android.content.Context
import android.os.Vibrator
import com.cap.haptics.core.backend.BackendFactory
import com.cap.haptics.core.backend.HapticBackend
import com.cap.haptics.core.backend.NoOpBackend
import com.cap.haptics.core.capability.CapabilityProbe
import com.cap.haptics.core.capability.VibratorProvider
import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.TierSelector
import com.cap.haptics.core.model.Waveform
import com.cap.haptics.core.util.HLog

/**
 * The SDK's public entry point.
 *
 * Brought forward from A7 because A2 needs *something* public for the harness to call, and
 * exposing `BackendFactory` would have leaked an internal. A7 hardens this: KDoc pass,
 * fuzzing, and the no-throw audit. A5 adds `playPattern`.
 *
 * Deliberately a plain object with no Android lifecycle attachment -- Unity's Arch ECS
 * systems need to call it directly, and a `MonoBehaviour` singleton would not survive that.
 */
object Haptics {

    @Volatile
    private var backend: HapticBackend = NoOpBackend

    private var vibrator: Vibrator? = null
    private var config: HapticsConfig = HapticsConfig()

    /** Null until [initialize] succeeds. */
    @Volatile
    var capabilities: HapticCapabilities? = null
        private set

    /** What this device could support if nothing were forced. */
    @Volatile
    var deviceTier: HapticTier = HapticTier.NONE
        private set

    /** What is actually playing back right now -- may be lower than [deviceTier]. */
    val activeTier: HapticTier get() = backend.tier

    val isInitialized: Boolean get() = capabilities != null

    /**
     * Probes the device and selects a backend. Idempotent -- calling it again re-probes and
     * rebuilds, which is exactly what you want after a config change.
     *
     * @return false when the device turns out to have no usable vibrator. Not an error
     *   condition: playback still works, it simply does nothing.
     */
    @JvmStatic
    fun initialize(context: Context, config: HapticsConfig = HapticsConfig()): Boolean {
        this.config = config
        HLog.verbose = config.verboseLogging

        val appContext = context.applicationContext
        val probed = CapabilityProbe.probe(appContext)

        vibrator = VibratorProvider.get(appContext)
        capabilities = probed
        deviceTier = TierSelector.select(probed)
        backend = BackendFactory.create(vibrator, probed, config.forcedTier)

        HLog.d("Initialized: device tier T${deviceTier.level}, active T${activeTier.level}")
        return probed.hasVibrator
    }

    /**
     * Swaps the forced tier without re-probing. Pass null to return to automatic selection.
     *
     * @return the tier actually in effect, which may be lower than requested.
     */
    @JvmStatic
    fun setForcedTier(tier: HapticTier?): HapticTier {
        val probed = capabilities
        if (probed == null) {
            HLog.w("setForcedTier before initialize(); ignored")
            return HapticTier.NONE
        }
        config = config.copy(forcedTier = tier)
        backend = BackendFactory.create(vibrator, probed, tier)
        return activeTier
    }

    @JvmStatic
    fun playWaveform(waveform: Waveform): HapticResult {
        if (!isInitialized) {
            HLog.w("playWaveform before initialize()")
            return HapticResult.NOT_INITIALIZED
        }
        return backend.playWaveform(waveform)
    }

    @JvmStatic
    fun cancel() {
        backend.cancel()
    }
}
