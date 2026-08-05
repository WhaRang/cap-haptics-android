package com.cap.haptics.core.pattern

import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.ViewFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternRegistryTest {

    private val playableTiers =
        listOf(HapticTier.WAVEFORM, HapticTier.PREDEFINED, HapticTier.COMPOSED)

    @Test
    fun `every pattern has a rendering at every playable tier`() {
        // The "never silently no-op" rule, as a property rather than an intention. A missing
        // cell reaches a user as nothing happening, which is indistinguishable from a dead
        // motor -- so the matrix has to be provably complete, not carefully written.
        HapticPattern.entries.forEach { pattern ->
            playableTiers.forEach { tier ->
                assertNotNull(
                    "no rendering for $pattern at $tier",
                    PatternRegistry.renderingFor(pattern, tier),
                )
            }
        }
    }

    @Test
    fun `a device with no motor has nothing to render`() {
        HapticPattern.entries.forEach { pattern ->
            assertNull(PatternRegistry.renderingFor(pattern, HapticTier.NONE))
        }
    }

    @Test
    fun `the composed tier always composes`() {
        HapticPattern.entries.forEach { pattern ->
            val rendering = PatternRegistry.renderingFor(pattern, HapticTier.COMPOSED)
            assertTrue(
                "$pattern is not composed at T3",
                rendering is Rendering.Composed,
            )
        }
    }

    @Test
    fun `the waveform tier always uses waveforms`() {
        HapticPattern.entries.forEach { pattern ->
            val rendering = PatternRegistry.renderingFor(pattern, HapticTier.WAVEFORM)
            assertTrue("$pattern is not a waveform at T1", rendering is Rendering.Wave)
        }
    }

    @Test
    fun `multi-beat patterns use waveforms even at the predefined tier`() {
        // Documents a real limitation rather than a shortcut: the four platform effects
        // cannot be sequenced without scheduling separate vibrate calls, and a Handler
        // between beats lets system load smear the rhythm.
        listOf(HapticPattern.SUCCESS, HapticPattern.ERROR, HapticPattern.HEARTBEAT, HapticPattern.RAMP_UP)
            .forEach { pattern ->
                assertTrue(
                    "$pattern should fall back to a waveform at T2",
                    PatternRegistry.renderingFor(pattern, HapticTier.PREDEFINED) is Rendering.Wave,
                )
            }
    }

    @Test
    fun `single-beat patterns use tuned effects at the predefined tier`() {
        listOf(
            HapticPattern.SELECTION,
            HapticPattern.IMPACT_LIGHT,
            HapticPattern.IMPACT_MEDIUM,
            HapticPattern.IMPACT_HEAVY,
            HapticPattern.WARNING,
            HapticPattern.LONG_PRESS,
        ).forEach { pattern ->
            assertTrue(
                "$pattern should use a tuned effect at T2",
                PatternRegistry.renderingFor(pattern, HapticTier.PREDEFINED) is Rendering.Effect,
            )
        }
    }

    @Test
    fun `impacts stay ordered at every tier`() {
        // Pins the relation, not the numbers, so retuning by feel does not mean rewriting
        // the test -- but an accidental inversion still fails.
        val light = peakOf(HapticPattern.IMPACT_LIGHT, HapticTier.WAVEFORM)
        val medium = peakOf(HapticPattern.IMPACT_MEDIUM, HapticTier.WAVEFORM)
        val heavy = peakOf(HapticPattern.IMPACT_HEAVY, HapticTier.WAVEFORM)
        assertTrue("T1 impacts inverted", light < medium && medium < heavy)

        val lightScale = composedScaleOf(HapticPattern.IMPACT_LIGHT)
        val mediumScale = composedScaleOf(HapticPattern.IMPACT_MEDIUM)
        val heavyScale = composedScaleOf(HapticPattern.IMPACT_HEAVY)
        assertTrue("T3 impacts inverted", lightScale < mediumScale && mediumScale < heavyScale)
    }

    @Test
    fun `selection is the lightest thing the library can produce`() {
        val selection = peakOf(HapticPattern.SELECTION, HapticTier.WAVEFORM)
        val lightest = peakOf(HapticPattern.IMPACT_LIGHT, HapticTier.WAVEFORM)
        assertTrue(selection < lightest)
    }

    @Test
    fun `waveform renderings survive losing amplitude control`() {
        // RAMP_UP is the deliberate exception: a swell is continuously on, so it has no
        // silent segments to preserve and cannot be rendered at all without amplitude
        // control. Every other pattern must degrade cleanly.
        HapticPattern.entries
            .filterNot { it == HapticPattern.RAMP_UP }
            .forEach { pattern ->
                val rendering =
                    PatternRegistry.renderingFor(pattern, HapticTier.WAVEFORM) as Rendering.Wave
                assertTrue(
                    "$pattern breaks the alternating convention",
                    rendering.waveform.followsAlternatingConvention,
                )
            }
    }

    @Test
    fun `only genuine UI gestures route to the system channel`() {
        // Routing SUCCESS to CONFIRM and ERROR to REJECT is tempting and wrong: it would
        // surrender the tuned compositions, the intensity dial and the tier story for
        // patterns a game invents its own meaning for.
        HapticPattern.entries.forEach { pattern ->
            val feedback = PatternRegistry.viewFeedbackFor(pattern)
            if (pattern == HapticPattern.LONG_PRESS) {
                assertEquals(ViewFeedback.LONG_PRESS, feedback)
            } else {
                assertNull("$pattern should not use the system channel", feedback)
            }
        }
    }

    @Test
    fun `patterns routed to the system channel still have a fallback at every tier`() {
        // The channel needs an Activity, so it can be absent. A pattern that only existed as
        // view feedback would silently vanish for those callers.
        HapticPattern.entries
            .filter { PatternRegistry.viewFeedbackFor(it) != null }
            .forEach { pattern ->
                playableTiers.forEach { tier ->
                    assertNotNull(
                        "$pattern has no fallback at $tier",
                        PatternRegistry.renderingFor(pattern, tier),
                    )
                }
            }
    }

    @Test
    fun `error is three beats`() {
        val rendering =
            PatternRegistry.renderingFor(HapticPattern.ERROR, HapticTier.COMPOSED) as Rendering.Composed
        assertEquals(3, rendering.steps.size)
    }

    private fun peakOf(pattern: HapticPattern, tier: HapticTier): Int {
        val rendering = PatternRegistry.renderingFor(pattern, tier) as Rendering.Wave
        return rendering.waveform.amplitudes!!.max()
    }

    private fun composedScaleOf(pattern: HapticPattern): Float {
        val rendering =
            PatternRegistry.renderingFor(pattern, HapticTier.COMPOSED) as Rendering.Composed
        return rendering.steps.maxOf { it.scale }
    }
}
