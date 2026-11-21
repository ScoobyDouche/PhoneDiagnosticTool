package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorCatalogTest {

    @Test
    fun `standard types are named with their unit`() {
        val accel = SensorCatalog.describe(1, "android.sensor.accelerometer")
        assertEquals("Accelerometer", accel.name)
        assertEquals("m/s²", accel.unit)
        assertEquals(SensorCategory.MOTION, accel.category)
        assertEquals("Hinge angle", SensorCatalog.describe(36, null).name)
    }

    @Test
    fun `vendor sensors take their name from the type string`() {
        assertEquals(
            "Light cct (vendor)",
            SensorCatalog.describe(65587, "com.samsung.sensor.light_cct").name
        )
        // Nothing to go on: keep the number so it can still be looked up.
        assertEquals("Type 65600", SensorCatalog.describe(65600, null).name)
        assertEquals("Type 65600", SensorCatalog.describe(65600, "").name)
    }

    @Test
    fun `only meaningful values are formatted`() {
        // A light sensor pads its array to three entries; only the first is lux.
        assertEquals("120.000 lx", SensorCatalog.formatValues(5, null, floatArrayOf(120f, 0f, 0f)))
        assertEquals(
            "0.100, 9.800, 0.200 m/s²",
            SensorCatalog.formatValues(1, null, floatArrayOf(0.1f, 9.8f, 0.2f))
        )
        // Rotation vectors are unitless and four values long.
        assertEquals(
            "0.000, 0.000, 0.707, 0.707",
            SensorCatalog.formatValues(11, null, floatArrayOf(0f, 0f, 0.707f, 0.707f, 0.1f))
        )
    }

    @Test
    fun `body sensors are not sampled`() {
        assertFalse(SensorCatalog.canSample(19)) // step counter
        assertFalse(SensorCatalog.canSample(21)) // heart rate
        assertTrue(SensorCatalog.canSample(1))
    }

    @Test
    fun `coverage reports which standard sensors are missing`() {
        val coverage = SensorCatalog.coverage(listOf(1, 4, 2, 5, 8)).toMap()
        assertEquals(true, coverage["Accelerometer"])
        assertEquals(true, coverage["Gyroscope"])
        assertEquals(false, coverage["Pressure"])
        assertEquals(false, coverage["Hinge angle"])
    }

    @Test
    fun `sensor key tells wake-up variants apart`() {
        val base = SensorEntry(
            name = "lsm6dso Accelerometer", type = "Accelerometer", vendor = "STMicro",
            powerMa = 0.1f, resolution = 0.001f, maxRange = 78f, minDelayUs = 2500, typeId = 1
        )
        assertFalse(base.key == base.copy(isWakeUp = true).key)
    }
}
