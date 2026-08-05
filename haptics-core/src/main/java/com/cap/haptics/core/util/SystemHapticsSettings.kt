package com.cap.haptics.core.util

import android.content.Context
import android.provider.Settings

/**
 * Best-effort read of whether the user has haptics switched on.
 *
 * Deliberately modest about what it knows. `HAPTIC_FEEDBACK_ENABLED` is the only haptics
 * preference the platform exposes publicly; OEM skins layer their own on top -- Samsung
 * alone has separate intensity sliders for calls, notifications and touch interaction, none
 * of them readable by a third-party app. So a `true` here does **not** promise the user will
 * feel anything, and the diagnostics screen says so rather than implying otherwise.
 *
 * The honest signal for "was it actually played" is [com.cap.haptics.core.model.HapticResult.SUPPRESSED]
 * from the view-feedback channel, which reports the platform's real answer.
 */
internal object SystemHapticsSettings {

    /** Null when the setting could not be read at all. */
    @Suppress("DEPRECATION")
    fun touchFeedbackEnabled(context: Context): Boolean? = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
        ) != 0
    }.onFailure {
        HLog.d("Could not read HAPTIC_FEEDBACK_ENABLED: ${it.message}")
    }.getOrNull()
}
