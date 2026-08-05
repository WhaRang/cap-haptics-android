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
enum class ViewFeedback(val minApi: Int) {
    LONG_PRESS(3),
    VIRTUAL_KEY(5),
    KEYBOARD_TAP(8),
    CLOCK_TICK(21),
    CONTEXT_CLICK(23),
    TEXT_HANDLE_MOVE(27),
    CONFIRM(30),
    REJECT(30),
    GESTURE_START(30),
    GESTURE_END(30),
    TOGGLE_ON(34),
    TOGGLE_OFF(34),
    SEGMENT_TICK(34),
    DRAG_START(34),
}
