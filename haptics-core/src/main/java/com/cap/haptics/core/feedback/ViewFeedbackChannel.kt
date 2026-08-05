package com.cap.haptics.core.feedback

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.ViewFeedback
import com.cap.haptics.core.util.HLog

/**
 * Plays haptics through `View.performHapticFeedback`.
 *
 * Requires an `Activity`, which is why it is optional: `Haptics.initialize` accepts any
 * `Context`, and a caller that supplies an application context simply gets no view feedback
 * and falls back to the `Vibrator` path. An SDK that refuses to initialise without an
 * Activity would be a worse SDK.
 *
 * **Threading matters here.** Unity's main thread is not the Android UI thread, and
 * `performHapticFeedback` must run on the latter. Calls from elsewhere are posted to the UI
 * thread, which means their real outcome cannot be reported -- see [perform].
 */
internal class ViewFeedbackChannel(private val activity: Activity) {

    fun isAvailable(feedback: ViewFeedback): Boolean =
        Build.VERSION.SDK_INT >= feedback.minApi

    /**
     * @return [HapticResult.SUPPRESSED] when the platform declined -- the user has haptics
     *   off. [HapticResult.OK] from a background thread means *dispatched*, not *played*:
     *   the answer arrives on the UI thread after this method has already returned. That is
     *   a real limitation of the channel, not an oversight.
     */
    fun perform(feedback: ViewFeedback): HapticResult {
        if (!isAvailable(feedback)) {
            HLog.d("$feedback needs API ${feedback.minApi}")
            return HapticResult.UNSUPPORTED_PATTERN
        }

        return try {
            val view = activity.window?.decorView
            if (view == null) {
                HLog.w("No decor view; cannot use view feedback")
                return HapticResult.UNSUPPORTED_PATTERN
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                performNow(view, feedback)
            } else {
                activity.runOnUiThread { performNow(view, feedback) }
                HapticResult.OK
            }
        } catch (t: Throwable) {
            HLog.e("View feedback failed for $feedback", t)
            HapticResult.PLATFORM_ERROR
        }
    }

    // InlinedApi: the constants above our floor are compile-time values, safe to read at any
    // level. isAvailable() is what stops one reaching a platform that predates it.
    //
    // Each minApi in ViewFeedback was checked against what lint reports here (27 for
    // TEXT_HANDLE_MOVE, 30 for CONFIRM/REJECT/GESTURE_*, 34 for TOGGLE_*/SEGMENT_TICK/
    // DRAG_START). Suppressing first and trusting the enum afterwards would have thrown away
    // the only authoritative source for those numbers.
    @SuppressLint("InlinedApi")
    private fun performNow(view: View, feedback: ViewFeedback): HapticResult {
        val constant = when (feedback) {
            ViewFeedback.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            ViewFeedback.VIRTUAL_KEY -> HapticFeedbackConstants.VIRTUAL_KEY
            ViewFeedback.KEYBOARD_TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            ViewFeedback.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
            ViewFeedback.CONTEXT_CLICK -> HapticFeedbackConstants.CONTEXT_CLICK
            ViewFeedback.TEXT_HANDLE_MOVE -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
            ViewFeedback.CONFIRM -> HapticFeedbackConstants.CONFIRM
            ViewFeedback.REJECT -> HapticFeedbackConstants.REJECT
            ViewFeedback.GESTURE_START -> HapticFeedbackConstants.GESTURE_START
            ViewFeedback.GESTURE_END -> HapticFeedbackConstants.GESTURE_END
            ViewFeedback.TOGGLE_ON -> HapticFeedbackConstants.TOGGLE_ON
            ViewFeedback.TOGGLE_OFF -> HapticFeedbackConstants.TOGGLE_OFF
            ViewFeedback.SEGMENT_TICK -> HapticFeedbackConstants.SEGMENT_TICK
            ViewFeedback.DRAG_START -> HapticFeedbackConstants.DRAG_START
        }

        // No FLAG_IGNORE_VIEW_SETTING: obeying the user's preference is the entire reason
        // this channel exists.
        val played = view.performHapticFeedback(constant)
        HLog.d("View feedback $feedback -> ${if (played) "played" else "suppressed"}")
        return if (played) HapticResult.OK else HapticResult.SUPPRESSED
    }
}
