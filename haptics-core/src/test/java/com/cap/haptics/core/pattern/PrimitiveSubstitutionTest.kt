package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.HapticPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PrimitiveSubstitutionTest {

    private val all = HapticPrimitive.entries.toSet()

    @Test
    fun `a supported primitive is returned unchanged`() {
        all.forEach { primitive ->
            assertEquals(primitive, PrimitiveSubstitution.resolve(primitive, all))
        }
    }

    @Test
    fun `every primitive survives on a device with only the core two`() {
        // This is the property that makes T3 safe: the tier gate requires CLICK and TICK, so
        // if every chain terminates in one of them, no composition can ever come up empty on
        // a device that reached T3. Without it, an API 31 phone missing THUD would fall all
        // the way to a flattened waveform for a pattern that only used THUD as a garnish.
        HapticPrimitive.entries.forEach { primitive ->
            assertNotNull(
                "$primitive has no path to a core primitive",
                PrimitiveSubstitution.resolve(primitive, HapticPrimitive.CORE),
            )
        }
    }

    @Test
    fun `low tick prefers tick over click`() {
        assertEquals(
            HapticPrimitive.TICK,
            PrimitiveSubstitution.resolve(HapticPrimitive.LOW_TICK, HapticPrimitive.CORE),
        )
    }

    @Test
    fun `low tick falls through to click when tick is missing`() {
        assertEquals(
            HapticPrimitive.CLICK,
            PrimitiveSubstitution.resolve(HapticPrimitive.LOW_TICK, setOf(HapticPrimitive.CLICK)),
        )
    }

    @Test
    fun `thud prefers click because weight matters more than sharpness`() {
        assertEquals(
            HapticPrimitive.CLICK,
            PrimitiveSubstitution.resolve(HapticPrimitive.THUD, HapticPrimitive.CORE),
        )
    }

    @Test
    fun `spin walks its chain to the first supported entry`() {
        assertEquals(
            HapticPrimitive.QUICK_RISE,
            PrimitiveSubstitution.resolve(
                HapticPrimitive.SPIN,
                setOf(HapticPrimitive.QUICK_RISE, HapticPrimitive.CLICK),
            ),
        )
    }

    @Test
    fun `nothing supported yields null so the caller can flatten instead`() {
        assertNull(PrimitiveSubstitution.resolve(HapticPrimitive.THUD, emptySet()))
    }
}
