package com.cap.haptics.core.model

/**
 * The four platform-tuned effects behind `VibrationEffect.createPredefined` (T2).
 *
 * Deliberately carries no platform constant: everything in [com.cap.haptics.core.model] must
 * stay free of `android.*` imports so the whole model layer is unit-testable on the JVM with
 * no device and no emulator. The mapping to platform ids lives in the capability layer.
 */
enum class PredefinedEffect(val minApi: Int) {
    TICK(29),
    CLICK(29),
    DOUBLE_CLICK(29),
    HEAVY_CLICK(29),
}
