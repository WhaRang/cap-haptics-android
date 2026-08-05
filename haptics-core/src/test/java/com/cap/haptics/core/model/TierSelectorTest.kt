package com.cap.haptics.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The payoff for keeping tier selection pure.
 *
 * Every device shape below is one this project cannot physically test -- the only hardware
 * available is an API 36 flagship that will always land on COMPOSED. These run on the JVM in
 * milliseconds with no emulator.
 */
class TierSelectorTest {

    private fun capabilities(
        sdkInt: Int,
        hasVibrator: Boolean = true,
        hasAmplitudeControl: Boolean = true,
        effects: SupportLevel? = null,
        primitives: Map<HapticPrimitive, SupportLevel> = emptyMap(),
    ) = HapticCapabilities(
        sdkInt = sdkInt,
        hasVibrator = hasVibrator,
        hasAmplitudeControl = hasAmplitudeControl,
        vibratorCount = if (hasVibrator) 1 else 0,
        effectSupport = effects?.let { level ->
            PredefinedEffect.entries.associateWith { level }
        } ?: emptyMap(),
        primitiveSupport = primitives,
        primitiveDurationsMs = emptyMap(),
    )

    private fun allPrimitives(level: SupportLevel) =
        HapticPrimitive.entries.associateWith { level }

    @Test
    fun `no vibrator falls through to NONE regardless of API level`() {
        val caps = capabilities(sdkInt = 36, hasVibrator = false, primitives = allPrimitives(SupportLevel.YES))
        assertEquals(HapticTier.NONE, TierSelector.select(caps))
    }

    @Test
    fun `API 26 with nothing available lands on the waveform floor`() {
        val caps = capabilities(sdkInt = 26, effects = SupportLevel.NO, primitives = allPrimitives(SupportLevel.NO))
        assertEquals(HapticTier.WAVEFORM, TierSelector.select(caps))
    }

    @Test
    fun `API 29 unknown effect support still selects PREDEFINED`() {
        // The platform substitutes a generic fallback, which beats a hand-rolled waveform.
        val caps = capabilities(sdkInt = 29, effects = SupportLevel.UNKNOWN, primitives = allPrimitives(SupportLevel.NO))
        assertEquals(HapticTier.PREDEFINED, TierSelector.select(caps))
    }

    @Test
    fun `API 30 with core primitives selects COMPOSED`() {
        val caps = capabilities(sdkInt = 30, effects = SupportLevel.YES, primitives = allPrimitives(SupportLevel.YES))
        assertEquals(HapticTier.COMPOSED, TierSelector.select(caps))
    }

    @Test
    fun `modern API with a cheap ERM motor drops to PREDEFINED`() {
        // The whole reason the gate is capability and not version: this device passes every
        // version check and still cannot compose.
        val caps = capabilities(sdkInt = 36, effects = SupportLevel.YES, primitives = allPrimitives(SupportLevel.NO))
        assertEquals(HapticTier.PREDEFINED, TierSelector.select(caps))
    }

    @Test
    fun `partial core primitive support is not enough for COMPOSED`() {
        val caps = capabilities(
            sdkInt = 31,
            effects = SupportLevel.YES,
            primitives = allPrimitives(SupportLevel.NO) + mapOf(HapticPrimitive.CLICK to SupportLevel.YES),
        )
        assertEquals(HapticTier.PREDEFINED, TierSelector.select(caps))
    }

    @Test
    fun `optional primitives are not required for COMPOSED`() {
        // THUD and SPIN missing is fine -- A4 substitutes them.
        val caps = capabilities(
            sdkInt = 36,
            effects = SupportLevel.YES,
            primitives = allPrimitives(SupportLevel.NO) + HapticPrimitive.CORE.associateWith { SupportLevel.YES },
        )
        assertEquals(HapticTier.COMPOSED, TierSelector.select(caps))
    }

    @Test
    fun `unqueryable capabilities default to UNKNOWN rather than throwing`() {
        val caps = capabilities(sdkInt = 30)
        assertEquals(SupportLevel.UNKNOWN, caps.supportOf(PredefinedEffect.CLICK))
        assertEquals(SupportLevel.UNKNOWN, caps.supportOf(HapticPrimitive.CLICK))
    }
}
