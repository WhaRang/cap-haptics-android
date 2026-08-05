package com.cap.haptics.core.model

/**
 * The four platform-tuned effects behind `VibrationEffect.createPredefined` (T2).
 *
 * Deliberately carries no platform constant: everything in [com.cap.haptics.core.model] must
 * stay free of `android.*` imports so the whole model layer is unit-testable on the JVM with
 * no device and no emulator. The mapping to platform ids lives in the capability layer.
 */
enum class PredefinedEffect(val id: Int, val minApi: Int) {
    TICK(id = 0, minApi = 29),
    CLICK(id = 1, minApi = 29),
    DOUBLE_CLICK(id = 2, minApi = 29),
    HEAVY_CLICK(id = 3, minApi = 29);

    companion object {
        /** Parses an id arriving over JNI. Null rather than throwing on garbage. */
        fun fromId(id: Int): PredefinedEffect? = entries.firstOrNull { it.id == id }
    }
}
