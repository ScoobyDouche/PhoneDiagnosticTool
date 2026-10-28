package com.phonediagnostic.data

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.nfc.NfcAdapter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.hardware.display.DisplayManager
import android.view.Display
import com.phonediagnostic.data.elevated.AccessTier
import com.phonediagnostic.data.elevated.ElevatedShell
import java.util.Locale
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.abs
import kotlin.math.sqrt

/** Core collector implementation; DeviceInfoCollector delegates here and layers DeepProbes. */
internal open class DeviceInfoCollectorBase(protected val context: Context) {

    @Volatile
    open var elevated: ElevatedShell? = null

    private data class NodeRead(val value: String?, val source: AccessTier?)

    internal fun readNodeSourced(path: String): NodeRead {
        val f = File(path)
        if (runCatching { f.canRead() }.getOrDefault(false)) {
            val direct = runCatching { f.readText().trim() }.getOrNull()
            if (!direct.isNullOrBlank()) return NodeRead(direct, null)
        }
        val e = elevated
        if (e != null) {
            val v = e.readFileOrNull(path)
            if (!v.isNullOrBlank()) return NodeRead(v, e.tier)
        }
        return NodeRead(null, null)
    }

    internal fun readNode(path: String): String? = readNodeSourced(path).value

    companion object {
        private const val LATENCY_HOST = "8.8.8.8"
        private val BATTERY_SUPPLY_DIRS = listOf(
            "/sys/class/power_supply/battery",
            "/sys/class/power_supply/bms",
            "/sys/class/power_supply/max170xx_battery",
            "/sys/class/power_supply/bat"
        )
        private const val LATENCY_PORT = 53
        private const val LATENCY_TIMEOUT_MS = 3000
        private const val SENSOR_SAMPLE_MS = 250L
        private const val PROPERTY_TIMEOUT_MS = 500L
        private const val LATENCY_GAP_MS = 120L
        private val propertyCache = ConcurrentHashMap<String, String>()
        @Volatile
        private var cachedGpu: GpuInfo? = null
    }

    fun collect(networkProbe: Boolean = true, sampleSensors: Boolean = false): FullDeviceReport {
        return FullDeviceReport(
            overview = collectOverview(),
            cpu = collectCpu(),
            gpu = collectGpu(),
            battery = collectBattery(),
            memory = collectMemory(),
            storage = collectStorage(),
            display = collectDisplay(),
            network = collectNetwork(networkProbe),
            sensors = collectSensors(live = sampleSensors),
            cameras = collectCameras(),
            thermals = collectThermals(),
            nfc = collectNfc(),
            bluetooth = collectBluetooth(),
            audio = collectAudio(),
            security = collectSecurity()
        )
    }

    fun collectLive(
        previous: FullDeviceReport,
        networkProbe: Boolean = true,
        sampleSensors: Boolean = false
    ): FullDeviceReport {
        return previous.copy(
            overview = collectOverview(),
            battery = collectBattery(),
            memory = collectMemory(),
            network = collectNetworkLight(previous.network, networkProbe),
            cpu = readCpuFrequenciesMhz().let { (freqs, source) ->
                previous.cpu.copy(
                    currentFreqMhz = freqs,
                    clockSource = source?.name ?: previous.cpu.clockSource
                )
            },
            sensors = if (sampleSensors) collectSensors(live = true) else previous.sensors,
            thermals = collectThermals(),
            nfc = collectNfc(),
            bluetooth = collectBluetooth(),
            audio = collectAudio()
        )
    }

    fun collectSample(): MetricSample {
        val battery = collectBattery()
        val memory = collectMemory()
        return MetricSample(
            timestampMs = System.currentTimeMillis(),
            batteryPct = battery.level,
            batteryTempC = battery.temperature,
            ramUsedMb = memory.usedRamMb,
            ramTotalMb = memory.totalRamMb,
            charging = battery.isCharging
        )
    }

