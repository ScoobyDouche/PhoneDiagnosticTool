package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricHistoryTest {

    private fun sample(minute: Int, pct: Int, charging: Boolean = false) = MetricSample(
        timestampMs = minute * 60_000L,
        batteryPct = pct,
        batteryTempC = 30f,
        ramUsedMb = 4000,
        ramTotalMb = 8000,
        charging = charging
    )

    @Test
    fun drainRateUsesLatestUnpluggedStretch() {
        val samples = listOf(
            sample(0, 50, charging = true),
            sample(10, 60, charging = true),
            sample(20, 100),
            sample(80, 94),
            sample(140, 88)
        )
        // 12% over two hours.
        assertEquals(6f, MetricHistory.drainRatePerHour(samples)!!, 0.01f)
    }

    @Test
    fun drainRateNeedsEnoughTimeAndDrop() {
        assertNull(MetricHistory.drainRatePerHour(listOf(sample(0, 80), sample(10, 70))))
        assertNull(MetricHistory.drainRatePerHour(listOf(sample(0, 80), sample(120, 79))))
        assertNull(MetricHistory.drainRatePerHour(listOf(sample(0, 80, charging = true))))
    }
}
