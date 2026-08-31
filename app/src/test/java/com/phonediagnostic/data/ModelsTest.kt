package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the derived values the UI reads directly. Each one has a divide-by-zero
 * or overflow path that is easy to reintroduce and invisible until a device
 * reports an unusual figure.
 */
class ModelsTest {

    @Test
    fun `storage percentages are derived from bytes`() {
        val volume = StorageVolumeInfo(
            name = "Internal", path = "/data", description = "Primary",
            totalBytes = 128L * 1024 * 1024 * 1024,
            freeBytes = 32L * 1024 * 1024 * 1024,
            usedBytes = 96L * 1024 * 1024 * 1024,
            isRemovable = false, isPrimary = true, state = "mounted"
        )
        assertEquals(128.0, volume.totalGb, 0.001)
        assertEquals(32.0, volume.freeGb, 0.001)
        assertEquals(96.0, volume.usedGb, 0.001)
        assertEquals(75, volume.usagePercent)
    }

    @Test
    fun `an unmounted volume reporting zero total does not divide by zero`() {
        val volume = StorageVolumeInfo(
            name = "SD", path = "", description = "Secondary",
            totalBytes = 0L, freeBytes = 0L, usedBytes = 0L,
            isRemovable = true, isPrimary = false, state = "unmounted"
        )
        assertEquals(0, volume.usagePercent)
        assertEquals(0.0, volume.totalGb, 0.001)
    }

    @Test
    fun `large volumes do not overflow the percentage calculation`() {
        // 8 TB in bytes times 100 exceeds Int range; the maths must stay in Long.
        val eightTb = 8L * 1024 * 1024 * 1024 * 1024
        val volume = StorageVolumeInfo(
            name = "Big", path = "/big", description = "Primary",
            totalBytes = eightTb, freeBytes = eightTb / 4, usedBytes = eightTb / 4 * 3,
            isRemovable = false, isPrimary = true, state = "mounted"
        )
        assertEquals(75, volume.usagePercent)
    }

    @Test
    fun `app storage total sums its three buckets`() {
        val entry = AppStorageEntry(
            packageName = "com.example", appLabel = "Example",
            appBytes = 100L, dataBytes = 20L, cacheBytes = 3L, isSystemApp = false
        )
        assertEquals(123L, entry.totalBytes)
    }

    @Test
    fun `metric sample ram percent handles a zero total`() {
        val sample = MetricSample(0L, 50, 30f, 512L, 0L, false)
        assertEquals(0, sample.ramPercent)
    }

    @Test
    fun `metric sample ram percent is rounded down`() {
        val sample = MetricSample(0L, 50, 30f, 3000L, 4000L, true)
        assertEquals(75, sample.ramPercent)
    }

    @Test
    fun `load test progress fraction is clamped to the unit interval`() {
        fun progress(elapsed: Int, duration: Int) = LoadTestProgress(
            running = true, durationSec = duration, elapsedSec = elapsed,
            operations = 0L, opsPerSec = 0L, threads = 4,
            ramUsedMb = 0L, batteryPct = 0, tempC = 0f, phase = "test"
        )
        assertEquals(0.5f, progress(30, 60).fraction, 0.001f)
        // Overshoot and a zero duration must not produce a bar past full or NaN.
        assertEquals(1f, progress(120, 60).fraction, 0.001f)
        assertEquals(0f, progress(10, 0).fraction, 0.001f)
    }

    @Test
    fun `load test durations are the ones the UI offers`() {
        assertEquals(listOf(60, 300, 600), LoadTester.ALLOWED_DURATIONS_SEC)
        assertTrue(LoadTester.ALLOWED_DURATIONS_SEC.all { it % 60 == 0 })
    }
}
