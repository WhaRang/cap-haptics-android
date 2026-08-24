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
 * The cap-haptics SDK's public entry point.
 *
 * ### Usage
 * ```
 * Haptics.initialize(activity)
 * Haptics.playPattern(HapticPattern.SUCCESS)
 * ```
 *
 * Ask for a *meaning* and the library decides how to render it on the hardware in front of
 * it — composing primitives on a modern LRA, falling back through platform-tuned effects to
 * plain waveforms on weaker motors.
 *
 * ### Guarantees
 *
 * - **Nothing here throws.** Every public method returns a [HapticResult] instead. An
 *   exception unwinding into a game engine through JNI is a native crash, and a haptic
 *   effect is never worth crashing over.
 * - **Safe before [initialize].** Calls return [HapticResult.NOT_INITIALIZED] rather than
 *   failing.
 * - **Safe on a device with no motor.** Calls return [HapticResult.NO_VIBRATOR].
 * - **Playback is lock-free.** [initialize] and [setForcedTier] serialise against each
 *   other, but the playback path only reads a volatile reference, so haptics never block on
 *   configuration.
 *
 * ### What a result code does and does not tell you
 *
 * [HapticResult.OK] means the platform accepted the effect — not that the user felt it. The
 * `Vibrator` path cannot know: system settings, OEM intensity sliders, Do Not Disturb and
 * battery saver all suppress output silently. The one exception is
 * [HapticResult.SUPPRESSED] from [performViewFeedback], which reports the platform's real
 * answer.
 *
 * ### Lifecycle
 *
 * A plain object with no Android lifecycle attachment, so Unity's Arch ECS systems can call
 * it directly from anywhere. [initialize] is idempotent — calling it again re-probes and
 * rebuilds, which is what you want after a configuration change.
 */
object Haptics {

    /** Guards reconfiguration only. The playback path never takes it. */
    private val lock = Any()

    @Volatile
    private var backend: HapticBackend = NoOpBackend

    @Volatile
    private var vibrator: Vibrator? = null

    @Volatile
    private var viewFeedback: ViewFeedbackChannel? = null

    @Volatile
    private var config: HapticsConfig = HapticsConfig()

    /** Null until [initialize] runs. */
    @Volatile
    var capabilities: HapticCapabilities? = null
        private set

    /** What this device could support if nothing were forced. */
    @Volatile
    var deviceTier: HapticTier = HapticTier.NONE
        private set

    /** What is actually playing back right now — may be lower than [deviceTier]. */
    val activeTier: HapticTier get() = backend.tier

    val isInitialized: Boolean get() = capabilities != null

    /**
     * True when [initialize] was given an Activity. Without one there is no View to call
     * `performHapticFeedback` on, so gesture patterns fall back to the Vibrator path.
     */
    val isViewFeedbackAvailable: Boolean get() = viewFeedback != null

    /**
     * The user's system-wide haptic preference, or null when unreadable.
     *
     * Advisory only: OEM skins add intensity controls no third-party app can see.
     * [HapticResult.SUPPRESSED] from a view-feedback call is the authoritative signal.
     */
    @Volatile
    var systemHapticsEnabled: Boolean? = null
        private set

    /**
     * Probes the device and selects a backend. Idempotent.
     *
     * @param context an Activity unlocks the system view-feedback channel; any other
     *   Context works fine without it.
     * @return false when the device has no usable vibrator. Not an error — playback still
     *   works, it simply does nothing.
     */
    @JvmStatic
    fun initialize(context: Context, config: HapticsConfig = HapticsConfig()): Boolean =
        synchronized(lock) {
            try {
                this.config = config
                HLog.verbose = config.verboseLogging

                val appContext = context.applicationContext ?: context
                val probed = CapabilityProbe.probe(appContext)

                vibrator = VibratorProvider.get(appContext)
                viewFeedback = (context as? Activity)?.let { ViewFeedbackChannel(it) }
                systemHapticsEnabled = SystemHapticsSettings.touchFeedbackEnabled(appContext)

                capabilities = probed
                deviceTier = TierSelector.select(probed)
                backend = BackendFactory.create(vibrator, probed, config.forcedTier, viewFeedback)

                HLog.d("Initialized: device T${deviceTier.level}, active T${activeTier.level}")
                probed.hasVibrator
            } catch (t: Throwable) {
                // Leaves the SDK in its safe default state rather than half-configured.
                HLog.e("initialize() failed; haptics disabled", t)
                backend = NoOpBackend
                capabilities = null
                false
            }
        }

