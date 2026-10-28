package com.phonediagnostic.data

import android.hardware.Sensor
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import java.util.Locale

/**
 * Deep diagnostic probes that expand beyond the base collector.
 * All reads go through the supplied [readNode] so Shizuku/root
 * elevated access is shared with DeviceInfoCollector.
 */
object DeepProbes {

    data class MemExtras(
        val cachedMb: Long?,
        val buffersMb: Long?,
        val swapTotalMb: Long?,
        val swapFreeMb: Long?,
        val zramMb: Long?,
        val activeMb: Long?
    ) {
        companion object {
            val NONE = MemExtras(null, null, null, null, null, null)
        }
    }

    data class CameraDeep(
        val physicalIds: String,
        val capabilities: String,
        val activeArraySize: String,
        val isoRange: String,
        val exposureRange: String,
        val hasFlash: Boolean?
    )

    /**
     * One cpufreq policy: a group of cores that share a clock, i.e. a cluster
     * on big.LITTLE parts. [cpus] comes from related_cpus.
     */
    data class CpuCluster(
        val cpus: List<Int>,
        val maxMhz: Int?,
        val governor: String?,
        val availableMhz: List<Int>
    )

    /**
     * Reads every cpufreq policy once. A policy directory is named after its
     * first core, so cores already covered by a found cluster are skipped,
     * which keeps this to roughly one read set per cluster rather than per core.
     */
    fun cpuClusters(readNode: (String) -> String?, coreCount: Int): List<CpuCluster> {
        val clusters = ArrayList<CpuCluster>()
        val covered = HashSet<Int>()
        for (i in 0 until coreCount.coerceIn(1, 64)) {
            if (i in covered) continue
            val base = "/sys/devices/system/cpu/cpufreq/policy$i"
            val related = readNode("$base/related_cpus")?.let(::parseCpuList).orEmpty()
            // Older kernels have no policyN directories; fall back to per-core nodes.
            val dir = if (related.isNotEmpty()) base else "/sys/devices/system/cpu/cpu$i/cpufreq"
            val maxMhz = readNode("$dir/cpuinfo_max_freq")?.trim()?.toLongOrNull()?.div(1000)?.toInt()
            val governor = readNode("$dir/scaling_governor")?.trim()?.takeIf { it.isNotEmpty() }
            if (related.isEmpty() && maxMhz == null && governor == null) continue
            val cpus = related.ifEmpty { listOf(i) }
            covered += cpus
            clusters += CpuCluster(
                cpus = cpus,
                maxMhz = maxMhz,
                governor = governor,
                availableMhz = parseFreqListMhz(readNode("$dir/scaling_available_frequencies"))
            )
        }
        // Per-core fallback yields one entry per core; merge cores that look identical.
        return if (clusters.all { it.cpus.size == 1 } && clusters.size > 1) {
            clusters.groupBy { it.maxMhz to it.governor }.values.map { group ->
                CpuCluster(
                    cpus = group.flatMap { it.cpus }.sorted(),
                    maxMhz = group.first().maxMhz,
                    governor = group.first().governor,
                    availableMhz = group.flatMap { it.availableMhz }.distinct().sorted()
                )
            }
        } else {
            clusters
        }.sortedBy { it.cpus.firstOrNull() ?: 0 }
    }

    /** Distinct governors across clusters, e.g. ["schedutil"] rather than one per core. */
    fun cpuGovernors(clusters: List<CpuCluster>): List<String> =
        clusters.mapNotNull { it.governor }.distinct()

    /** Every frequency any cluster can run at, ascending, so big cores are included. */
    fun availableFreqMhz(clusters: List<CpuCluster>): List<Int> =
        clusters.flatMap { it.availableMhz }.distinct().sorted()

    fun onlineCores(readNode: (String) -> String?): List<Int> =
        readNode("/sys/devices/system/cpu/online")?.let(::parseCpuList).orEmpty()

