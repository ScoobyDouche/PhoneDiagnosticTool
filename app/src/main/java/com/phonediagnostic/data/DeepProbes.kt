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
    )

    data class CameraDeep(
        val physicalIds: String,
        val capabilities: String,
        val activeArraySize: String,
        val isoRange: String,
        val exposureRange: String,
        val hasFlash: Boolean?
    )

    fun cpuGovernors(readNode: (String) -> String?): List<String> {
        val result = ArrayList<String>()
        for (i in 0 until 16) {
            val g = readNode("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
            if (!g.isNullOrBlank()) result.add(g.trim())
            else break
        }
        if (result.isEmpty()) {
            val g = readNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor")
            if (!g.isNullOrBlank()) result.add(g.trim())
        }
        return result
    }

    fun availableFreqMhz(readNode: (String) -> String?): List<Int> {
        val paths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies",
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies"
        )
        for (p in paths) {
            val raw = readNode(p)?.trim() ?: continue
            val mhz = raw.split(Regex("\\s+"))
                .mapNotNull { it.toLongOrNull()?.div(1000)?.toInt() }
                .filter { it > 0 }
            if (mhz.isNotEmpty()) return mhz.distinct().sorted()
        }
        return emptyList()
    }

    fun onlineCores(readNode: (String) -> String?): List<Int> {
        val online = readNode("/sys/devices/system/cpu/online")?.trim() ?: return emptyList()
        val cores = ArrayList<Int>()
        for (part in online.split(",")) {
            val p = part.trim()
            if ("-" in p) {
                val (a, b) = p.split("-", limit = 2)
                val start = a.toIntOrNull() ?: continue
                val end = b.toIntOrNull() ?: continue
                for (i in start..end) cores.add(i)
            } else {
                p.toIntOrNull()?.let { cores.add(it) }
            }
        }
        return cores
    }

    fun topologyHint(online: List<Int>, freqs: List<Int>, maxF: Int?): String {
        if (online.isEmpty() && freqs.isEmpty()) return ""
        val n = maxOf(online.size, freqs.size).coerceAtLeast(1)
        if (freqs.size >= 2) {
            val sorted = freqs.sorted()
            val low = sorted.first()
            val high = sorted.last()
            if (high > low * 1.3) {
                return "$n cores · mixed freqs ${low}–${high} MHz"
            }
        }
        return if (maxF != null) "$n cores · up to $maxF MHz" else "$n cores"
    }

    fun meminfoExtras(readNode: (String) -> String?): MemExtras {
        fun kbToMb(kb: Long?) = kb?.div(1024)
        var cached: Long? = null
        var buffers: Long? = null
        var swapTotal: Long? = null
        var swapFree: Long? = null
        var active: Long? = null
        val text = readNode("/proc/meminfo") ?: return MemExtras(null, null, null, null, null, null)
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
        var zram: Long? = null
        try {
            val z = readNode("/sys/block/zram0/disksize")?.toLongOrNull()
            if (z != null && z > 0) zram = z / (1024 * 1024)
        } catch (_: Exception) {}
        return MemExtras(
            cachedMb = kbToMb(cached),
            buffersMb = kbToMb(buffers),
            swapTotalMb = kbToMb(swapTotal),
            swapFreeMb = kbToMb(swapFree),
            zramMb = zram,
            activeMb = kbToMb(active)
        )
    }

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
            "xo" in t || "modem" in t || "pa" in t -> "Modem/RF"
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
}
