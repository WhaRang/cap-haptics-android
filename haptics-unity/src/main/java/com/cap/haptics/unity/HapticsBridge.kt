package com.cap.haptics.unity

import android.app.Activity
import com.cap.haptics.core.Haptics
import com.cap.haptics.core.HapticsConfig
import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.ViewFeedback
import com.cap.haptics.core.model.Waveform

/**
 * The JNI surface. This is the ABI.
 *
 * Everything here is shaped by what `AndroidJavaObject.Call` can actually reach from C#, and
 * every rule below exists because breaking it produces a runtime failure with no compiler to
 * catch it (PLAN.md section 3.3):
 *
 * - **Primitives, Strings and primitive arrays only.** No Kotlin data classes, no `List<T>`,
 *   no nullable primitives. `Activity` is the single exception, and unavoidable.
 * - **No overloads.** JNI resolves by name plus runtime argument types, so overloads produce
 *   `NoSuchMethodError`s that look like the method is missing entirely. Hence `playPattern`
 *   and `playWaveform` rather than two `play`s.
 * - **No default arguments.** Kotlin compiles those to a synthetic `$default` method JNI
 *   cannot see, so the visible method has a different signature than the source suggests.
 * - **Nothing throws.** A Java exception unwinding through JNI into Unity is a native crash.
 *   Every method returns a result code; [getLastError] carries the detail.
 * - **A singleton with an explicit accessor**, because `object` field access from JNI is
 *   fiddlier than a plain static method.
 *
 * Result codes are [HapticResult.code]. Ids are the enums' `id`/`level` fields — see
 * [getEnumManifestJson] for the full mapping, which the C# side validates at init.
 */
class HapticsBridge private constructor() {

    @Volatile
    private var lastError: String = ""

    // ---------------------------------------------------------------- contract

    /**
     * The bridge ABI version. Check this from C# before anything else: a mismatch means the
     * AAR in `Plugins/Android` is not the one this C# was written against.
     */
    fun getBridgeVersion(): Int = BridgeVersion.CURRENT

    /** Every marshalled enum with its wire id, for the C# side to validate against. */
    fun getEnumManifestJson(): String = guardedString("getEnumManifestJson") {
        EnumManifest.toJson()
    }

    // ------------------------------------------------------------------ setup

    /**
     * @param activity pass `UnityPlayer.currentActivity`. An Activity rather than a Context
     *   because the system view-feedback channel needs a View.
     * @param verboseLogging routes SDK logging to `adb logcat -s CapHaptics:V`.
     */
    fun initialize(activity: Activity, verboseLogging: Boolean): Boolean =
        try {
            Haptics.initialize(activity, HapticsConfig(verboseLogging = verboseLogging))
        } catch (t: Throwable) {
            recordError("initialize", t)
            false
        }

    /** One JSON blob, read once per session. See [CapabilitiesJson] for why not on the hot path. */
    fun getCapabilitiesJson(): String = guardedString("getCapabilitiesJson") {
        CapabilitiesJson.of(
            capabilities = Haptics.capabilities,
            deviceTier = Haptics.deviceTier,
            activeTier = Haptics.activeTier,
            viewFeedbackAvailable = Haptics.isViewFeedbackAvailable,
            systemHapticsEnabled = Haptics.systemHapticsEnabled,
        )
    }

    fun isInitialized(): Boolean = Haptics.isInitialized

    /** What is playing back now. May be lower than [getDeviceTier] if a tier was forced. */
    fun getActiveTier(): Int = Haptics.activeTier.level

    /** What this device could do if nothing were forced. */
    fun getDeviceTier(): Int = Haptics.deviceTier.level

    /**
     * Debug affordance: pin playback to a lower tier to exercise the fallback paths.
     *
     * @param tier a [HapticTier] level, or any negative value to return to automatic
     *   selection. Requests above the device's natural tier are clamped, not rejected.
     * @return the tier actually in effect.
     */
    fun setForcedTier(tier: Int): Int = try {
        Haptics.setForcedTier(if (tier < 0) null else HapticTier.fromLevel(tier)).level
    } catch (t: Throwable) {
        recordError("setForcedTier", t)
        Haptics.activeTier.level
    }

    // -------------------------------------------------------------- playback

    /**
     * @param patternId a [HapticPattern] id.
     * @param intensityScale 0..1. Out-of-range values are clamped rather than rejected.
     */
    fun playPattern(patternId: Int, intensityScale: Float): Int = guarded("playPattern") {
        val pattern = HapticPattern.fromId(patternId)
        if (pattern == null) {
            lastError = "Unknown pattern id $patternId — is the C# enum stale?"
            HapticResult.INVALID_ARGUMENT
        } else {
            Haptics.playPattern(pattern, intensityScale)
        }
    }

