package com.cap.haptics.core

import android.app.Activity
import android.content.Context
import android.os.Vibrator
import com.cap.haptics.core.backend.BackendFactory
import com.cap.haptics.core.backend.HapticBackend
import com.cap.haptics.core.backend.NoOpBackend
import com.cap.haptics.core.capability.CapabilityProbe
import com.cap.haptics.core.capability.VibratorProvider
import com.cap.haptics.core.feedback.ViewFeedbackChannel
import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.TierSelector
import com.cap.haptics.core.model.ViewFeedback
import com.cap.haptics.core.model.Waveform
import com.cap.haptics.core.util.HLog
import com.cap.haptics.core.util.SystemHapticsSettings

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
    private var viewFeedback: ViewFeedbackChannel? = null
    private var config: HapticsConfig = HapticsConfig()

    /**
     * True when [initialize] was given an Activity. Without one there is no View to call
     * `performHapticFeedback` on, so gesture patterns fall back to the Vibrator path.
     */
    val isViewFeedbackAvailable: Boolean get() = viewFeedback != null

    /**
     * The user's system-wide haptic preference, or null when unreadable.
     *
     * Advisory only: OEM skins add their own intensity controls that no third-party app can
     * see. [HapticResult.SUPPRESSED] from a view-feedback call is the authoritative signal.
     */
    @Volatile
    var systemHapticsEnabled: Boolean? = null
        private set

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
        // An Activity is optional. Callers that only have an application context simply get
        // no view feedback rather than a failed init -- Unity supplies `currentActivity`.
        viewFeedback = (context as? Activity)?.let { ViewFeedbackChannel(it) }
        systemHapticsEnabled = SystemHapticsSettings.touchFeedbackEnabled(appContext)

        capabilities = probed
        deviceTier = TierSelector.select(probed)
        backend = BackendFactory.create(vibrator, probed, config.forcedTier, viewFeedback)

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
        backend = BackendFactory.create(vibrator, probed, tier, viewFeedback)
        return activeTier
    }

    /**
     * Plays a UI-gesture haptic through the system channel.
     *
     * Unlike everything else here, this obeys the user's haptic settings and can tell you
     * when it was declined -- [HapticResult.SUPPRESSED]. There is no intensity control.
     */
    @JvmStatic
    fun performViewFeedback(feedback: ViewFeedback): HapticResult {
        val channel = viewFeedback
        if (channel == null) {
            HLog.w("No view feedback channel; initialize() was not given an Activity")
            return HapticResult.UNSUPPORTED_PATTERN
        }
        return channel.perform(feedback)
    }

    @JvmStatic
    fun playWaveform(waveform: Waveform): HapticResult {
        if (!isInitialized) {
            HLog.w("playWaveform before initialize()")
            return HapticResult.NOT_INITIALIZED
        }
        return backend.playWaveform(waveform)
    }

    /**
     * Plays one of the four platform-tuned effects, natively at T2 and above and as a
     * waveform approximation below. Never returns [HapticResult.UNSUPPORTED_PATTERN] for a
     * device that has a vibrator.
     */
    @JvmStatic
    fun playEffect(effect: PredefinedEffect): HapticResult {
        if (!isInitialized) {
            HLog.w("playEffect before initialize()")
            return HapticResult.NOT_INITIALIZED
        }
        return backend.playEffect(effect)
    }

    /**
     * The main entry point: play a semantic pattern, rendered however the active tier can.
     *
     * @param intensity 0..1, applied inside the library so its meaning stays consistent
     *   across tiers. Reduces the authored rendering, never strengthens it. Note the
     *   predefined tier has no intensity knob beyond swapping to a lighter effect, so
     *   non-impact patterns ignore it at T2.
     */
    @JvmStatic
    fun playPattern(pattern: HapticPattern, intensity: Float = 1f): HapticResult {
        if (!isInitialized) {
            HLog.w("playPattern before initialize()")
            return HapticResult.NOT_INITIALIZED
        }
        return backend.playPattern(pattern, intensity)
    }

    /**
     * Plays a sequence of primitives, composed natively at T3 and flattened to a waveform
     * below. Unsupported primitives are substituted individually rather than sinking the
     * whole composition.
     */
    @JvmStatic
    fun playComposition(steps: List<CompositionStep>): HapticResult {
        if (!isInitialized) {
            HLog.w("playComposition before initialize()")
            return HapticResult.NOT_INITIALIZED
        }
        return backend.playComposition(steps)
    }

    /** Convenience for the single-primitive case. */
    @JvmStatic
    fun playPrimitive(primitive: HapticPrimitive, scale: Float = 1f): HapticResult =
        playComposition(listOf(CompositionStep(primitive, scale)))

    @JvmStatic
    fun cancel() {
        backend.cancel()
    }
}
