package com.cap.haptics.core.model

/**
 * An immutable snapshot of what this device's haptics hardware can actually do.
 *
 * This class is the seam that makes the SDK testable. `CapabilityProbe` is the only code in
 * the library that reads the platform; everything downstream -- tier selection, the pattern
 * registry, intensity scaling, primitive substitution -- is a pure function over one of
 * these. That means "what happens on an API 26 device with no amplitude control" is a JVM
 * unit test, not a device you have to go and buy.
 *
 * Given the project has exactly one test device, and it sits at the top tier, this is not a
 * nicety. It is how the fallback paths get verified at all.
 *
 * Note there are no `android.*` types here on purpose.
 */
data class HapticCapabilities(
    /** `Build.VERSION.SDK_INT` at probe time. */
    val sdkInt: Int,

    /** False on tablets and devices with no motor. Playback degrades to a no-op. */
    val hasVibrator: Boolean,

    /**
     * Whether the motor honours per-step amplitude. When false, T1 waveforms keep their
     * timings but collapse to a single default amplitude -- the rhythm survives, the
     * dynamics do not.
     */
    val hasAmplitudeControl: Boolean,

    /** Actuator count. Above 1 only on API 31+ multi-vibrator devices. */
    val vibratorCount: Int,

    /** Per-effect support for T2. Missing keys read as [SupportLevel.UNKNOWN]. */
    val effectSupport: Map<PredefinedEffect, SupportLevel>,

    /** Per-primitive support for T3. Missing keys read as [SupportLevel.UNKNOWN]. */
    val primitiveSupport: Map<HapticPrimitive, SupportLevel>,

    /**
     * Measured primitive durations in ms, where the platform will tell us. Empty when the
     * query is unavailable or fails. Used in A5 to time compositions against real hardware
     * rather than guessed delays.
     */
    val primitiveDurationsMs: Map<HapticPrimitive, Int>,
) {

    fun supportOf(effect: PredefinedEffect): SupportLevel =
        effectSupport[effect] ?: SupportLevel.UNKNOWN

    fun supportOf(primitive: HapticPrimitive): SupportLevel =
        primitiveSupport[primitive] ?: SupportLevel.UNKNOWN

    /** Human-readable dump for the diagnostics screen and logcat. */
    fun summary(): String = buildString {
        appendLine("API level          : $sdkInt")
        appendLine("has vibrator       : $hasVibrator")
        appendLine("amplitude control  : $hasAmplitudeControl")
        appendLine("actuator count     : $vibratorCount")
        appendLine()
        appendLine("predefined effects (T2)")
        PredefinedEffect.entries.forEach {
            appendLine("  ${it.name.padEnd(14)} ${supportOf(it)}")
        }
        appendLine()
        appendLine("composition primitives (T3)")
        HapticPrimitive.entries.forEach {
            val duration = primitiveDurationsMs[it]?.let { ms -> "  ${ms}ms" } ?: ""
            appendLine("  ${it.name.padEnd(14)} ${supportOf(it)}$duration")
        }
    }

    companion object {
        /** Fallback used when probing fails outright, so callers still get a valid object. */
        fun none(sdkInt: Int): HapticCapabilities = HapticCapabilities(
            sdkInt = sdkInt,
            hasVibrator = false,
            hasAmplitudeControl = false,
            vibratorCount = 0,
            effectSupport = emptyMap(),
            primitiveSupport = emptyMap(),
            primitiveDurationsMs = emptyMap(),
        )
    }
}