    fun playEffect(effectId: Int): Int = guarded("playEffect") {
        val effect = PredefinedEffect.fromId(effectId)
        if (effect == null) {
            lastError = "Unknown effect id $effectId"
            HapticResult.INVALID_ARGUMENT
        } else {
            Haptics.playEffect(effect)
        }
    }

    fun playPrimitive(primitiveId: Int, scale: Float): Int = guarded("playPrimitive") {
        val primitive = HapticPrimitive.fromId(primitiveId)
        if (primitive == null) {
            lastError = "Unknown primitive id $primitiveId"
            HapticResult.INVALID_ARGUMENT
        } else {
            Haptics.playPrimitive(primitive, scale)
        }
    }

    /**
     * Three parallel arrays rather than a list of structs, because a Kotlin data class cannot
     * cross JNI without per-element marshalling. Ugly, and correct.
     */
    fun playComposition(primitiveIds: IntArray, scales: FloatArray, delaysMs: IntArray): Int =
        guarded("playComposition") {
            if (primitiveIds.size != scales.size || primitiveIds.size != delaysMs.size) {
                lastError = "Array lengths differ: ids=${primitiveIds.size}, " +
                    "scales=${scales.size}, delays=${delaysMs.size}"
                return@guarded HapticResult.INVALID_ARGUMENT
            }

            val steps = ArrayList<CompositionStep>(primitiveIds.size)
            primitiveIds.forEachIndexed { index, id ->
                val primitive = HapticPrimitive.fromId(id)
                if (primitive == null) {
                    lastError = "Unknown primitive id $id at index $index"
                    return@guarded HapticResult.INVALID_ARGUMENT
                }
                steps += CompositionStep(primitive, scales[index], delaysMs[index])
            }
            Haptics.playComposition(steps)
        }

    /**
     * @param amplitudes may be empty to mean "no amplitude information", since a null array
     *   across JNI is more trouble than it is worth.
     * @param repeatIndex -1 for no repeat. **A repeating waveform runs until [cancel].**
     */
    fun playWaveform(timingsMs: LongArray, amplitudes: IntArray, repeatIndex: Int): Int =
        guarded("playWaveform") {
            val effectiveAmplitudes = if (amplitudes.isEmpty()) null else amplitudes
            val waveform = Waveform.create(timingsMs, effectiveAmplitudes, repeatIndex)
            if (waveform == null) {
                lastError = Waveform.validate(timingsMs, effectiveAmplitudes, repeatIndex)
                    ?: "invalid waveform"
                HapticResult.INVALID_ARGUMENT
            } else {
                Haptics.playWaveform(waveform)
            }
        }

    /**
     * The system channel. Unlike everything else here it obeys the user's haptic settings and
     * can report [HapticResult.SUPPRESSED] when they have them switched off.
     */
    fun performViewFeedback(feedbackId: Int): Int = guarded("performViewFeedback") {
        val feedback = ViewFeedback.fromId(feedbackId)
        if (feedback == null) {
            lastError = "Unknown view feedback id $feedbackId"
            HapticResult.INVALID_ARGUMENT
        } else {
            Haptics.performViewFeedback(feedback)
        }
    }

    fun cancel() {
        try {
            Haptics.cancel()
        } catch (t: Throwable) {
            recordError("cancel", t)
        }
    }

    /**
     * Detail for the last failure, or empty. Not cleared on success — it describes the last
     * thing that went wrong, not the last call.
     */
    fun getLastError(): String = lastError

    // -------------------------------------------------------------- internals

    private inline fun guarded(operation: String, block: () -> HapticResult): Int = try {
        block().code
    } catch (t: Throwable) {
        recordError(operation, t)
        HapticResult.PLATFORM_ERROR.code
    }

    private inline fun guardedString(operation: String, block: () -> String): String = try {
        block()
    } catch (t: Throwable) {
        recordError(operation, t)
        ""
    }

    private fun recordError(operation: String, t: Throwable) {
        lastError = "$operation: ${t::class.java.simpleName}: ${t.message}"
    }

    companion object {
        private val INSTANCE = HapticsBridge()

        /**
         * From C#:
         * ```
         * using var bridge = new AndroidJavaClass("com.cap.haptics.unity.HapticsBridge")
         *     .CallStatic<AndroidJavaObject>("getInstance");
         * ```
         */
        @JvmStatic
        fun getInstance(): HapticsBridge = INSTANCE
    }
}
