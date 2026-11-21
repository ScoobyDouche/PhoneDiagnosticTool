package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthScoreTest {

    @Test
    fun flashLifeParsing() {
        assertEquals(80, HealthScore.flashLifeUsedUpper("70–80% used"))
        assertEquals(100, HealthScore.flashLifeUsedUpper("Rated life exceeded"))
        assertNull(HealthScore.flashLifeUsedUpper(null))
    }

    @Test
    fun batteryHealthFallsBackToMeasuredCapacity() {
        val b = BatteryInfo(
            level = 50, status = "Discharging", health = "Good", temperature = 30f, voltage = 3800,
            technology = "Li-ion", isCharging = false, powerSource = "Battery",
            currentNowMa = null, currentAvgMa = null,
            capacityMah = 4000,
            measuredCapacity = MeasuredCapacity(3400, 40, 0L)
        )
        assertEquals(85, HealthScore.batteryHealthPercent(b))
        assertEquals(92, HealthScore.batteryHealthPercent(b.copy(capacityHealthPercent = 92)))
        assertNull(HealthScore.batteryHealthPercent(b.copy(measuredCapacity = null)))
    }

    @Test
    fun baselineRoundTrips() {
        val b = HealthBaseline(1234L, 88, null, 120, 45.5, 900L)
        assertEquals(b, HealthBaseline.fromJson(b.toJson()))
        assertNull(HealthBaseline.fromJson("not json"))
    }
}
