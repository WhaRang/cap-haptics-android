package com.cap.haptics.core.capability

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import androidx.annotation.RequiresApi
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.PredefinedEffect

/**
 * Maps the library's pure model enums onto platform constants.
 *
 * This mapping lives here rather than on the enums themselves so that
 * [com.cap.haptics.core.model] stays free of `android.*` and therefore JVM-testable.
 */

@RequiresApi(Build.VERSION_CODES.Q)
internal fun PredefinedEffect.platformId(): Int = when (this) {
    PredefinedEffect.TICK -> VibrationEffect.EFFECT_TICK
    PredefinedEffect.CLICK -> VibrationEffect.EFFECT_CLICK
    PredefinedEffect.DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
    PredefinedEffect.HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
}

/**
 * Callers must filter by [HapticPrimitive.minApi] first -- the last three entries are API 31
 * constants and asking about them on an API 30 device is a programming error, not a
 * runtime condition to handle.
 *
 * InlinedApi fires on those three because they are API 31 fields read inside an API 30
 * function. Reading them is safe at any level -- they are compile-time constants that get
 * inlined into the class file. What would be unsafe is *passing* one to a platform call on a
 * device that predates it, and the minApi filter in `CapabilityProbe` is what prevents that.
 */
@SuppressLint("InlinedApi")
@RequiresApi(Build.VERSION_CODES.R)
internal fun HapticPrimitive.platformId(): Int = when (this) {
    HapticPrimitive.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
    HapticPrimitive.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
    HapticPrimitive.QUICK_RISE -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
    HapticPrimitive.SLOW_RISE -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
    HapticPrimitive.QUICK_FALL -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
    HapticPrimitive.LOW_TICK -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
    HapticPrimitive.THUD -> VibrationEffect.Composition.PRIMITIVE_THUD
    HapticPrimitive.SPIN -> VibrationEffect.Composition.PRIMITIVE_SPIN
}