    internal fun collectOverview(): DeviceOverview {
        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        val uptimeStr = "${hours}h ${minutes}m"
        val kernel = try {
            File("/proc/version").readText().trim().take(120)
        } catch (_: Exception) {
            System.getProperty("os.version").orEmpty()
        }
        return DeviceOverview(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            device = Build.DEVICE,
            product = Build.PRODUCT,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            buildId = Build.ID,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            uptime = uptimeStr,
            fingerprint = Build.FINGERPRINT.orEmpty(),
            board = Build.BOARD.orEmpty(),
            bootloader = Build.BOOTLOADER.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            host = Build.HOST.orEmpty(),
            tags = Build.TAGS.orEmpty(),
            type = Build.TYPE.orEmpty(),
            kernelVersion = kernel,
            radioVersion = try { Build.getRadioVersion().orEmpty() } catch (_: Exception) { "" }
        )
    }

    internal open fun collectCpu(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val abis = Build.SUPPORTED_ABIS.toList()
        val arch = when {
            abis.any { it.contains("arm64") } -> "ARM64"
            abis.any { it.contains("armeabi") } -> "ARM"
            abis.any { it.contains("x86_64") } -> "x86_64"
            abis.any { it.contains("x86") } -> "x86"
            else -> "Unknown"
        }
        val hardware = Build.HARDWARE.ifBlank { "Unknown" }
        val boardPlatform = readSystemProperty("ro.board.platform")
            .ifBlank { readSystemProperty("ro.mediatek.platform") }
            .ifBlank { readSystemProperty("ro.soc.model") }
            .ifBlank { readSystemProperty("ro.product.board") }
            .ifBlank { hardware }
        val processor = resolveProcessorName(hardware, boardPlatform)
        val (freqs, freqSource) = readCpuFrequenciesMhz()
        val (minF, maxF, rangeSource) = readCpuFreqRangeMhz()
        return CpuInfo(
            cores = cores, architecture = arch, supportedAbis = abis,
            hardware = hardware, processor = processor, boardPlatform = boardPlatform,
            currentFreqMhz = freqs, minFreqMhz = minF, maxFreqMhz = maxF,
            clockSource = (freqSource ?: rangeSource)?.name
        )
    }

    private fun resolveProcessorName(hardware: String, boardPlatform: String): String {
        val cpuinfo = try { File("/proc/cpuinfo").readLines() } catch (_: Exception) { emptyList() }
        fun valueFor(prefix: String): String? {
            val line = cpuinfo.firstOrNull { it.startsWith(prefix) } ?: return null
            return line.substringAfter(":").trim().takeIf { it.isNotBlank() }
        }
        val fromCpuinfo = listOfNotNull(
            valueFor("model name"), valueFor("Model Name"), valueFor("Hardware"), valueFor("Processor")
        ).firstOrNull { c -> c.isNotBlank() && c != "0" && !c.matches(Regex("^\\d+$")) }
        val fromProps = listOf(
            readSystemProperty("ro.soc.model"), readSystemProperty("ro.chipname"), boardPlatform, hardware
        ).firstOrNull { p ->
            p.isNotBlank() && !p.equals("qcom", true) && !p.equals("unknown", true) && p != "0"
        }
        return fromCpuinfo ?: fromProps ?: hardware.ifBlank { boardPlatform }.ifBlank { "Unknown" }
    }

    private fun readSystemProperty(key: String): String =
        propertyCache.getOrPut(key) { readSystemPropertyUncached(key) }

