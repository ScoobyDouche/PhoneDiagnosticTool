package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryCapacityTrackerTest {

    @Test
    fun estimatesFromChargeDelta() {
        // 30% -> 80% took 2000 mAh, so 100% is 4000 mAh.
        assertEquals(4000, BatteryCapacityTracker.estimateFullMah(30, 1_000_000L, 80, 3_000_000L))
    }

    @Test
    fun counterOffsetCancels() {
        assertEquals(
            BatteryCapacityTracker.estimateFullMah(30, 0L, 80, 2_000_000L),
            BatteryCapacityTracker.estimateFullMah(30, 9_000_000L, 80, 11_000_000L)
        )
    }

    @Test
    fun rejectsNarrowSpansAndNonsense() {
        assertNull(BatteryCapacityTracker.estimateFullMah(50, 0L, 60, 400_000L))
        assertNull(BatteryCapacityTracker.estimateFullMah(30, 3_000_000L, 80, 1_000_000L))
        // mAh-scaled counter (vendor quirk) gives an implausible 4 mAh battery.
        assertNull(BatteryCapacityTracker.estimateFullMah(30, 1000L, 80, 3000L))
    }
}
