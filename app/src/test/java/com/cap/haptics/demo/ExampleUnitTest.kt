package com.cap.haptics.demo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * The real JVM test suite lives in :haptics-core -- see PLAN.md section 5.2. Tier
 * selection, the pattern registry and intensity scaling are all pure functions over
 * a HapticCapabilities data class precisely so they can be tested here, with no
 * device attached.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
