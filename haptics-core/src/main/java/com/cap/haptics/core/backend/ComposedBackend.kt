package com.cap.haptics.core.backend

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import com.cap.haptics.core.capability.platformId
import com.cap.haptics.core.feedback.ViewFeedbackChannel
import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.SupportLevel
import com.cap.haptics.core.pattern.PrimitiveSubstitution
import com.cap.haptics.core.util.HLog

/**
 * T3 -- `VibrationEffect.Composition`. API 30+ and real primitive support.
 *
 * Extends [PredefinedBackend], so a T3 device keeps the T2 and T1 paths beneath it and has
 * somewhere to fall back to when composition fails outright.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal class ComposedBackend(
    vibrator: Vibrator,
    capabilities: HapticCapabilities,
    viewFeedback: ViewFeedbackChannel? = null,
) : PredefinedBackend(vibrator, capabilities, viewFeedback) {

    override val tier: HapticTier get() = HapticTier.COMPOSED

    /** Computed once: the substitution lookup runs per step, per playback. */
    private val supportedPrimitives: Set<HapticPrimitive> =
        capabilities.primitiveSupport
            .filterValues { it == SupportLevel.YES }
            .keys

    // WrongConstant: the @IntDef is erased by the enum mapping -- see PlatformIds.
    @SuppressLint("WrongConstant")
    override fun playComposition(steps: List<CompositionStep>): HapticResult {
        val problem = CompositionStep.validate(steps)
        if (problem != null) {
            HLog.w("Rejected composition: $problem")
            return HapticResult.INVALID_ARGUMENT
        }

        val resolved = steps.mapNotNull { step ->
            val substitute = PrimitiveSubstitution.resolve(step.primitive, supportedPrimitives)
            when {
                substitute == null -> {
                    HLog.d("Dropping ${step.primitive}: no supported substitute")
                    null
                }

                substitute != step.primitive -> {
                    HLog.d("Substituting ${step.primitive} -> $substitute")
                    step.copy(primitive = substitute)
                }

                else -> step
            }
        }

        // Every step dropped means this device supports none of the primitives involved.
        // Flattening to a waveform is a better answer than silence.
        if (resolved.isEmpty()) {
            HLog.d("No primitive survived substitution; flattening to a waveform")
            return super.playComposition(steps)
        }

        return try {
            val composition = VibrationEffect.startComposition()
            resolved.forEach { step ->
                composition.addPrimitive(step.primitive.platformId(), step.scale, step.delayMs)
            }
            vibrator.vibrate(composition.compose())
            HLog.d("T${tier.level} composed ${resolved.size} primitives")
            HapticResult.OK
        } catch (t: Throwable) {
            HLog.e("Composition failed; flattening to a waveform", t)
            super.playComposition(steps)
        }
    }
}