    /**
     * Core layout from cluster sizes and peak clocks, e.g. "4 + 3 + 1 cores ·
     * 2.0 / 2.8 / 3.3 GHz". Based on each cluster's maximum rather than the
     * current clocks, which move with load and say nothing about topology.
     */
    fun topologyHint(clusters: List<CpuCluster>, coreCount: Int): String {
        if (clusters.isEmpty()) return if (coreCount > 0) "$coreCount cores" else ""
        val ordered = clusters.sortedWith(compareBy({ it.maxMhz ?: 0 }, { it.cpus.firstOrNull() ?: 0 }))
        val speeds = ordered.mapNotNull { it.maxMhz }
        val ghz = speeds.joinToString(" / ") { String.format(Locale.US, "%.1f", it / 1000.0) }
        return if (ordered.size == 1) {
            val n = ordered[0].cpus.size.coerceAtLeast(coreCount)
            if (speeds.isNotEmpty()) "$n cores · up to $ghz GHz" else "$n cores"
        } else {
            val sizes = ordered.joinToString(" + ") { it.cpus.size.toString() }
            if (speeds.size == ordered.size) "$sizes cores · $ghz GHz" else "$sizes cores"
        }
    }

    /** Parses kernel CPU lists in both "0-3,6" (online) and "0 1 2 3" (related_cpus) forms. */
    internal fun parseCpuList(raw: String): List<Int> {
        val cores = ArrayList<Int>()
        for (part in raw.trim().split(Regex("[,\\s]+"))) {
            if (part.isEmpty()) continue
            if ("-" in part) {
                val (a, b) = part.split("-", limit = 2)
                val start = a.toIntOrNull() ?: continue
                val end = b.toIntOrNull() ?: continue
                if (end >= start && end - start < 256) cores.addAll(start..end)
            } else {
                part.toIntOrNull()?.let { cores.add(it) }
            }
        }
        return cores.distinct().sorted()
    }

    /** kHz list from scaling_available_frequencies to distinct ascending MHz. */
    internal fun parseFreqListMhz(raw: String?): List<Int> =
        raw?.trim()?.split(Regex("\\s+"))
            ?.mapNotNull { it.toLongOrNull()?.div(1000)?.toInt() }
            ?.filter { it > 0 }
            ?.distinct()
            ?.sorted()
            .orEmpty()

