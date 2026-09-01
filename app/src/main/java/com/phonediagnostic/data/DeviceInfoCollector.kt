package com.phonediagnostic.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import android.hardware.display.DisplayManager
import android.view.Display
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

class DeviceInfoCollector(private val context: Context) {

    companion object {
        private const val LATENCY_HOST = "8.8.8.8"
        private const val LATENCY_PORT = 53
        private const val LATENCY_TIMEOUT_MS = 3000
        private const val SENSOR_SAMPLE_MS = 250L
        private const val PROPERTY_TIMEOUT_MS = 500L
        private const val LATENCY_GAP_MS = 120L

        /**
         * `ro.*` build properties cannot change while the process lives, and
         * reading one may cost a `getprop` fork. Read each key at most once.
         */
        private val propertyCache = ConcurrentHashMap<String, String>()

        /** GPU strings are fixed for the life of the process; the probe is not cheap. */
        @Volatile
        private var cachedGpu: GpuInfo? = null
    }

    /**
     * @param sampleSensors when true, briefly registers sensor listeners to capture
     *   live readings. That wakes every sampled sensor, so callers pass false unless
     *   the readings are actually on screen.
     */
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
            thermals = collectThermals()
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
            cpu = previous.cpu.copy(currentFreqMhz = readCpuFrequenciesMhz()),
            // Keep the previously captured readings when we are not re-sampling.
            sensors = if (sampleSensors) collectSensors(live = true) else previous.sensors,
            thermals = collectThermals()
        )
    }

    /**
     * Battery + memory only. Used by the background monitor and the load test,
     * which sample often and must not pay for cameras, sensors, GPU or storage.
     */
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

    private fun collectOverview(): DeviceOverview {
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
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH
            } else {
                "N/A"
            },
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

    private fun collectCpu(): CpuInfo {
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
        val freqs = readCpuFrequenciesMhz()
        val (minF, maxF) = readCpuFreqRangeMhz()
        return CpuInfo(
            cores = cores, architecture = arch, supportedAbis = abis,
            hardware = hardware, processor = processor, boardPlatform = boardPlatform,
            currentFreqMhz = freqs, minFreqMhz = minF, maxFreqMhz = maxF
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
        // Fast path: the framework accessor costs no fork. Blocked on some builds,
        // in which case we fall through to getprop.
        runCatching {
            val getter = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
            val value = getter.invoke(null, key) as? String
            if (!value.isNullOrBlank()) return value.trim()
        }

        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val value = BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readLine()?.trim().orEmpty()
            }
            process.waitFor(PROPERTY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            value
        } catch (_: Exception) {
            ""
        } finally {
            // Without this the pipes stay open and repeated collection exhausts
            // the process file-descriptor limit.
            process?.let { p ->
                runCatching { p.outputStream.close() }
                runCatching { p.errorStream.close() }
                p.destroy()
            }
        }
    }

    private fun readCpuFrequenciesMhz(): List<Int> {
        val result = ArrayList<Int>()
        for (i in 0 until 16) {
            val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            if (!f.canRead()) continue
            try {
                val khz = f.readText().trim().toLongOrNull() ?: continue
                result.add((khz / 1000L).toInt())
            } catch (_: Exception) {}
        }
        return result
    }

    private fun readCpuFreqRangeMhz(): Pair<Int?, Int?> {
        var min: Long? = null
        var max: Long? = null
        for (p in listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq",
            "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq"
        )) {
            try {
                val f = File(p)
                if (f.canRead()) { min = f.readText().trim().toLongOrNull(); if (min != null) break }
            } catch (_: Exception) {}
        }
        for (p in listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq",
            "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq"
        )) {
            try {
                val f = File(p)
                if (f.canRead()) { max = f.readText().trim().toLongOrNull(); if (max != null) break }
            } catch (_: Exception) {}
        }
        return Pair(min?.let { (it / 1000L).toInt() }, max?.let { (it / 1000L).toInt() })
    }

    private fun collectGpu(): GpuInfo {
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
                // Unbind before destroying, or the context stays alive on some drivers.
                egl.eglMakeCurrent(
                    display,
                    EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_CONTEXT
                )
                egl.eglDestroyContext(display, ctx)
            }
            egl.eglTerminate(display)
        } catch (_: Exception) {}
        val info = GpuInfo(renderer, vendor, version)
        if (renderer != "Unknown") cachedGpu = info
        return info
    }

    /** Public so the background monitor can sample it without a full collect. */
    fun collectBattery(): BatteryInfo {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
        val status = when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        val health = when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        val temperature = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val technology = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val powerSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }
        val isCharging = status == "Charging" || status == "Full"
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNowMa = bm?.let { normalizeBatteryCurrentMa(it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) }
        val currentAvgMa = bm?.let { normalizeBatteryCurrentMa(it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)) }
        // BATTERY_PROPERTY_CAPACITY reports a percentage, not mAh, so it is not
        // usable as a capacity figure; design capacity comes from build props below.
        val chargeCounter = bm?.let {
            val v = it.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (v == Long.MIN_VALUE || v == 0L) null else v
        }
        // Design capacity from system props when available
        val designMah = listOf(
            readSystemProperty("ro.battery.capacity"),
            readSystemProperty("persist.sys.battery.capacity")
        ).firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { n -> n > 500 } }

        return BatteryInfo(
            level = batteryPct, status = status, health = health, temperature = temperature,
            voltage = voltage, technology = technology, isCharging = isCharging,
            powerSource = powerSource, currentNowMa = currentNowMa, currentAvgMa = currentAvgMa,
            capacityMah = designMah, chargeCounterUah = chargeCounter
        )
    }

    private fun normalizeBatteryCurrentMa(rawUa: Int): Int? {
        if (rawUa == Int.MIN_VALUE || rawUa == 0) return null
        val ma = rawUa / 1000
        return if (ma == 0 && abs(rawUa) < 1000) null else ma
    }

    /** Public so the background monitor can sample it without a full collect. */
    fun collectMemory(): MemoryInfo {
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

    private fun collectStorage(): StorageInfo {
        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val usedBytes = totalBytes - freeBytes
        val totalGb = totalBytes / (1024.0 * 1024 * 1024)
        val freeGb = freeBytes / (1024.0 * 1024 * 1024)
        val usedGb = usedBytes / (1024.0 * 1024 * 1024)
        val percent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0
        val externalState = try { Environment.getExternalStorageState() } catch (_: Exception) { "unknown" }
        return StorageInfo(
            totalInternalGb = totalGb, freeInternalGb = freeGb, usedInternalGb = usedGb,
            usagePercent = percent, volumes = collectVolumes(),
            dataDirectory = dataDir.absolutePath, cacheDirectory = context.cacheDir.absolutePath,
            filesDirectory = context.filesDir.absolutePath, externalStorageState = externalState,
            emulatedExternal = Environment.isExternalStorageEmulated()
        )
    }

    private fun collectVolumes(): List<StorageVolumeInfo> {
        val result = ArrayList<StorageVolumeInfo>()
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            for (volume in sm.storageVolumes) {
                val desc = try { volume.getDescription(context) } catch (_: Exception) {
                    if (volume.isPrimary) "Internal shared storage" else "Storage"
                }
                val state = volume.state ?: "unknown"
                val path = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.directory?.absolutePath ?: ""
                    else @Suppress("DEPRECATION") volume.javaClass.getMethod("getPath").invoke(volume) as? String ?: ""
                } catch (_: Exception) { "" }
                var total = 0L; var free = 0L
                if (path.isNotBlank() && state == Environment.MEDIA_MOUNTED) {
                    try { val s = StatFs(path); total = s.totalBytes; free = s.availableBytes } catch (_: Exception) {}
                }
                result.add(StorageVolumeInfo(
                    name = desc, path = path.ifBlank { "(path unavailable)" },
                    description = buildString {
                        append(if (volume.isPrimary) "Primary" else "Secondary")
                        if (volume.isRemovable) append(" · Removable")
                        if (volume.isEmulated) append(" · Emulated")
                    },
                    totalBytes = total, freeBytes = free, usedBytes = (total - free).coerceAtLeast(0),
                    isRemovable = volume.isRemovable, isPrimary = volume.isPrimary, state = state
                ))
            }
        }
        return result
    }

    /**
     * Reads the panel through [DisplayManager] rather than the deprecated
     * `WindowManager.getDefaultDisplay`. The usual replacement, `WindowMetrics`,
     * needs a visual context, and this collector deliberately holds the
     * application context so the background monitor can share one instance.
     *
     * [Display.Mode.physicalWidth] is also a better answer than the old
     * `getRealMetrics`: it reports the panel's own resolution rather than
     * whatever the current window happens to occupy, which is what a
     * diagnostics readout should show.
     */
    private fun collectDisplay(): DisplayInfo {
        val metrics = context.resources.displayMetrics
        val display = try {
            (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
        } catch (_: Exception) {
            null
        }

        val mode = try { display?.mode } catch (_: Exception) { null }
        // Fall back to the window's metrics when the panel will not describe itself.
        val widthPx = mode?.physicalWidth?.takeIf { it > 0 } ?: metrics.widthPixels
        val heightPx = mode?.physicalHeight?.takeIf { it > 0 } ?: metrics.heightPixels
        val refresh = try { display?.refreshRate ?: 60f } catch (_: Exception) { 60f }

        // xdpi/ydpi are the physical dot pitch, so pairing them with the panel's
        // own pixel count gives the diagonal in inches.
        val xdpi = metrics.xdpi.takeIf { it > 0f } ?: metrics.densityDpi.toFloat()
        val ydpi = metrics.ydpi.takeIf { it > 0f } ?: metrics.densityDpi.toFloat()
        val wIn = widthPx / xdpi.toDouble()
        val hIn = heightPx / ydpi.toDouble()

        return DisplayInfo(
            widthPx = widthPx,
            heightPx = heightPx,
            densityDpi = metrics.densityDpi,
            density = metrics.density,
            refreshRate = refresh,
            screenSizeInches = sqrt(wIn * wIn + hIn * hIn)
        )
    }

    private fun collectThermals(): List<ThermalZone> {
        val zones = ArrayList<ThermalZone>()
        val base = File("/sys/class/thermal")
        if (!base.isDirectory) return zones
        try {
            base.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.sortedBy { it.name }?.forEach { dir ->
                val type = try { File(dir, "type").readText().trim() } catch (_: Exception) { dir.name }
                val tempRaw = try { File(dir, "temp").readText().trim().toLongOrNull() } catch (_: Exception) { null }
                if (tempRaw != null) {
                    // milli-C or deci-C depending on vendor; 0 often means unread/disabled
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

    private fun collectSensors(live: Boolean): List<SensorEntry> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyList()
        val sensors = try { sm.getSensorList(Sensor.TYPE_ALL) } catch (_: Exception) { emptyList() }
        if (sensors.isEmpty()) return emptyList()
        val liveMap = if (live) sampleLiveSensors(sm, sensors) else emptyMap()
        return sensors.map { s ->
            SensorEntry(
                name = s.name ?: "Unknown", type = sensorTypeName(s.type), vendor = s.vendor ?: "",
                powerMa = s.power, resolution = s.resolution, maxRange = s.maximumRange,
                minDelayUs = s.minDelay, liveValues = liveMap[s] ?: ""
            )
        }.sortedWith(compareBy({ it.type }, { it.name }))
    }

    private fun sampleLiveSensors(sm: SensorManager, sensors: List<Sensor>): Map<Sensor, String> {
        val interesting = sensors.filter { s ->
            s.type in setOf(
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY, Sensor.TYPE_PRESSURE,
                Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY,
                Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_HEART_RATE
            )
        }.take(12)
        if (interesting.isEmpty()) return emptyMap()
        val results = mutableMapOf<Sensor, String>()
        val latch = CountDownLatch(interesting.size)
        val listeners = mutableListOf<SensorEventListener>()
        interesting.forEach { sensor ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    val values = event.values?.take(3)
                        ?.joinToString(", ") { String.format(Locale.US, "%.3f", it) } ?: return
                    synchronized(results) {
                        if (!results.containsKey(sensor)) { results[sensor] = values; latch.countDown() }
                    }
                    try { sm.unregisterListener(this) } catch (_: Exception) {}
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            listeners.add(listener)
            try { sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
            catch (_: Exception) { latch.countDown() }
        }
        try { latch.await(SENSOR_SAMPLE_MS, TimeUnit.MILLISECONDS) } catch (_: Exception) {}
        listeners.forEach { try { sm.unregisterListener(it) } catch (_: Exception) {} }
        return results
    }

    /** TYPE_ORIENTATION is deprecated but still reported by some devices, so it is named here. */
    @Suppress("DEPRECATION")
    private fun sensorTypeName(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic field"
        Sensor.TYPE_LIGHT -> "Light"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_PRESSURE -> "Pressure"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient temperature"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
        Sensor.TYPE_STEP_COUNTER -> "Step counter"
        Sensor.TYPE_STEP_DETECTOR -> "Step detector"
        Sensor.TYPE_HEART_RATE -> "Heart rate"
        Sensor.TYPE_ORIENTATION -> "Orientation"
        else -> "Type $type"
    }

    private fun collectCameras(): List<CameraEntry> {
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
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
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

    private fun networkMeta(capabilities: NetworkCapabilities?): Triple<Int?, Int?, Pair<Boolean, Boolean>> {
        if (capabilities == null) return Triple(null, null, false to false)
        val down = capabilities.linkDownstreamBandwidthKbps.takeIf { it > 0 }?.let { it / 1000 }
        val up = capabilities.linkUpstreamBandwidthKbps.takeIf { it > 0 }?.let { it / 1000 }
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return Triple(down, up, validated to metered)
    }

    private fun collectNetworkLight(previous: NetworkInfo, networkProbe: Boolean): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isConnected = capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        )
        val networkType = when {
            capabilities == null -> "None"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unknown"
        }
        val (down, up, flags) = networkMeta(capabilities)
        if (!networkProbe) {
            return previous.copy(
                isConnected = isConnected, networkType = networkType,
                latencyMs = null, latencyStatus = "Disabled in Settings",
                downstreamMbps = down, upstreamMbps = up,
                validated = flags.first, metered = flags.second
            )
        }
        return previous.copy(
            isConnected = isConnected, networkType = networkType,
            downstreamMbps = down, upstreamMbps = up,
            validated = flags.first, metered = flags.second
        )
    }

    private fun collectNetwork(networkProbe: Boolean): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isConnected = capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        )
        val networkType = when {
            capabilities == null -> "None"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unknown"
        }
        val (down, up, flags) = networkMeta(capabilities)
        val target = "$LATENCY_HOST:$LATENCY_PORT"
        if (!networkProbe) {
            return NetworkInfo(isConnected, networkType, null, target, "Disabled in Settings", down, up, flags.first, flags.second)
        }
        if (!isConnected) {
            return NetworkInfo(false, networkType, null, target, "No network", down, up, flags.first, flags.second)
        }
        return measureLatency(networkType, down, up, flags.first, flags.second)
    }

    private fun measureLatency(
        networkType: String,
        down: Int?,
        up: Int?,
        validated: Boolean,
        metered: Boolean
    ): NetworkInfo {
        val target = "$LATENCY_HOST:$LATENCY_PORT"
        return when (val probe = singleLatency()) {
            is LatencyProbe.Ok ->
                NetworkInfo(true, networkType, probe.millis, target, "OK", down, up, validated, metered)
            is LatencyProbe.Failed ->
                NetworkInfo(true, networkType, null, target, probe.reason, down, up, validated, metered)
        }
    }

    private sealed interface LatencyProbe {
        data class Ok(val millis: Long) : LatencyProbe
        data class Failed(val reason: String) : LatencyProbe
    }

    /** One TCP connect to the latency target. The socket is always closed. */
    private fun singleLatency(): LatencyProbe {
        return try {
            // `use` matters here: on a failed connect the socket was previously
            // left open, leaking a descriptor on every probe.
            Socket().use { socket ->
                val startNs = System.nanoTime()
                socket.connect(InetSocketAddress(LATENCY_HOST, LATENCY_PORT), LATENCY_TIMEOUT_MS)
                LatencyProbe.Ok((System.nanoTime() - startNs) / 1_000_000)
            }
        } catch (_: java.net.SocketTimeoutException) {
            LatencyProbe.Failed("Timeout")
        } catch (e: Exception) {
            LatencyProbe.Failed("Error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Repeats the latency probe to expose spread and packet loss, which a single
     * sample cannot show. Blocking — call from a background dispatcher.
     */
    fun measureLatencyStats(count: Int = 5): LatencyStats {
        val attempts = count.coerceIn(1, 20)
        val samples = ArrayList<Long>(attempts)
        var lastError: String? = null
        repeat(attempts) { index ->
            when (val probe = singleLatency()) {
                is LatencyProbe.Ok -> samples.add(probe.millis)
                is LatencyProbe.Failed -> lastError = probe.reason
            }
            if (index < attempts - 1) {
                try {
                    Thread.sleep(LATENCY_GAP_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@repeat
                }
            }
        }
        val lost = attempts - samples.size
        val avg = if (samples.isEmpty()) null else samples.sum() / samples.size
        // Mean absolute deviation from the average — a readable stand-in for jitter.
        val jitter = if (samples.size < 2 || avg == null) {
            null
        } else {
            samples.sumOf { kotlin.math.abs(it - avg) } / samples.size
        }
        return LatencyStats(
            target = "$LATENCY_HOST:$LATENCY_PORT",
            samplesMs = samples,
            attempts = attempts,
            minMs = samples.minOrNull(),
            avgMs = avg,
            maxMs = samples.maxOrNull(),
            jitterMs = jitter,
            lossPercent = if (attempts == 0) 0 else (lost * 100) / attempts,
            lastError = if (samples.isEmpty()) lastError else null
        )
    }

    /**
     * Everything the Network screen shows beyond the summary card. Collected on
     * demand only — none of this belongs in the live tick.
     */
    fun collectNetworkDetail(): NetworkDetail {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = cm?.activeNetwork
        val capabilities = active?.let { cm.getNetworkCapabilities(it) }
        val linkProperties = active?.let { cm.getLinkProperties(it) }

        val dns = linkProperties?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            linkProperties?.privateDnsServerName
        } else {
            null
        }
        val privateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            linkProperties?.isPrivateDnsActive ?: false
        } else {
            false
        }

        return NetworkDetail(
            interfaces = collectInterfaces(),
            dnsServers = dns,
            privateDnsServer = privateDns,
            privateDnsActive = privateDnsActive,
            interfaceName = linkProperties?.interfaceName.orEmpty(),
            domains = linkProperties?.domains.orEmpty(),
            wifi = collectWifiDetail(capabilities),
            cellular = collectCellularDetail(capabilities),
            capabilities = describeCapabilities(capabilities)
        )
    }

    private fun collectInterfaces(): List<NetworkInterfaceInfo> {
        val result = ArrayList<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            for (nif in interfaces) {
                val addresses = ArrayList<String>()
                for (address in nif.inetAddresses) {
                    if (address.isLoopbackAddress) continue
                    val text = address.hostAddress ?: continue
                    // Strip the scope id IPv6 link-local addresses carry.
                    val clean = text.substringBefore('%')
                    val label = when (address) {
                        is Inet4Address -> "IPv4"
                        is Inet6Address -> if (address.isLinkLocalAddress) "IPv6 link-local" else "IPv6"
                        else -> "IP"
                    }
                    addresses.add("$label  $clean")
                }
                if (addresses.isEmpty()) continue
                result.add(
                    NetworkInterfaceInfo(
                        name = nif.name.orEmpty(),
                        displayName = nif.displayName.orEmpty(),
                        addresses = addresses,
                        isUp = try { nif.isUp } catch (_: Exception) { false },
                        mtu = try { nif.mtu } catch (_: Exception) { 0 }
                    )
                )
            }
        } catch (_: Exception) {
            // No interface visibility — return whatever we gathered.
        }
        return result.sortedBy { it.name }
    }

    @Suppress("DEPRECATION")
    private fun collectWifiDetail(capabilities: NetworkCapabilities?): WifiDetail? {
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val info = try { wm.connectionInfo } catch (_: Exception) { null } ?: return null

        // Without a location permission the platform redacts the SSID. We do not
        // ask for location, so say so rather than showing the placeholder.
        val rawSsid = info.ssid.orEmpty().trim('"')
        val ssid = when {
            rawSsid.isBlank() -> "Unavailable"
            rawSsid.equals("<unknown ssid>", ignoreCase = true) ->
                "Hidden (needs location permission)"
            else -> rawSsid
        }

        val frequency = info.frequency.takeIf { it > 0 }
        val band = when {
            frequency == null -> "Unknown"
            frequency < 3000 -> "2.4 GHz"
            frequency < 5900 -> "5 GHz"
            else -> "6 GHz"
        }
        val rssi = info.rssi.takeIf { it != Int.MIN_VALUE && it < 0 }
        return WifiDetail(
            ssid = ssid,
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            rxLinkSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.rxLinkSpeedMbps.takeIf { it > 0 }
            } else {
                null
            },
            txLinkSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.txLinkSpeedMbps.takeIf { it > 0 }
            } else {
                null
            },
            rssiDbm = rssi,
            signalLevel = rssi?.let { WifiManager.calculateSignalLevel(it, 5) } ?: 0,
            frequencyMhz = frequency,
            band = band
        )
    }

    private fun collectCellularDetail(capabilities: NetworkCapabilities?): CellularDetail? {
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) != true) return null
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        return try {
            CellularDetail(
                carrier = tm.networkOperatorName.orEmpty().ifBlank { "Unknown" },
                simOperator = tm.simOperatorName.orEmpty(),
                countryIso = tm.networkCountryIso.orEmpty().uppercase(Locale.US),
                phoneType = when (tm.phoneType) {
                    TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                    TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                    TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                    else -> "None"
                },
                roaming = try { tm.isNetworkRoaming } catch (_: Exception) { false }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun describeCapabilities(capabilities: NetworkCapabilities?): List<String> {
        if (capabilities == null) return emptyList()
        val flags = listOf(
            "Internet" to NetworkCapabilities.NET_CAPABILITY_INTERNET,
            "Validated" to NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            "Not metered" to NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
            "Not restricted" to NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED,
            "Not VPN" to NetworkCapabilities.NET_CAPABILITY_NOT_VPN,
            "Captive portal" to NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
        )
        return flags.filter { (_, flag) ->
            try { capabilities.hasCapability(flag) } catch (_: Exception) { false }
        }.map { it.first }
    }
}
