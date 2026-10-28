package com.phonediagnostic.data

import android.hardware.Sensor
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import java.io.File
import java.util.Locale

/**
 * Extra sysfs / API probes used to deepen the diagnostic report.
 * Kept separate so DeviceInfoCollector stays focused on orchestration.
 */
internal object DeepProbes {

    fun cpuGovernors(readNode: (String) -> String?): List<String> {
        val found = LinkedHashSet<String>()
        for (i in 0 until 16) {
            for (p in listOf(
                "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor",
                "/sys/devices/system/cpu/cpufreq/policy$i/scaling_governor"
            )) {
                val v = readNode(p)?.trim().orEmpty()
                if (v.isNotEmpty()) found.add(v)
            }
        }
        return found.toList()
    }

    fun availableFreqMhz(readNode: (String) -> String?): List<Int> {
        for (p in listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies",
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies"
        )) {
            val raw = readNode(p)?.trim().orEmpty()
            if (raw.isEmpty()) continue
            val mhz = raw.split(Regex("\\s+"))
                .mapNotNull { it.toLongOrNull()?.div(1000L)?.toInt() }
                .filter { it > 0 }
                .distinct()
                .sorted()
            if (mhz.isNotEmpty()) return mhz
        }
        return emptyList()
    }

    fun onlineCores(readNode: (String) -> String?): List<Int> {
        val online = readNode("/sys/devices/system/cpu/online")?.trim().orEmpty()
        if (online.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        for (part in online.split(',')) {
            val p = part.trim()
            if ('-' in p) {
                val ends = p.split('-')
                val a = ends.getOrNull(0)?.toIntOrNull() ?: continue
                val b = ends.getOrNull(1)?.toIntOrNull() ?: continue
                for (i in a..b) out.add(i)
            } else {
                p.toIntOrNull()?.let { out.add(it) }
            }
        }
        return out
    }

    fun topologyHint(online: List<Int>, freqs: List<Int>, maxF: Int?): String {
        if (online.isEmpty() && freqs.isEmpty()) return ""
        val n = online.size.takeIf { it > 0 } ?: freqs.size
        val distinct = freqs.filter { it > 0 }.distinct().sorted()
        return when {
            distinct.size >= 3 -> "$n cores · multi-cluster (${distinct.joinToString("/") { "${it}MHz" }})"
            distinct.size == 2 -> "$n cores · dual-cluster (${distinct[0]}/${distinct[1]} MHz peak)"
            maxF != null && n > 0 -> "$n cores · up to ${maxF} MHz"
            n > 0 -> "$n cores online"
            else -> ""
        }
    }

    data class MemExtras(
        val cachedMb: Long? = null,
        val buffersMb: Long? = null,
        val swapTotalMb: Long? = null,
        val swapFreeMb: Long? = null,
        val zramMb: Long? = null,
        val activeMb: Long? = null
    )

    fun meminfoExtras(readNode: (String) -> String?): MemExtras {
        fun kbToMb(kb: Long?) = kb?.div(1024)
        var cached: Long? = null
        var buffers: Long? = null
        var swapTotal: Long? = null
        var swapFree: Long? = null
        var active: Long? = null
        try {
            File("/proc/meminfo").forEachLine { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 2) return@forEachLine
                val key = parts[0].trimEnd(':')
                val kb = parts[1].toLongOrNull() ?: return@forEachLine
                when (key) {
                    "Cached" -> cached = kb
                    "Buffers" -> buffers = kb
                    "SwapTotal" -> swapTotal = kb
                    "SwapFree" -> swapFree = kb
                    "Active" -> active = kb
                }
            }
        } catch (_: Exception) {}
        var zram: Long? = null
        for (i in 0 until 4) {
            val raw = readNode("/sys/block/zram$i/disksize")?.trim()?.toLongOrNull()
            if (raw != null && raw > 0) zram = (zram ?: 0L) + raw / (1024 * 1024)
        }
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
        when (readNode("/sys/fs/selinux/enforce")?.trim()) {
            "1" -> return "Enforcing"
            "0" -> return "Permissive"
        }
        val getenforce = try {
            val p = Runtime.getRuntime().exec(arrayOf("getenforce"))
            p.inputStream.bufferedReader().readLine()?.trim().orEmpty().also { p.destroy() }
        } catch (_: Exception) { "" }
        return when {
            getenforce.equals("Enforcing", true) -> "Enforcing"
            getenforce.equals("Permissive", true) -> "Permissive"
            getenforce.equals("Disabled", true) -> "Disabled"
            else -> ""
        }
    }

    fun thermalTypeLabel(type: String): String {
        val t = type.lowercase(Locale.US)
        return when {
            "cpu" in t || "soc" in t -> "CPU / SoC"
            "gpu" in t -> "GPU"
            "battery" in t || "batt" in t -> "Battery"
            "skin" in t || "shell" in t || "surface" in t -> "Skin"
            "pmic" in t -> "PMIC"
            "modem" in t || "pa_" in t || "wifi" in t -> "Radio"
            "charger" in t || "usb" in t -> "Charger"
            "npu" in t || "dsp" in t || "tpu" in t -> "Accelerator"
            type.isBlank() -> ""
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
                chars.physicalCameraIds.joinToString(", ")
            } else ""
        } catch (_: Exception) { "" }
        val caps = try {
            val arr = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            arr.mapNotNull { cap ->
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
            }.joinToString(", ")
        } catch (_: Exception) { "" }
        val active = try {
            val r = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (r != null) "${r.width()}×${r.height()}" else ""
        } catch (_: Exception) { "" }
        val iso = try {
            val range = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            if (range != null) "${range.lower}-${range.upper}" else ""
        } catch (_: Exception) { "" }
        val exposure = try {
            val range = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            if (range != null) {
                val lo = range.lower / 1_000_000.0
                val hi = range.upper / 1_000_000.0
                String.format(Locale.US, "%.3f–%.1f ms", lo, hi)
            } else ""
        } catch (_: Exception) { "" }
        val flash = try {
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } catch (_: Exception) { false }
        return CameraDeep(physical, caps, active, iso, exposure, flash)
    }

    data class CameraDeep(
        val physicalIds: String,
        val capabilities: String,
        val activeArraySize: String,
        val isoRange: String,
        val exposureRange: String,
        val hasFlash: Boolean
    )
}
