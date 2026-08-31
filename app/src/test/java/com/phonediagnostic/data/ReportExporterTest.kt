package com.phonediagnostic.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export is the one thing a user hands to someone else, so it has to stay
 * parseable and must not leak placeholder text.
 */
class ReportExporterTest {

    private fun report(
        model: String = "Pixel 7",
        thermals: List<ThermalZone> = emptyList(),
        sensors: List<SensorEntry> = emptyList()
    ) = FullDeviceReport(
        overview = DeviceOverview(
            manufacturer = "Google", model = model, brand = "Google",
            device = "panther", product = "panther", androidVersion = "15",
            apiLevel = 35, buildId = "AP4A", securityPatch = "2026-08-01",
            uptime = "3h 12m"
        ),
        cpu = CpuInfo(
            cores = 8, architecture = "ARM64", supportedAbis = listOf("arm64-v8a"),
            hardware = "panther", processor = "Tensor G2", boardPlatform = "gs201",
            currentFreqMhz = listOf(1800, 2200), minFreqMhz = 300, maxFreqMhz = 2850
        ),
        gpu = GpuInfo("Mali-G710", "ARM", "OpenGL ES 3.2"),
        battery = BatteryInfo(
            level = 82, status = "Discharging", health = "Good", temperature = 31.4f,
            voltage = 4102, technology = "Li-ion", isCharging = false,
            powerSource = "Battery", currentNowMa = -320, currentAvgMa = -298
        ),
        memory = MemoryInfo(
            totalRamMb = 7800L, availableRamMb = 2100L, usedRamMb = 5700L, usagePercent = 73
        ),
        storage = StorageInfo(
            totalInternalGb = 128.0, freeInternalGb = 40.5, usedInternalGb = 87.5,
            usagePercent = 68
        ),
        display = DisplayInfo(1080, 2400, 420, 2.625f, 90f, 6.3),
        network = NetworkInfo(
            isConnected = true, networkType = "Wi-Fi", latencyMs = 23L,
            latencyTarget = "8.8.8.8:53", latencyStatus = "OK"
        ),
        sensors = sensors,
        thermals = thermals
    )

    @Test
    fun `json export is parseable and carries the key sections`() {
        val json = JSONObject(ReportExporter.toJson(report()))
        assertEquals("Phone Diagnostic Tool", json.getString("app"))
        listOf("overview", "cpu", "gpu", "battery", "memory", "network", "storage", "display")
            .forEach { assertTrue("missing section: $it", json.has(it)) }
        assertEquals("Pixel 7", json.getJSONObject("overview").getString("model"))
        assertEquals(8, json.getJSONObject("cpu").getInt("cores"))
        assertEquals(82, json.getJSONObject("battery").getInt("level"))
    }

    @Test
    fun `json export represents unavailable readings as null rather than zero`() {
        val bare = report().copy(
            battery = report().battery.copy(currentNowMa = null, currentAvgMa = null)
        )
        val battery = JSONObject(ReportExporter.toJson(bare)).getJSONObject("battery")
        // A zero here would read as "drawing no current", which is a different claim.
        assertTrue(battery.isNull("currentNowMa"))
        assertTrue(battery.isNull("currentAvgMa"))
    }

    @Test
    fun `text export uses a decimal point regardless of default locale`() {
        val original = java.util.Locale.getDefault()
        try {
            // Germany formats decimals with a comma; the report must not follow suit.
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val text = ReportExporter.toShareText(report())
            assertTrue("expected 31.4 in: $text", text.contains("31.4"))
            assertFalse("comma decimal leaked into the report", text.contains("31,4"))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun `text export omits the thermals section when nothing is readable`() {
        assertFalse(ReportExporter.toShareText(report()).contains("THERMALS"))
        val withZones = report(thermals = listOf(ThermalZone("thermal_zone0", 42.5f, "cpu")))
        val text = ReportExporter.toShareText(withZones)
        assertTrue(text.contains("THERMALS (1)"))
        assertTrue(text.contains("42.5"))
    }

    @Test
    fun `text export truncates very long sensor lists but says how many were dropped`() {
        val many = (1..45).map {
            SensorEntry("Sensor $it", "Type", "Vendor", 0.1f, 0.01f, 100f, 10000)
        }
        val text = ReportExporter.toShareText(report(sensors = many))
        assertTrue(text.contains("SENSORS (45)"))
        assertTrue(text.contains("+5 more"))
    }

    @Test
    fun `suggested filename is filesystem safe and carries the extension`() {
        val name = ReportExporter.suggestedFileName(report(model = "Pixel 7 Pro"), "txt")
        assertTrue(name.startsWith("phone-diagnostic-Pixel_7_Pro-"))
        assertTrue(name.endsWith(".txt"))
        assertFalse(name.contains(" "))
        assertFalse(name.contains("/"))
    }

    @Test
    fun `suggested filename survives a model name with no usable characters`() {
        val name = ReportExporter.suggestedFileName(report(model = "///"), "json")
        assertTrue("expected the device fallback, got: $name", name.contains("device"))
        assertTrue(name.endsWith(".json"))
        // A leading or doubled separator would come from an untrimmed placeholder.
        assertFalse(name.contains("--"))
    }
}
