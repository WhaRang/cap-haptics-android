package com.cap.haptics.core.model

/**
 * A validated timing/amplitude envelope.
 *
 * Validation happens here, once, in pure Kotlin -- so the platform never sees arguments it
 * would throw on, and so the rules are unit-testable without a device. `createWaveform` is
 * unforgiving about array lengths, amplitude ranges and repeat indices; every one of those
 * would otherwise surface as an `IllegalArgumentException` from deep inside the platform.
 *
 * **The two platform forms mean different things**, and the difference is easy to miss:
 *
 * - `createWaveform(timings, amplitudes, repeat)` -- segment `i` plays for `timings[i]` at
 *   `amplitudes[i]`. No implicit gaps.
 * - `createWaveform(timings, repeat)` -- timings alternate off/on starting with **off**.
 *
 * The library's convention is to author every waveform so that *even indices carry
 * amplitude 0*. Under that rule both forms produce an identical rhythm, which is what makes
 * [withoutAmplitudes] a safe degradation rather than a reinterpretation. A leading 0
 * therefore means "start vibrating immediately".
 */
class Waveform private constructor(
    val timingsMs: LongArray,
    /** Null means "no amplitude information" -- the platform's default amplitude is used. */
    val amplitudes: IntArray?,
    /** Index to loop from, or [NO_REPEAT]. */
    val repeatIndex: Int,
) {

    /**
     * Drops amplitude data, keeping the timings. Used when the motor has no amplitude control.
     *
     * Faithful only for waveforms following the even-index-is-silent convention described
     * above. A continuously-on envelope -- a ramp, say -- has no silent segments, so
     * stripping its amplitudes turns it into an alternating buzz. That is not a bug in this
     * method so much as the honest truth about the hardware: a motor with no amplitude
     * control cannot render a ramp. A5's degradation matrix gives such patterns a different
     * rendering at this tier rather than relying on this fallback.
     */
    fun withoutAmplitudes(): Waveform = Waveform(timingsMs, null, repeatIndex)

    /** True when the waveform can be stripped to timings-only without changing its rhythm. */
    val followsAlternatingConvention: Boolean
        get() = amplitudes?.filterIndexed { index, _ -> index % 2 == 0 }?.all { it == 0 } ?: true

    val totalDurationMs: Long get() = timingsMs.sum()

    override fun toString(): String =
        "Waveform(timings=${timingsMs.toList()}, " +
            "amplitudes=${amplitudes?.toList()}, repeat=$repeatIndex)"

    companion object {
        const val MAX_AMPLITUDE: Int = 255
        const val NO_REPEAT: Int = -1

        /**
         * Returns null when the input is invalid. Call [validate] for the reason.
         *
         * Null rather than an exception because this sits on the path from Unity, where the
         * caller is untrusted C# and the honest answer is a result code, not a crash.
         *
         * Deliberately silent: logging here would pull `android.util.Log` into the model
         * layer and cost every test in this package its ability to run on the JVM. Callers
         * that want the reason in logcat ask [validate] for it -- see `Haptics.playWaveform`.
         */
        fun create(
            timingsMs: LongArray,
            amplitudes: IntArray? = null,
            repeatIndex: Int = NO_REPEAT,
        ): Waveform? {
            if (validate(timingsMs, amplitudes, repeatIndex) != null) return null
            return Waveform(timingsMs.copyOf(), amplitudes?.copyOf(), repeatIndex)
        }

        /** Convenience for the single-pulse case. */
        fun oneShot(durationMs: Long, amplitude: Int? = null): Waveform? = create(
            timingsMs = longArrayOf(0, durationMs),
            amplitudes = amplitude?.let { intArrayOf(0, it) },
        )

        /** Returns a human-readable problem description, or null when valid. */
        fun validate(
            timingsMs: LongArray,
            amplitudes: IntArray?,
            repeatIndex: Int,
        ): String? = when {
            timingsMs.isEmpty() ->
                "timings must not be empty"

            timingsMs.any { it < 0 } ->
                "timings must be non-negative, got ${timingsMs.toList()}"

            // An all-zero envelope is accepted by the platform but produces silence, which
            // is indistinguishable from a bug at the point where someone is holding a phone
            // and feeling nothing.
            timingsMs.all { it == 0L } ->
                "timings are all zero, nothing would play"

            amplitudes != null && amplitudes.size != timingsMs.size ->
                "amplitudes size ${amplitudes.size} != timings size ${timingsMs.size}"

            amplitudes != null && amplitudes.any { it !in 0..MAX_AMPLITUDE } ->
                "amplitudes must be in 0..$MAX_AMPLITUDE, got ${amplitudes.toList()}"

            repeatIndex != NO_REPEAT && repeatIndex !in timingsMs.indices ->
                "repeatIndex $repeatIndex out of bounds for ${timingsMs.size} steps"

            else -> null
        }
    }
}
