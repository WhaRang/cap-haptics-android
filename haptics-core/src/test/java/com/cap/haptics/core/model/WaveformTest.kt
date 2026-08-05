package com.cap.haptics.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every case here is one the platform would have thrown on. Catching them in pure Kotlin
 * keeps `IllegalArgumentException` out of the JNI path, where it would become a native
 * crash rather than a result code.
 */
class WaveformTest {

    @Test
    fun `a well-formed waveform is accepted`() {
        val waveform = Waveform.create(
            timingsMs = longArrayOf(0, 30, 60, 50),
            amplitudes = intArrayOf(0, 120, 0, 220),
        )
        assertNotNull(waveform)
        assertEquals(140L, waveform!!.totalDurationMs)
    }

    @Test
    fun `empty timings are rejected`() {
        assertNull(Waveform.create(longArrayOf()))
    }

    @Test
    fun `negative timings are rejected`() {
        assertNull(Waveform.create(longArrayOf(0, -5)))
    }

    @Test
    fun `an all-zero envelope is rejected as silence`() {
        // The platform accepts this and plays nothing, which is indistinguishable from a bug
        // when someone is holding a phone feeling for a buzz.
        assertNull(Waveform.create(longArrayOf(0, 0, 0)))
    }

    @Test
    fun `mismatched amplitude length is rejected`() {
        assertNull(Waveform.create(longArrayOf(0, 30, 60), intArrayOf(0, 255)))
    }

    @Test
    fun `out of range amplitudes are rejected`() {
        assertNull(Waveform.create(longArrayOf(0, 30), intArrayOf(0, 256)))
        assertNull(Waveform.create(longArrayOf(0, 30), intArrayOf(0, -1)))
    }

    @Test
    fun `out of bounds repeat index is rejected`() {
        assertNull(Waveform.create(longArrayOf(0, 30), repeatIndex = 7))
        assertNull(Waveform.create(longArrayOf(0, 30), repeatIndex = -2))
    }

    @Test
    fun `repeat index of NO_REPEAT is valid`() {
        assertNotNull(Waveform.create(longArrayOf(0, 30), repeatIndex = Waveform.NO_REPEAT))
    }

    @Test
    fun `dropping amplitudes preserves the rhythm`() {
        val full = Waveform.create(longArrayOf(0, 30, 60, 50), intArrayOf(0, 120, 0, 220))!!
        val stripped = full.withoutAmplitudes()

        assertNull(stripped.amplitudes)
        assertEquals(full.timingsMs.toList(), stripped.timingsMs.toList())
    }

    @Test
    fun `oneShot builds an off-then-on envelope`() {
        val waveform = Waveform.oneShot(durationMs = 40, amplitude = 200)!!

        assertEquals(listOf(0L, 40L), waveform.timingsMs.toList())
        assertEquals(listOf(0, 200), waveform.amplitudes!!.toList())
    }

    @Test
    fun `defensive copy prevents caller mutation`() {
        val timings = longArrayOf(0, 30)
        val waveform = Waveform.create(timings)!!
        timings[1] = 9999

        assertEquals(30L, waveform.timingsMs[1])
    }
}
