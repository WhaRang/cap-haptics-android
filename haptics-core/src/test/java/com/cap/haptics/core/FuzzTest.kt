package com.cap.haptics.core

import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.ViewFeedback
import com.cap.haptics.core.model.Waveform
import com.cap.haptics.core.pattern.CompositionApproximation
import com.cap.haptics.core.pattern.IntensityScaler
import com.cap.haptics.core.pattern.PatternRegistry
import com.cap.haptics.core.pattern.PrimitiveSubstitution
import com.cap.haptics.core.pattern.Rendering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Adversarial input across the pure layers.
 *
 * The SDK promises never to throw, and the layers reached here are where a caller's garbage
 * arrives first — everything below them is already wrapped in try/catch. The seed is fixed
 * so a failure reproduces exactly rather than haunting one CI run in fifty.
 *
 * The platform-facing half of the promise cannot be checked here (it needs a device); the
 * harness has a fuzz button that exercises `Haptics` itself on hardware.
 */
class FuzzTest {

    private val random = Random(seed = 20260805)

    private val nastyLongs = longArrayOf(
        0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, Int.MAX_VALUE.toLong(), -999_999L,
    )

    private val nastyInts = intArrayOf(
        0, -1, 1, 255, 256, -256, Int.MIN_VALUE, Int.MAX_VALUE,
    )

    private val nastyFloats = floatArrayOf(
        0f, 1f, -1f, 0.5f, 1.0001f, -0.0001f, 1e30f, -1e30f,
        Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
        Float.MIN_VALUE, Float.MAX_VALUE,
    )

    @Test
    fun `waveform creation survives arbitrary arrays`() {
        repeat(3_000) {
            val size = random.nextInt(0, 8)
            val timings = LongArray(size) { nastyLongs.random(random) }
            val amplitudes =
                if (random.nextBoolean()) {
                    IntArray(random.nextInt(0, 8)) { nastyInts.random(random) }
                } else {
                    null
                }
            val repeat = nastyInts.random(random)

            // The contract: create() returns null exactly when validate() has a complaint.
            // If those ever disagree, something invalid reaches the platform.
            val problem = Waveform.validate(timings, amplitudes, repeat)
            val waveform = Waveform.create(timings, amplitudes, repeat)

            if (problem == null) {
                assertNotNull("validate passed but create returned null", waveform)
            } else {
                assertNull("create accepted input validate rejected: $problem", waveform)
            }
        }
    }

    @Test
    fun `accepted waveforms are always within platform limits`() {
        repeat(3_000) {
            val timings = LongArray(random.nextInt(0, 6)) { random.nextLong(-100, 500) }
            val amplitudes = IntArray(random.nextInt(0, 6)) { random.nextInt(-50, 400) }
            val waveform = Waveform.create(timings, amplitudes, random.nextInt(-3, 8)) ?: return@repeat

            assertTrue(waveform.timingsMs.all { it >= 0 })
            assertTrue(waveform.timingsMs.any { it > 0 })
            assertTrue(waveform.amplitudes!!.all { it in 0..Waveform.MAX_AMPLITUDE })
            assertEquals(waveform.timingsMs.size, waveform.amplitudes!!.size)
            assertTrue(waveform.timingsMs.size <= Waveform.MAX_STEPS)
        }
    }

    @Test
    fun `composition validation survives arbitrary steps`() {
        repeat(3_000) {
            val steps = List(random.nextInt(0, 6)) {
                CompositionStep(
                    primitive = HapticPrimitive.entries.random(random),
                    scale = nastyFloats.random(random),
                    delayMs = nastyInts.random(random),
                )
            }

            val problem = CompositionStep.validate(steps)
            val flattened = CompositionApproximation.toWaveform(steps)

            if (problem != null) {
                assertNull("flattened a composition validate rejected: $problem", flattened)
            }
        }
    }

    @Test
    fun `intensity scaling never produces something the platform would reject`() {
        val tiers = listOf(HapticTier.WAVEFORM, HapticTier.PREDEFINED, HapticTier.COMPOSED)

        repeat(5_000) {
            val pattern = HapticPattern.entries.random(random)
            val tier = tiers.random(random)
            val rendering = PatternRegistry.renderingFor(pattern, tier)!!
            val intensity = nastyFloats.random(random)

            when (val scaled = IntensityScaler.scale(rendering, intensity)) {
                is Rendering.Composed -> assertNull(
                    "$pattern/$tier at intensity $intensity produced an invalid composition",
                    CompositionStep.validate(scaled.steps),
                )

                is Rendering.Wave -> assertNull(
                    "$pattern/$tier at intensity $intensity produced an invalid waveform",
                    Waveform.validate(
                        scaled.waveform.timingsMs,
                        scaled.waveform.amplitudes,
                        scaled.waveform.repeatIndex,
                    ),
                )

                is Rendering.Effect -> Unit
            }
        }
    }

    @Test
    fun `the intensity curve stays bounded for any input`() {
        nastyFloats.forEach { intensity ->
            val factor = IntensityScaler.effectiveFactor(intensity)
            assertTrue("factor $factor out of range for $intensity", factor in 0f..1f)
            assertTrue("factor was NaN for $intensity", !factor.isNaN())
        }
    }

    @Test
    fun `primitive substitution survives arbitrary support sets`() {
        repeat(2_000) {
            val supported = HapticPrimitive.entries.filter { random.nextBoolean() }.toSet()
            val primitive = HapticPrimitive.entries.random(random)

            val resolved = PrimitiveSubstitution.resolve(primitive, supported)
            if (resolved != null) {
                // Never substitute something the device cannot play -- that would turn a
                // graceful degradation into a silent failure.
                assertTrue("resolved to unsupported $resolved", resolved in supported)
            }
        }
    }

    @Test
    fun `wire-format parsing rejects garbage instead of throwing`() {
        // These parse untrusted integers arriving over JNI. Returning null keeps a
        // stale C# enum from silently selecting the wrong pattern.
        nastyInts.forEach { value ->
            HapticPattern.fromId(value)
            HapticTier.fromLevel(value)
            HapticResult.fromCode(value)
            HapticPrimitive.fromId(value)
            PredefinedEffect.fromId(value)
            ViewFeedback.fromId(value)
        }

        assertNull(HapticPattern.fromId(-1))
        assertNull(HapticPattern.fromId(Int.MAX_VALUE))
        assertNull(HapticTier.fromLevel(99))
        assertNull(HapticResult.fromCode(-7))

        // Round-trips must hold, or the bridge silently plays the wrong thing.
        HapticPattern.entries.forEach { assertEquals(it, HapticPattern.fromId(it.id)) }
        HapticTier.entries.forEach { assertEquals(it, HapticTier.fromLevel(it.level)) }
        HapticResult.entries.forEach { assertEquals(it, HapticResult.fromCode(it.code)) }
    }

    @Test
    fun `wire-format ids are unique`() {
        // A duplicate id would make fromId ambiguous and the ABI unfixable after release.
        assertEquals(HapticPattern.entries.size, HapticPattern.entries.map { it.id }.toSet().size)
        assertEquals(HapticTier.entries.size, HapticTier.entries.map { it.level }.toSet().size)
        assertEquals(HapticResult.entries.size, HapticResult.entries.map { it.code }.toSet().size)
    }
}
