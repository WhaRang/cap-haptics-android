package com.cap.haptics.core.model

/**
 * The platform's UI-gesture haptics, behind `View.performHapticFeedback`.
 *
 * A parallel channel rather than a fourth tier. What it offers that the `Vibrator` path
 * cannot:
 *
 * - It **obeys the user's haptic settings** automatically, and reports when it was
 *   suppressed -- the `Vibrator` path silently does nothing instead.
 * - It is **OEM-tuned per gesture**, so a long-press on a Samsung feels like a long-press
 *   everywhere else on that device.
 *
 * What it costs: no intensity control, no waveform control, and nothing to say about tiers.
 * So the library prefers it only for patterns that genuinely *are* UI gestures, and only at
 * full intensity -- see `PatternRegistry.viewFeedbackFor`.
 *
 * Like the other model enums this carries no platform constant, so the package stays
 * JVM-testable.
 */
enum class ViewFeedback(val id: Int, val minApi: Int) {
    LONG_PRESS(id = 0, minApi = 3),
    VIRTUAL_KEY(id = 1, minApi = 5),
    KEYBOARD_TAP(id = 2, minApi = 8),
    CLOCK_TICK(id = 3, minApi = 21),
    CONTEXT_CLICK(id = 4, minApi = 23),
    TEXT_HANDLE_MOVE(id = 5, minApi = 27),
    CONFIRM(id = 6, minApi = 30),
    REJECT(id = 7, minApi = 30),
    GESTURE_START(id = 8, minApi = 30),
    GESTURE_END(id = 9, minApi = 30),
    TOGGLE_ON(id = 10, minApi = 34),
    TOGGLE_OFF(id = 11, minApi = 34),
    SEGMENT_TICK(id = 12, minApi = 34),
    DRAG_START(id = 13, minApi = 34);

    companion object {
        /** Parses an id arriving over JNI. Null rather than throwing on garbage. */
        fun fromId(id: Int): ViewFeedback? = entries.firstOrNull { it.id == id }
    }
}
