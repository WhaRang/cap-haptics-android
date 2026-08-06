package com.cap.haptics.unity

import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect

/**
 * Serialises a capability snapshot for the C# side.
 *
 * JSON is the right shape here and the wrong shape almost everywhere else in the bridge:
 * this is read **once per session** to populate a diagnostics panel, so a string crossing
 * JNI costs nothing. On the playback path it would mean marshalling and garbage on every
 * single vibration, which is why every other bridge method takes and returns primitives.
 */
internal object CapabilitiesJson {

    fun of(
        capabilities: HapticCapabilities?,
        deviceTier: HapticTier,
        activeTier: HapticTier,
        viewFeedbackAvailable: Boolean,
        systemHapticsEnabled: Boolean?,
    ): String {
        if (capabilities == null) {
            return Json.obj(
                "bridgeVersion" to Json.number(BridgeVersion.CURRENT),
                "initialized" to Json.bool(false),
            )
        }

        return Json.obj(
            "bridgeVersion" to Json.number(BridgeVersion.CURRENT),
            "initialized" to Json.bool(true),
            "sdkInt" to Json.number(capabilities.sdkInt),
            "hasVibrator" to Json.bool(capabilities.hasVibrator),
            "hasAmplitudeControl" to Json.bool(capabilities.hasAmplitudeControl),
            "vibratorCount" to Json.number(capabilities.vibratorCount),
            "deviceTier" to Json.number(deviceTier.level),
            "activeTier" to Json.number(activeTier.level),
            "viewFeedbackAvailable" to Json.bool(viewFeedbackAvailable),
            // Tri-state string, not a nullable bool: "we could not read it" and "the user
            // turned it off" are different answers the panel must not conflate — and the
            // first draft's `true/false/null` turned out to be unrepresentable in Unity's
            // JsonUtility, which silently reads null as false. The values are
            // SupportLevel names, the vocabulary the rest of the blob already uses.
            "systemHapticsEnabled" to Json.string(
                when (systemHapticsEnabled) {
                    true -> "YES"
                    false -> "NO"
                    null -> "UNKNOWN"
                }
            ),
            "effects" to Json.array(
                PredefinedEffect.entries.map { effect ->
                    Json.obj(
                        "name" to Json.string(effect.name),
                        "id" to Json.number(effect.id),
                        "support" to Json.string(capabilities.supportOf(effect).name),
                    )
                }
            ),
            "primitives" to Json.array(
                HapticPrimitive.entries.map { primitive ->
                    Json.obj(
                        "name" to Json.string(primitive.name),
                        "id" to Json.number(primitive.id),
                        "support" to Json.string(capabilities.supportOf(primitive).name),
                        "durationMs" to Json.number(
                            capabilities.primitiveDurationsMs[primitive] ?: -1
                        ),
                    )
                }
            ),
        )
    }
}
