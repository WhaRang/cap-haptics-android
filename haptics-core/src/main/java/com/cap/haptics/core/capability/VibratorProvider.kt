package com.cap.haptics.core.capability

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * The one place that knows how to get hold of a [Vibrator].
 *
 * API 31 replaced the `VIBRATOR_SERVICE` lookup with [VibratorManager], which also exposes
 * multiple actuators. Isolating that branch here keeps the version check out of every
 * backend.
 */
internal object VibratorProvider {

    /** Null when the device has no vibrator service at all. */
    fun get(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager(context)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Actuator count. Always 0 or 1 below API 31, where there is no way to ask. */
    fun count(context: Context): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager(context)?.vibratorIds?.size ?: 0
        } else {
            if (get(context)?.hasVibrator() == true) 1 else 0
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun manager(context: Context): VibratorManager? =
        context.getSystemService(VibratorManager::class.java)
}
