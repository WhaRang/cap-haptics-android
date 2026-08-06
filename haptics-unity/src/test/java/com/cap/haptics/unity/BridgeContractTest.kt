package com.cap.haptics.unity

import com.cap.haptics.core.model.HapticCapabilities
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.SupportLevel
import com.cap.haptics.core.model.ViewFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge itself needs a device, but its serialisation and id mapping do not — and those
 * are where an ABI breaks silently.
 */
class BridgeContractTest {

    private val capabilities = HapticCapabilities(
        sdkInt = 36,
        hasVibrator = true,
        hasAmplitudeControl = true,
        vibratorCount = 1,
        effectSupport = PredefinedEffect.entries.associateWith { SupportLevel.YES },
        primitiveSupport = HapticPrimitive.entries.associateWith { SupportLevel.YES },
        primitiveDurationsMs = mapOf(HapticPrimitive.CLICK to 12),
    )

    @Test
    fun `every marshalled enum has unique ids`() {
        // A duplicate makes fromId ambiguous, and the ABI is unfixable once an AAR is sitting
        // in someone's Unity project.
        assertEquals(
            HapticPrimitive.entries.size,
            HapticPrimitive.entries.map { it.id }.toSet().size,
        )
        assertEquals(
            PredefinedEffect.entries.size,
            PredefinedEffect.entries.map { it.id }.toSet().size,
        )
        assertEquals(ViewFeedback.entries.size, ViewFeedback.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `every marshalled enum round-trips through its id`() {
        HapticPrimitive.entries.forEach { assertEquals(it, HapticPrimitive.fromId(it.id)) }
        PredefinedEffect.entries.forEach { assertEquals(it, PredefinedEffect.fromId(it.id)) }
        ViewFeedback.entries.forEach { assertEquals(it, ViewFeedback.fromId(it.id)) }
    }

    @Test
    fun `the manifest lists every value of every marshalled enum`() {
        // This is what the C# side validates itself against, so a missing entry would let
        // real drift through unnoticed.
        val json = EnumManifest.toJson()

        listOf(
            HapticPattern.entries.map { it.name },
            HapticPrimitive.entries.map { it.name },
            PredefinedEffect.entries.map { it.name },
            ViewFeedback.entries.map { it.name },
            HapticTier.entries.map { it.name },
            HapticResult.entries.map { it.name },
        ).flatten().forEach { name ->
            assertTrue("manifest is missing $name", json.contains("\"$name\""))
        }

        assertTrue(json.contains("\"bridgeVersion\":${BridgeVersion.CURRENT}"))
    }

    @Test
    fun `capabilities json reports the uninitialized case honestly`() {
        val json = CapabilitiesJson.of(
            capabilities = null,
            deviceTier = HapticTier.NONE,
            activeTier = HapticTier.NONE,
            viewFeedbackAvailable = false,
            systemHapticsEnabled = null,
        )

        assertTrue(json.contains("\"initialized\":false"))
    }

    @Test
    fun `capabilities json carries the fields the diagnostics panel needs`() {
        val json = CapabilitiesJson.of(
            capabilities = capabilities,
            deviceTier = HapticTier.COMPOSED,
            activeTier = HapticTier.WAVEFORM,
            viewFeedbackAvailable = true,
            systemHapticsEnabled = true,
        )

        assertTrue(json.contains("\"sdkInt\":36"))
        assertTrue(json.contains("\"deviceTier\":3"))
        assertTrue(json.contains("\"activeTier\":1"))
        assertTrue(json.contains("\"hasAmplitudeControl\":true"))
        assertTrue(json.contains("\"CLICK\""))
        assertTrue(json.contains("\"systemHapticsEnabled\":\"YES\""))
        assertTrue(json.contains("\"durationMs\":12"))
        // Unqueried durations report -1 rather than being omitted, so the C# parse stays
        // uniform across entries.
        assertTrue(json.contains("\"durationMs\":-1"))
    }

    @Test
    fun `unknown system haptics is UNKNOWN rather than NO`() {
        // "We could not read it" and "the user turned it off" are different answers. A
        // tri-state string rather than a nullable bool, because Unity's JsonUtility parses
        // JSON null as false — which is precisely the conflation this field must avoid.
        val json = CapabilitiesJson.of(
            capabilities = capabilities,
            deviceTier = HapticTier.COMPOSED,
            activeTier = HapticTier.COMPOSED,
            viewFeedbackAvailable = true,
            systemHapticsEnabled = null,
        )

        assertTrue(json.contains("\"systemHapticsEnabled\":\"UNKNOWN\""))
    }

    @Test
    fun `json escaping survives characters that would break the parse`() {
        assertEquals("\"a\\\"b\"", Json.string("a\"b"))
        assertEquals("\"a\\\\b\"", Json.string("a\\b"))
        assertEquals("\"a\\nb\"", Json.string("a\nb"))
        assertEquals("\"\\u0007\"", Json.string("\u0007"))
    }
}