    fun meminfoExtras(readNode: (String) -> String?): MemExtras {
        fun kbToMb(kb: Long?) = kb?.div(1024)
        var cached: Long? = null
        var buffers: Long? = null
        var swapTotal: Long? = null
        var swapFree: Long? = null
        var active: Long? = null
        val text = readNode("/proc/meminfo") ?: return MemExtras.NONE
        for (line in text.lineSequence()) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue
            val key = parts[0].trimEnd(':')
            val kb = parts[1].toLongOrNull() ?: continue
            when (key) {
                "Cached" -> cached = kb
                "Buffers" -> buffers = kb
                "SwapTotal" -> swapTotal = kb
                "SwapFree" -> swapFree = kb
                "Active" -> active = kb
            }
        }
        // SwapTotal already covers the ZRAM device's size on Android, so report
        // what the compressed pages really cost in RAM: mm_stat's mem_used_total.
        val zram = readNode("/sys/block/zram0/mm_stat")?.let(::parseZramUsedMb)
        return MemExtras(
            cachedMb = kbToMb(cached),
            buffersMb = kbToMb(buffers),
            swapTotalMb = kbToMb(swapTotal),
            swapFreeMb = kbToMb(swapFree),
            zramMb = zram,
            activeMb = kbToMb(active)
        )
    }

    /** mm_stat fields: orig_data_size compr_data_size mem_used_total ... (bytes). */
    internal fun parseZramUsedMb(mmStat: String): Long? =
        mmStat.trim().split(Regex("\\s+")).getOrNull(2)?.toLongOrNull()?.div(1024 * 1024)

    fun selinuxMode(readNode: (String) -> String?): String {
        val enforce = readNode("/sys/fs/selinux/enforce")?.trim()
        return when (enforce) {
            "1" -> "Enforcing"
            "0" -> "Permissive"
            else -> {
                val status = readNode("/sys/fs/selinux/status")?.trim().orEmpty()
                if (status.isNotBlank()) status else "Unknown"
            }
        }
    }

    fun thermalTypeLabel(type: String): String {
        val t = type.lowercase(Locale.US)
        return when {
            "cpu" in t -> "CPU"
            "gpu" in t -> "GPU"
            "bat" in t -> "Battery"
            "skin" in t || "surface" in t -> "Skin"
            // "pa" (power amplifier) only as its own token, so e.g. "panel" is not RF.
            "xo" in t || "modem" in t || Regex("(^|[-_])pa([-_\\d]|$)").containsMatchIn(t) -> "Modem/RF"
            "npu" in t || "dsp" in t -> "NPU/DSP"
            "charger" in t || "usb" in t -> "Charger"
            else -> type
        }
    }

    fun sensorReportingMode(mode: Int): String = when (mode) {
        Sensor.REPORTING_MODE_CONTINUOUS -> "continuous"
        Sensor.REPORTING_MODE_ON_CHANGE -> "on-change"
        Sensor.REPORTING_MODE_ONE_SHOT -> "one-shot"
        Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "special"
        else -> ""
    }

    fun cameraExtras(chars: CameraCharacteristics): CameraDeep {
        val physical = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("NewApi")
                chars.physicalCameraIds.joinToString(", ")
            } else ""
        } catch (_: Exception) { "" }
        val caps = try {
            val arr = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            arr.map { cap ->
                when (cap) {
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "COMPAT"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "HFR"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL"
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONO"
                    else -> null
                }
            }.filterNotNull().joinToString(", ")
        } catch (_: Exception) { "" }
        val active = try {
            val r = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (r != null) "${r.width()}×${r.height()}" else ""
        } catch (_: Exception) { "" }
        val iso = try {
            val range = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            if (range != null) "${range.lower}–${range.upper}" else ""
        } catch (_: Exception) { "" }
        val exposure = try {
            val range = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            if (range != null) {
                val lo = range.lower / 1_000_000.0
                val hi = range.upper / 1_000_000.0
                String.format(Locale.US, "%.2f–%.0f ms", lo, hi)
            } else ""
        } catch (_: Exception) { "" }
        val flash = try {
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        } catch (_: Exception) { null }
        return CameraDeep(
            physicalIds = physical,
            capabilities = caps,
            activeArraySize = active,
            isoRange = iso,
            exposureRange = exposure,
            hasFlash = flash
        )
    }

    /**
     * JEDEC device life-time estimate (eMMC life_time, UFS life_time_estimation_a/b):
     * 0x01 = 0–10% of rated erase cycles used ... 0x0A = 90–100%, 0x0B = exceeded.
     */
    fun storageLifeUsed(raw: String?): String? {
        val values = raw.orEmpty().trim().split(Regex("\\s+"))
            .mapNotNull { it.removePrefix("0x").removePrefix("0X").toIntOrNull(16) }
            .filter { it in 1..0x0B }
        val worst = values.maxOrNull() ?: return null
        return if (worst == 0x0B) "Rated life exceeded" else "${(worst - 1) * 10}–${worst * 10}% used"
    }

    /** JEDEC pre-EOL info: how much of the spare block pool is gone. */
    fun storagePreEol(raw: String?): String? =
        when (raw?.trim()?.removePrefix("0x")?.removePrefix("0X")?.toIntOrNull(16)) {
            1 -> "Normal"
            2 -> "Warning — 80% of spare blocks used"
            3 -> "Urgent — spare blocks nearly gone"
            else -> null
        }

    /**
     * Parses "name active_time_ms" lines (one per /sys/class/wakeup source) into
     * the [limit] sources that kept the phone awake longest.
     */
    fun topWakeSources(lines: String?, limit: Int = 5): List<Pair<String, Long>> =
        lines.orEmpty().lineSequence()
            .mapNotNull { line ->
                val name = line.substringBeforeLast(' ').trim()
                val ms = line.substringAfterLast(' ').trim().toLongOrNull()
                if (name.isEmpty() || ms == null || ms <= 0) null else name to ms
            }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()

    /** Share of time since boot the CPU spent suspended. */
    fun deepSleepPercent(sinceBootMs: Long, awakeMs: Long): Int? {
        if (sinceBootMs <= 0 || awakeMs < 0 || awakeMs > sinceBootMs) return null
        return (((sinceBootMs - awakeMs) * 100) / sinceBootMs).toInt()
    }

    /** PowerManager.THERMAL_STATUS_* as words. */
    fun thermalStatusName(status: Int): String = when (status) {
        0 -> "None"
        1 -> "Light"
        2 -> "Moderate"
        3 -> "Severe"
        4 -> "Critical"
        5 -> "Emergency"
        6 -> "Shutdown"
        else -> "Unknown"
    }

    /**
     * Throughput at the end of a load test as a share of its best stretch.
     * [perSecond] is ops counted in each one-second interval. Well under 100
     * means the SoC slowed down, almost always from heat.
     */
    fun sustainedPercent(perSecond: List<Long>, window: Int = 10): Int? {
        if (perSecond.size < window * 2) return null
        val peak = perSecond.windowed(window).maxOf { it.average() }
        if (peak <= 0.0) return null
        val end = perSecond.takeLast(window).average()
        return ((end * 100) / peak).toInt().coerceIn(0, 100)
    }
}