    private fun readSystemPropertyUncached(key: String): String {
        runCatching {
            val getter = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
            val value = getter.invoke(null, key) as? String
            if (!value.isNullOrBlank()) return value.trim()
        }
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val value = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine()?.trim().orEmpty() }
            process.waitFor(PROPERTY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            value
        } catch (_: Exception) {
            ""
        } finally {
            process?.let { p ->
                runCatching { p.outputStream.close() }
                runCatching { p.errorStream.close() }
                p.destroy()
            }
        }
    }

    internal fun readCpuFrequenciesMhz(): Pair<List<Int>, AccessTier?> {
        val result = ArrayList<Int>()
        var source: AccessTier? = null
        for (i in 0 until 16) {
            val r = readNodeSourced("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val khz = r.value?.toLongOrNull() ?: continue
            if (r.source != null) source = r.source
            result.add((khz / 1000L).toInt())
        }
        return result to source
    }

    private fun readCpuFreqRangeMhz(): Triple<Int?, Int?, AccessTier?> {
        var min: Long? = null
        var max: Long? = null
        var source: AccessTier? = null
        for (p in listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq",
            "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq"
        )) {
            val r = readNodeSourced(p); min = r.value?.toLongOrNull()
            if (min != null) { if (r.source != null) source = r.source; break }
        }
        for (p in listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq",
            "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq"
        )) {
            val r = readNodeSourced(p); max = r.value?.toLongOrNull()
            if (max != null) { if (r.source != null) source = r.source; break }
        }
        return Triple(min?.let { (it / 1000L).toInt() }, max?.let { (it / 1000L).toInt() }, source)
    }

    internal fun collectGpu(): GpuInfo {
        cachedGpu?.let { return it }
        var renderer = "Unknown"; var vendor = "Unknown"; var version = "Unknown"
        try {
            val egl = EGLContext.getEGL() as EGL10
            val display: EGLDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            egl.eglInitialize(display, IntArray(2))
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            egl.eglChooseConfig(display, intArrayOf(EGL10.EGL_RENDERABLE_TYPE, 4, EGL10.EGL_NONE), configs, 1, numConfig)
            if (numConfig[0] > 0) {
                val ctx = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, intArrayOf(0x3098, 2, EGL10.EGL_NONE))
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, ctx)
                renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
                vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
                version = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                egl.eglDestroyContext(display, ctx)
            }
            egl.eglTerminate(display)
        } catch (_: Exception) {}
        val info = GpuInfo(renderer, vendor, version)
        if (renderer != "Unknown") cachedGpu = info
        return info
    }

    open fun collectMemory(): MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availableMb = memInfo.availMem / (1024 * 1024)
        val usedMb = (totalMb - availableMb).coerceAtLeast(0)
        val percent = if (totalMb > 0) ((usedMb * 100) / totalMb).toInt() else 0
        val thresholdMb = memInfo.threshold / (1024 * 1024)
        val low = memInfo.lowMemory
        val hint = when {
            low -> "Under memory pressure — system is freeing caches"
            availableMb <= thresholdMb * 2 -> "Available is near the system threshold"
            percent >= 75 -> "Normal when idle — Android keeps apps cached for speed"
            else -> "Healthy headroom"
        }
        return MemoryInfo(totalMb, availableMb, usedMb, percent, thresholdMb, low, hint)
    }

    internal open fun collectSecurity(): SecurityInfo {
        val locked = when (readSystemProperty("ro.boot.flash.locked")) {
            "1" -> true
            "0" -> false
            else -> when (readSystemProperty("ro.boot.vbmeta.device_state")) {
                "locked" -> true
                "unlocked" -> false
                else -> null
            }
        }
        val encryption = try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            when (dpm?.storageEncryptionStatus) {
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> "Encrypted (file-based)"
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> "Encrypted"
                DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> "Not encrypted"
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> "Unsupported"
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
        return SecurityInfo(
            verifiedBootState = readSystemProperty("ro.boot.verifiedbootstate"),
            bootloaderLocked = locked,
            encryption = encryption
        )
    }

    internal open fun collectThermals(): List<ThermalZone> {
        val zones = ArrayList<ThermalZone>()
        val base = File("/sys/class/thermal")
        if (!base.isDirectory) return zones
        try {
            base.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.sortedBy { it.name }?.forEach { dir ->
                val type = try { File(dir, "type").readText().trim() } catch (_: Exception) { dir.name }
                val tempRaw = try { File(dir, "temp").readText().trim().toLongOrNull() } catch (_: Exception) { null }
                if (tempRaw != null) {
                    val tempC = when {
                        tempRaw == 0L -> null
                        tempRaw > 1000 -> tempRaw / 1000f
                        tempRaw > 200 -> tempRaw / 10f
                        else -> tempRaw.toFloat()
                    }
                    if (tempC != null && tempC in -40f..150f) {
                        zones.add(ThermalZone(name = dir.name, tempC = tempC, type = type))
                    }
                }
            }
        } catch (_: Exception) {}
        return zones.sortedByDescending { it.tempC }
    }

    internal open fun collectSensors(live: Boolean): List<SensorEntry> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyList()
        val sensors = try { sm.getSensorList(Sensor.TYPE_ALL) } catch (_: Exception) { emptyList() }
        if (sensors.isEmpty()) return emptyList()
        return sensors.map { s ->
            SensorEntry(
                name = s.name ?: "Unknown", type = sensorTypeName(s.type), vendor = s.vendor ?: "",
                powerMa = s.power, resolution = s.resolution, maxRange = s.maximumRange,
                minDelayUs = s.minDelay, liveValues = ""
            )
        }.sortedWith(compareBy({ it.type }, { it.name }))
    }

    private fun sensorTypeName(type: Int): String = try {
        when (type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic field"
            Sensor.TYPE_LIGHT -> "Light"
            Sensor.TYPE_PROXIMITY -> "Proximity"
            Sensor.TYPE_PRESSURE -> "Pressure"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient temperature"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
            Sensor.TYPE_STEP_COUNTER -> "Step counter"
            Sensor.TYPE_STEP_DETECTOR -> "Step detector"
            else -> "Type $type"
        }
    } catch (_: Exception) { "Type $type" }

    internal open fun collectCameras(): List<CameraEntry> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        val ids = try { cm.cameraIdList } catch (_: Exception) { return emptyList() }
        val result = ArrayList<CameraEntry>()
        for (id in ids) {
            try {
                val chars = cm.getCameraCharacteristics(id)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }
                val orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                val level = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                    else -> "Unknown"
                }
                val size = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val pixelArray = if (size != null) "${size.width}×${size.height}" else "—"
                val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString(", ") { String.format(Locale.US, "%.2f mm", it) } ?: "—"
                val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    ?.joinToString(", ") { String.format(Locale.US, "f/%.1f", it) } ?: "—"
                result.add(CameraEntry(id, facing, orientation, level, pixelArray, focals, apertures))
            } catch (_: Exception) {}
        }
        return result
    }

    // Stubs for remaining collectors — full implementations can be restored from main later
    fun collectBattery(): BatteryInfo = BatteryInfo(
        level = -1, status = "Unknown", health = "Unknown", temperature = 0f,
        voltage = 0, technology = "Unknown", isCharging = false, powerSource = "Unknown",
        currentNowMa = null, currentAvgMa = null
    )

    internal fun collectStorage(): StorageInfo = StorageInfo(volumes = emptyList())
    internal fun collectDisplay(): DisplayInfo = DisplayInfo(
        widthPx = 0, heightPx = 0, densityDpi = 0, refreshRateHz = 0f, hdrTypes = emptyList()
    )
    internal fun collectNetwork(probe: Boolean): NetworkInfo = NetworkInfo()
    internal fun collectNetworkLight(prev: NetworkInfo, probe: Boolean): NetworkInfo = prev
    internal fun collectNfc(): NfcInfo = NfcInfo()
    internal fun collectBluetooth(): BluetoothInfo = BluetoothInfo()
    internal fun collectAudio(): AudioInfo = AudioInfo()
}
