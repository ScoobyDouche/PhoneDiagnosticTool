package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckupResultsTest {

    @Test
    fun countsAndStatus() {
        val r = CheckupResults()
            .with(CheckupTest.TOUCH, CheckStatus.PASS)
            .with(CheckupTest.LIGHT, CheckStatus.FAIL)
            .with(CheckupTest.HEADPHONES, CheckStatus.SKIPPED)
        assertEquals(1, r.passed)
        assertEquals(1, r.failed)
        assertEquals(2, r.run)
        assertEquals(CheckStatus.NOT_RUN, r.status(CheckupTest.BUTTONS))
        assertFalse(r.isEmpty)
        assertTrue(CheckupResults().isEmpty)
    }

    @Test
    fun lightNeedsARealChange() {
        assertFalse(CheckupResults.lightPasses(100f, 104f))
        assertFalse(CheckupResults.lightPasses(300f, 400f))
        assertTrue(CheckupResults.lightPasses(2f, 250f))
        assertTrue(CheckupResults.lightPasses(0f, 12f))
    }

    @Test
    fun motionNeedsTwoAxesTurned() {
        assertFalse(CheckupResults.motionPasses(floatArrayOf(1f, 2f, 0.5f)))
        assertFalse(CheckupResults.motionPasses(floatArrayOf(15f, 2f, 0.5f)))
        assertTrue(CheckupResults.motionPasses(floatArrayOf(15f, 13f, 0.5f)))
    }
}
