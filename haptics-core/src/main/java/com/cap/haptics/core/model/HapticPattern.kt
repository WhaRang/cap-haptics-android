package com.cap.haptics.core.model

/**
 * The SDK's semantic vocabulary.
 *
 * Callers ask for a *meaning* -- "this succeeded", "this was a heavy impact" -- and the
 * library decides how to render it on the hardware in front of it. That indirection is the
 * entire point of the SDK: a game that calls `vibrate(200)` has hardcoded an assumption
 * about a motor it has never met.
 *
 * [id] is the wire form and crosses the JNI boundary in A8, where it is the integer a C#
 * enum mirrors. **Append new patterns, never renumber existing ones** -- a stale AAR paired
 * with a newer C# enum would otherwise silently play the wrong thing rather than failing.
 */
enum class HapticPattern(val id: Int) {

    /** Moving through discrete options: a picker detent, a list snap. The lightest thing. */
    SELECTION(0),

    IMPACT_LIGHT(1),
    IMPACT_MEDIUM(2),
    IMPACT_HEAVY(3),

    /** Affirmative, rising. */
    SUCCESS(4),

    /** Attention-seeking but not final. */
    WARNING(5),

    /** Final and unwelcome: three insistent beats. */
    ERROR(6),

    /** A swelling envelope. The pattern that loses the most on weaker hardware. */
    RAMP_UP(7),

    HEARTBEAT(8),

    /** A6 reroutes this through `View.performHapticFeedback` so it obeys system settings. */
    LONG_PRESS(9);

    companion object {
        /** Returns null rather than throwing: this parses untrusted input from the bridge. */
        fun fromId(id: Int): HapticPattern? = entries.firstOrNull { it.id == id }
    }
}
