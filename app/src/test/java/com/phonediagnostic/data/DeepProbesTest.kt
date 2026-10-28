package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepProbesTest {

    /** A fake sysfs: unknown paths read as null, like an unreadable node. */
    private fun nodes(vararg entries: Pair<String, String>): (String) -> String? {
        val map = entries.toMap()
        return { map[it] }
    }

    private val policy = "/sys/devices/system/cpu/cpufreq/policy"

    /** Snapdragon 8 Gen 2 style 3 + 4 + 1 layout, with cpu1-2 never readable as policies. */
    private val triCluster = nodes(
        "${policy}0/related_cpus" to "0 1 2",
        "${policy}0/cpuinfo_max_freq" to "2016000",
        "${policy}0/scaling_governor" to "schedutil",
        "${policy}0/scaling_available_frequencies" to "307200 1017600 2016000",
        "${policy}3/related_cpus" to "3 4 5 6",
        "${policy}3/cpuinfo_max_freq" to "2803200",
        "${policy}3/scaling_governor" to "schedutil",
        "${policy}3/scaling_available_frequencies" to "499200 2803200",
        "${policy}7/related_cpus" to "7",
        "${policy}7/cpuinfo_max_freq" to "3187200",
        "${policy}7/scaling_governor" to "performance",
        "/sys/devices/system/cpu/online" to "0-5,7"
    )

    @Test
    fun clustersComeFromPoliciesNotCores() {
        val clusters = DeepProbes.cpuClusters(triCluster, 8)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4, 5, 6), listOf(7)), clusters.map { it.cpus })
        assertEquals(listOf(2016, 2803, 3187), clusters.map { it.maxMhz })
    }

    @Test
    fun governorsAreDistinctAcrossClusters() {
        val clusters = DeepProbes.cpuClusters(triCluster, 8)
        assertEquals(listOf("schedutil", "performance"), DeepProbes.cpuGovernors(clusters))
    }

    @Test
    fun availableFrequenciesIncludeEveryCluster() {
        val clusters = DeepProbes.cpuClusters(triCluster, 8)
        assertEquals(listOf(307, 499, 1017, 2016, 2803), DeepProbes.availableFreqMhz(clusters))
    }

    @Test
    fun topologyUsesClusterSizesAndPeakClocks() {
        val clusters = DeepProbes.cpuClusters(triCluster, 8)
        assertEquals("3 + 4 + 1 cores · 2.0 / 2.8 / 3.2 GHz", DeepProbes.topologyHint(clusters, 8))
    }

    @Test
    fun perCoreFallbackMergesIdenticalCores() {
        val cpu = "/sys/devices/system/cpu/cpu"
        val read = nodes(
            "${cpu}0/cpufreq/cpuinfo_max_freq" to "1800000",
            "${cpu}1/cpufreq/cpuinfo_max_freq" to "1800000",
            "${cpu}2/cpufreq/cpuinfo_max_freq" to "2400000",
            "${cpu}3/cpufreq/cpuinfo_max_freq" to "2400000"
        )
        val clusters = DeepProbes.cpuClusters(read, 4)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), clusters.map { it.cpus })
        assertEquals("2 + 2 cores · 1.8 / 2.4 GHz", DeepProbes.topologyHint(clusters, 4))
    }

    @Test
    fun topologyWithoutCpufreqFallsBackToCoreCount() {
        assertEquals("8 cores", DeepProbes.topologyHint(emptyList(), 8))
    }

    @Test
    fun onlineCoresParsesRangesAndSingles() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 7), DeepProbes.onlineCores(triCluster))
        assertEquals(listOf(0, 1, 2, 3), DeepProbes.parseCpuList("0 1 2 3\n"))
        assertEquals(emptyList<Int>(), DeepProbes.parseCpuList("garbage"))
    }

    @Test
    fun meminfoExtrasConvertsKbToMb() {
        val read = nodes(
            "/proc/meminfo" to """
                MemTotal:        7864320 kB
                Buffers:            8192 kB
                Cached:          2097152 kB
                SwapCached:        10240 kB
                Active:          3145728 kB
                SwapTotal:       4194304 kB
                SwapFree:        1048576 kB
            """.trimIndent(),
            "/sys/block/zram0/mm_stat" to "3221225472 1073741824 1132462080 0 1132462080 100 200 0"
        )
        val extras = DeepProbes.meminfoExtras(read)
        assertEquals(2048L, extras.cachedMb)
        assertEquals(8L, extras.buffersMb)
        assertEquals(3072L, extras.activeMb)
        assertEquals(4096L, extras.swapTotalMb)
        assertEquals(1024L, extras.swapFreeMb)
        // mem_used_total (third field), not the device's configured size.
        assertEquals(1080L, extras.zramMb)
    }

    @Test
    fun meminfoExtrasAreNullWhenUnreadable() {
        val extras = DeepProbes.meminfoExtras(nodes())
        assertNull(extras.cachedMb)
        assertNull(extras.zramMb)
    }

    @Test
    fun thermalLabelsMapCommonTypes() {
        assertEquals("CPU", DeepProbes.thermalTypeLabel("cpu-1-0-usr"))
        assertEquals("Battery", DeepProbes.thermalTypeLabel("battery"))
        assertEquals("Modem/RF", DeepProbes.thermalTypeLabel("pa-therm1"))
        // "pa" inside another word is not a power amplifier.
        assertEquals("panel-therm", DeepProbes.thermalTypeLabel("panel-therm"))
    }
}
