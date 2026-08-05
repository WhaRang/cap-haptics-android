package com.cap.haptics.core.model

/**
 * Outcome of a playback call.
 *
 * The SDK's public surface never throws -- a Java exception unwinding into Unity through JNI
 * is a native crash risk and an appalling thing to debug on a device. Every failure comes
 * back as one of these instead.
 *
 * [code] is the wire form and crosses the JNI boundary in A8. Append new codes, never
 * renumber existing ones.
 */
enum class HapticResult(val code: Int) {

    /** Handed to the platform successfully. Note this is *not* a promise the user felt it. */
    OK(0),

    /** `Haptics.initialize` was never called, or it failed. */
    NOT_INITIALIZED(1),

    /** Device has no vibrator. Playback is a well-behaved no-op. */
    NO_VIBRATOR(2),

    /** The requested pattern has no rendering at the active tier. Should be unreachable
     *  once A5's matrix is complete -- every pattern degrades to something. */
    UNSUPPORTED_PATTERN(3),

    /** Caller passed something the platform would have rejected. Validated before the call. */
    INVALID_ARGUMENT(4),

    /** The platform call itself failed. Logged with the throwable. */
    PLATFORM_ERROR(5);

    val isSuccess: Boolean get() = this == OK

    companion object {
        fun fromCode(code: Int): HapticResult? = entries.firstOrNull { it.code == code }
    }
}
