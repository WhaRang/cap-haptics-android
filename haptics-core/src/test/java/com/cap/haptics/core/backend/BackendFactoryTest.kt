package com.cap.haptics.core.backend

import com.cap.haptics.core.model.HapticTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The forced-tier override is the only way to reach the fallback paths on this project's
 * single, top-tier test device. Its clamping rule therefore has to be right.
 */
class BackendFactoryTest {

    @Test
    fun `no override keeps the natural tier`() {
        assertEquals(
            HapticTier.COMPOSED,
            BackendFactory.resolveTier(natural = HapticTier.COMPOSED, forced = null),
        )
    }

    @Test
    fun `forcing downward is honoured`() {
        assertEquals(
            HapticTier.WAVEFORM,
            BackendFactory.resolveTier(natural = HapticTier.COMPOSED, forced = HapticTier.WAVEFORM),
        )
        assertEquals(
            HapticTier.PREDEFINED,
            BackendFactory.resolveTier(natural = HapticTier.COMPOSED, forced = HapticTier.PREDEFINED),
        )
    }

    @Test
    fun `forcing upward is clamped to what the device can do`() {
        // Otherwise the failure would surface from inside the platform, far from the cause.
        assertEquals(
            HapticTier.WAVEFORM,
            BackendFactory.resolveTier(natural = HapticTier.WAVEFORM, forced = HapticTier.COMPOSED),
        )
    }

    @Test
    fun `forcing the same tier is a no-op`() {
        assertEquals(
            HapticTier.PREDEFINED,
            BackendFactory.resolveTier(natural = HapticTier.PREDEFINED, forced = HapticTier.PREDEFINED),
        )
    }

    @Test
    fun `a device with no vibrator cannot be forced into any tier`() {
        assertEquals(
            HapticTier.NONE,
            BackendFactory.resolveTier(natural = HapticTier.NONE, forced = HapticTier.COMPOSED),
        )
    }

    @Test
    fun `forcing NONE simulates a device with no motor`() {
        assertEquals(
            HapticTier.NONE,
            BackendFactory.resolveTier(natural = HapticTier.COMPOSED, forced = HapticTier.NONE),
        )
    }
}
