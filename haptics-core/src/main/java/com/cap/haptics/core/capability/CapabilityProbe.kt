package com.cap.haptics.core.capability

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Vibrator
import androidx.annotation.RequiresApi
import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.SupportLevel
import com.cap.haptics.core.util.HLog

/**
 * Reads the device's real haptic capabilities into a [HapticCapabilities] snapshot.
 *
 * This is the *only* class in the library that interrogates the platform for capabilities.
 * Everything downstream consumes the snapshot, which is what makes the rest of the SDK
 * testable without a device. Keep it that way.
 *
 * Probing never throws: a device that answers strangely produces a conservative snapshot
 * and a log line, not a crash on a user's phone.
 *
 * Note that every version guard below reads `Build.VERSION.SDK_INT` directly rather than a
 * passed-in copy. That is not stylistic -- lint's flow analysis only narrows the API level
 * when it can see the field access itself, and hoisting it into a parameter silently
 * disables the NewApi check on everything downstream.
 */
object CapabilityProbe {

    @JvmStatic
    fun probe(context: Context): HapticCapabilities {
        val sdkInt = Build.VERSION.SDK_INT
        return try {
            val vibrator = VibratorProvider.get(context)
            if (vibrator == null || !vibrator.hasVibrator()) {
                HLog.d("No vibrator present")
                HapticCapabilities.none(sdkInt)
            } else {
                HapticCapabilities(
                    sdkInt = sdkInt,
                    hasVibrator = true,
                    hasAmplitudeControl = vibrator.hasAmplitudeControl(),
                    vibratorCount = VibratorProvider.count(context),
                    effectSupport = probeEffects(vibrator),
                    primitiveSupport = probePrimitives(vibrator),
                    primitiveDurationsMs = probeDurations(vibrator),
                ).also { HLog.d("Capabilities probed:\n${it.summary()}") }
            }
        } catch (t: Throwable) {
            HLog.e("Capability probe failed; assuming no vibrator", t)
            HapticCapabilities.none(sdkInt)
        }
    }

    /**
     * API 29 is the awkward one: `createPredefined` exists but `areEffectsSupported` does
     * not arrive until 30, so there is genuinely no way to ask. UNKNOWN, not a guess.
     */
    // WrongConstant: the platform parameter carries a hidden @IntDef which the enum-to-Int
    // mapping erases. The values are exactly those constants -- see PlatformIds.
    @SuppressLint("WrongConstant")
    private fun probeEffects(vibrator: Vibrator): Map<PredefinedEffect, SupportLevel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return PredefinedEffect.entries.associateWith { SupportLevel.NO }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return PredefinedEffect.entries.associateWith { SupportLevel.UNKNOWN }
        }

        val effects = PredefinedEffect.entries
        val results = vibrator.areEffectsSupported(
            *effects.map { it.platformId() }.toIntArray()
        )
        return effects.withIndex().associate { (index, effect) ->
            effect to results.getOrNull(index).toSupportLevel()
        }
    }

    /**
     * Queried per-primitive, not as one boolean: LOW_TICK / THUD / SPIN only exist from API
     * 31, and a motor may support CLICK while refusing THUD. A4's substitution map depends
     * on knowing precisely which.
     */
    @SuppressLint("WrongConstant")
    private fun probePrimitives(vibrator: Vibrator): Map<HapticPrimitive, SupportLevel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return HapticPrimitive.entries.associateWith { SupportLevel.NO }
        }

        val queryable = HapticPrimitive.entries.filter { it.minApi <= Build.VERSION.SDK_INT }
        val supported = vibrator.arePrimitivesSupported(
            *queryable.map { it.platformId() }.toIntArray()
        )

        return HapticPrimitive.entries.associateWith { primitive ->
            val index = queryable.indexOf(primitive)
            when {
                // Newer than this OS -- definitively unavailable, not unknown.
                index < 0 -> SupportLevel.NO
                supported.getOrNull(index) == true -> SupportLevel.YES
                else -> SupportLevel.NO
            }
        }
    }

    /**
     * Best-effort. Real primitive durations let A5 time compositions against the hardware
     * instead of guessing delays, but the query only exists from API 31 -- an empty map is
     * a perfectly acceptable answer.
     */
    @SuppressLint("WrongConstant")
    private fun probeDurations(vibrator: Vibrator): Map<HapticPrimitive, Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyMap()

        return runCatching {
            val queryable = HapticPrimitive.entries.filter { it.minApi <= Build.VERSION.SDK_INT }
            val durations = vibrator.getPrimitiveDurations(
                *queryable.map { it.platformId() }.toIntArray()
            )
            queryable.withIndex().mapNotNull { (index, primitive) ->
                durations.getOrNull(index)?.takeIf { it > 0 }?.let { primitive to it }
            }.toMap()
        }.onFailure {
            HLog.w("Primitive duration query failed", it)
        }.getOrDefault(emptyMap())
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun Int?.toSupportLevel(): SupportLevel = when (this) {
        Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> SupportLevel.YES
        Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> SupportLevel.NO
        else -> SupportLevel.UNKNOWN
    }
}
