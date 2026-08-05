package com.cap.haptics.core.util

import android.util.Log
import com.cap.haptics.core.HapticsCore

/**
 * Tagged, gated logging for the whole library.
 *
 * Everything the SDK does at runtime should be traceable from `adb logcat -s CapHaptics:V`
 * without a debugger attached -- on a device where the only symptom of a bug is "I felt
 * nothing", the log is often the only evidence there is.
 *
 * [verbose] is off by default so a shipping app gets warnings and errors only. It is wired
 * to `HapticsConfig` in A2.
 */
object HLog {

    /** When false, [d] is suppressed. Warnings and errors always go through. */
    @JvmStatic
    var verbose: Boolean = false

    fun d(message: String) {
        if (verbose) Log.d(HapticsCore.LOG_TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(HapticsCore.LOG_TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(HapticsCore.LOG_TAG, message, throwable)
    }
}