    /**
     * Swaps the forced tier without re-probing. Pass null to return to automatic selection.
     *
     * A debug affordance: with a single modern test device, forcing a lower tier is the only
     * way to exercise the fallback paths by hand. Requests above the device's natural tier
     * are clamped — see `BackendFactory.resolveTier`.
     *
     * @return the tier actually in effect, which may be lower than requested.
     */
    @JvmStatic
    fun setForcedTier(tier: HapticTier?): HapticTier = synchronized(lock) {
        try {
            val probed = capabilities
            if (probed == null) {
                HLog.w("setForcedTier before initialize(); ignored")
                return HapticTier.NONE
            }
            config = config.copy(forcedTier = tier)
            backend = BackendFactory.create(vibrator, probed, tier, viewFeedback)
            activeTier
        } catch (t: Throwable) {
            HLog.e("setForcedTier() failed", t)
            activeTier
        }
    }

    /**
     * The main entry point: play a semantic pattern, rendered however the active tier can.
     *
     * @param intensity 0..1, applied inside the library so its meaning stays consistent
     *   across tiers. Reduces the authored rendering, never strengthens it. Note 0 is the
     *   weakest *perceptible* setting rather than silence — a caller wanting nothing should
     *   not call this. Out-of-range and NaN values are clamped, not rejected.
     */
    @JvmStatic
    fun playPattern(pattern: HapticPattern, intensity: Float = 1f): HapticResult =
        guarded("playPattern($pattern)") { backend.playPattern(pattern, intensity) }

    /**
     * Plays one of the four platform-tuned effects, natively at T2 and above and as a
     * waveform approximation below.
     */
    @JvmStatic
    fun playEffect(effect: PredefinedEffect): HapticResult =
        guarded("playEffect($effect)") { backend.playEffect(effect) }

    /**
     * Plays a sequence of primitives, composed natively at T3 and flattened to a single
     * waveform below. Unsupported primitives are substituted individually rather than
     * sinking the whole composition.
     */
    @JvmStatic
    fun playComposition(steps: List<CompositionStep>): HapticResult =
        guarded("playComposition") { backend.playComposition(steps) }

    /** Convenience for the single-primitive case. */
    @JvmStatic
    fun playPrimitive(primitive: HapticPrimitive, scale: Float = 1f): HapticResult =
        playComposition(listOf(CompositionStep(primitive, scale)))

    /**
     * Plays a caller-authored envelope. Build one with [Waveform.create], which validates
     * the arrays the platform would otherwise throw on.
     */
    @JvmStatic
    fun playWaveform(waveform: Waveform): HapticResult =
        guarded("playWaveform") { backend.playWaveform(waveform) }

    /**
     * Plays a UI-gesture haptic through the system channel.
     *
     * Unlike everything else here this obeys the user's haptic settings, and is the only
     * call that can report [HapticResult.SUPPRESSED] when they have them switched off.
     * There is no intensity control.
     */
    @JvmStatic
    fun performViewFeedback(feedback: ViewFeedback): HapticResult =
        guarded("performViewFeedback($feedback)") {
            val channel = viewFeedback
            if (channel == null) {
                HLog.w("No view feedback channel; initialize() was not given an Activity")
                HapticResult.UNSUPPORTED_PATTERN
            } else {
                channel.perform(feedback)
            }
        }

    /**
     * Stops anything currently playing.
     *
     * Worth calling before quitting if you ever use a repeating waveform: those loop until
     * cancelled, and nothing else stops them.
     */
    @JvmStatic
    fun cancel() {
        try {
            backend.cancel()
        } catch (t: Throwable) {
            HLog.e("cancel() failed", t)
        }
    }

    /**
     * The outermost no-throw boundary.
     *
     * The backends already catch platform failures, so in practice this catches the things
     * nobody predicted: a Java caller passing a list containing nulls past Kotlin's type
     * system, an OEM `Vibrator` throwing something undocumented, a state race during
     * re-initialisation. Whatever it is, a haptic effect is not worth a crash.
     */
    private inline fun guarded(operation: String, block: () -> HapticResult): HapticResult {
        if (!isInitialized) {
            HLog.w("$operation before initialize()")
            return HapticResult.NOT_INITIALIZED
        }
        return try {
            block()
        } catch (t: Throwable) {
            HLog.e("$operation threw", t)
            HapticResult.PLATFORM_ERROR
        }
    }
}
