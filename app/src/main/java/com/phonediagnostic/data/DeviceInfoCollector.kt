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
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.abs

class DeviceInfoCollector(private val context: Context) {

    companion object {
        private const val LATENCY_HOST = "8.8.8.8"
        private const val LATENCY_PORT = 53
        private const val LATENCY_TIMEOUT_MS = 3000
        private const val SENSOR_SAMPLE_MS = 250L
    }

    fun collect(networkProbe: Boolean = true): FullDeviceReport {
        return FullDeviceReport(
            overview = collectOverview(),
            cpu = collectCpu(),
            gpu = collectGpu(),
            battery = collectBattery(),
            memory = collectMemory(),
            storage = collectStorage(),
            display = collectDisplay(),
            network = collectNetwork(networkProbe),
            sensors = collectSensors(live = true),
            cameras = collectCameras()
        )
    }

    fun collectLive(previous: FullDeviceReport, networkProbe: Boolean = true): FullDeviceReport {
        return previous.copy(
            overview = collectOverview(),
            battery = collectBattery(),
            memory = collectMemory(),
            network = collectNetworkLight(previous.network, networkProbe),
            cpu = previous.cpu.copy(
                currentFreqMhz = readCpuFrequenciesMhz()
            ),
            sensors = collectSensors(live = true)
        )
    }

    private fun collectOverview(): DeviceOverview {
        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        val uptimeStr = "${hours}h ${minutes}m"

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
            type = Build.TYPE.orEmpty()
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
            cores = cores,
            architecture = arch,
            supportedAbis = abis,
            hardware = hardware,
            processor = processor,
            boardPlatform = boardPlatform,
            currentFreqMhz = freqs,
            minFreqMhz = minF,
            maxFreqMhz = maxF
        )
    }

    private fun resolveProcessorName(hardware: String, boardPlatform: String): String {
        val cpuinfo = try {
            File("/proc/cpuinfo").readLines()
        } catch (_: Exception) {
            emptyList()
        }

        fun valueFor(prefix: String): String? {
            val line = cpuinfo.firstOrNull { it.startsWith(prefix) } ?: return null
            return line.substringAfter(":").trim().takeIf { it.isNotBlank() }
        }

        val fromCpuinfo = listOfNotNull(
            valueFor("model name"),
            valueFor("Model Name"),
            valueFor("Hardware"),
            valueFor("Processor")
        ).firstOrNull { candidate ->
            candidate.isNotBlank() &&
                candidate != "0" &&
                !candidate.matches(Regex("^\\d+$"))
        }

        val fromProps = listOf(
            readSystemProperty("ro.soc.model"),
            readSystemProperty("ro.chipname"),
            boardPlatform,
            hardware
        ).firstOrNull { p ->
            p.isNotBlank() &&
                !p.equals("qcom", ignoreCase = true) &&
                !p.equals("unknown", ignoreCase = true) &&
                p != "0"
        }

        return fromCpuinfo ?: fromProps ?: hardware.ifBlank { boardPlatform }.ifBlank { "Unknown" }
    }

    private fun readSystemProperty(key: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLine()?.trim().orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** Best-effort: read scaling_cur_freq from cpuN cpufreq nodes (kHz → MHz). */
    private fun readCpuFrequenciesMhz(): List<Int> {
        val result = ArrayList<Int>()
        for (i in 0 until 16) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
            val f = File(path)
            if (!f.canRead()) continue
            try {
                val khz = f.readText().trim().toLongOrNull() ?: continue
                result.add((khz / 1000L).toInt())
            } catch (_: Exception) {
                // skip
            }
        }
        return result
    }

    private fun readCpuFreqRangeMhz(): Pair<Int?, Int?> {
        var min: Long? = null
        var max: Long? = null
        val candidates = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq",
            "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq"
        )
        val maxCandidates = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq",
            "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq"
        )
        for (p in candidates) {
            try {
                val f = File(p)
                if (f.canRead()) {
                    min = f.readText().trim().toLongOrNull()
                    if (min != null) break
                }
            } catch (_: Exception) {}
        }
        for (p in maxCandidates) {
            try {
                val f = File(p)
                if (f.canRead()) {
                    max = f.readText().trim().toLongOrNull()
                    if (max != null) break
                }
            } catch (_: Exception) {}
        }
        return Pair(
            min?.let { (it / 1000L).toInt() },
            max?.let { (it / 1000L).toInt() }
        )
    }

    private fun collectGpu(): GpuInfo {
        var renderer = "Unknown"
        var vendor = "Unknown"
        var version = "Unknown"

        try {
            val egl = EGLContext.getEGL() as EGL10
            val display: EGLDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            val versionArray = IntArray(2)
            egl.eglInitialize(display, versionArray)

            val configSpec = intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            egl.eglChooseConfig(display, configSpec, configs, 1, numConfig)

            if (numConfig[0] > 0) {
                val config = configs[0]
                val ctx = egl.eglCreateContext(
                    display,
                    config,
                    EGL10.EGL_NO_CONTEXT,
                    intArrayOf(0x3098, 2, EGL10.EGL_NONE)
                )

                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, ctx)

                renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
                vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
                version = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"

                egl.eglDestroyContext(display, ctx)
            }
            egl.eglTerminate(display)
        } catch (_: Exception) {
            // Keep defaults
        }

        return GpuInfo(
            renderer = renderer,
            vendor = vendor,
            version = version
        )
    }

    private fun collectBattery(): BatteryInfo {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }

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

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNowMa = batteryManager?.let { bm ->
            normalizeBatteryCurrentMa(
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            )
        }
        val currentAvgMa = batteryManager?.let { bm ->
            normalizeBatteryCurrentMa(
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            )
        }

        return BatteryInfo(
            level = batteryPct,
            status = status,
            health = health,
            temperature = temperature,
            voltage = voltage,
            technology = technology,
            isCharging = isCharging,
            powerSource = powerSource,
            currentNowMa = currentNowMa,
            currentAvgMa = currentAvgMa
        )
    }

    private fun normalizeBatteryCurrentMa(rawUa: Int): Int? {
        if (rawUa == Int.MIN_VALUE || rawUa == 0) return null
        val ma = rawUa / 1000
        return if (ma == 0 && abs(rawUa) < 1000) null else ma
    }

    private fun collectMemory(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

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

        return MemoryInfo(
            totalRamMb = totalMb,
            availableRamMb = availableMb,
            usedRamMb = usedMb,
            usagePercent = percent,
            thresholdMb = thresholdMb,
            isLowMemory = low,
            statusHint = hint
        )
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

        val volumes = collectVolumes()
        val externalState = try {
            Environment.getExternalStorageState()
        } catch (_: Exception) {
            "unknown"
        }

        return StorageInfo(
            totalInternalGb = totalGb,
            freeInternalGb = freeGb,
            usedInternalGb = usedGb,
            usagePercent = percent,
            volumes = volumes,
            dataDirectory = dataDir.absolutePath,
            cacheDirectory = context.cacheDir.absolutePath,
            filesDirectory = context.filesDir.absolutePath,
            externalStorageState = externalState,
            emulatedExternal = Environment.isExternalStorageEmulated()
        )
    }

    private fun collectVolumes(): List<StorageVolumeInfo> {
        val result = ArrayList<StorageVolumeInfo>()
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            for (volume in sm.storageVolumes) {
                val desc = try {
                    volume.getDescription(context)
                } catch (_: Exception) {
                    if (volume.isPrimary) "Internal shared storage" else "Storage"
                }
                val state = volume.state ?: "unknown"
                val path = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory?.absolutePath ?: ""
                    } else {
                        @Suppress("DEPRECATION")
                        volume.javaClass.getMethod("getPath").invoke(volume) as? String ?: ""
                    }
                } catch (_: Exception) {
                    ""
                }

                var total = 0L
                var free = 0L
                if (path.isNotBlank() && state == Environment.MEDIA_MOUNTED) {
                    try {
                        val s = StatFs(path)
                        total = s.totalBytes
                        free = s.availableBytes
                    } catch (_: Exception) {
                        // unreadable
                    }
                }

                result.add(
                    StorageVolumeInfo(
                        name = desc,
                        path = path.ifBlank { "(path unavailable)" },
                        description = buildString {
                            append(if (volume.isPrimary) "Primary" else "Secondary")
                            if (volume.isRemovable) append(" · Removable")
                            if (volume.isEmulated) append(" · Emulated")
                        },
                        totalBytes = total,
                        freeBytes = free,
                        usedBytes = (total - free).coerceAtLeast(0),
                        isRemovable = volume.isRemovable,
                        isPrimary = volume.isPrimary,
                        state = state
                    )
                )
            }
        }

        val data = Environment.getDataDirectory()
        try {
            val s = StatFs(data.path)
            val dataTotal = s.totalBytes
            val dataFree = s.availableBytes
            val alreadySamePool = result.any { vol ->
                vol.totalBytes > 0 &&
                    vol.totalBytes == dataTotal &&
                    vol.freeBytes == dataFree
            }
            val pathAlreadyListed = result.any {
                it.path == data.absolutePath || it.path.startsWith("/data")
            }
            if (!alreadySamePool && !pathAlreadyListed) {
                result.add(
                    0,
                    StorageVolumeInfo(
                        name = "Internal data",
                        path = data.absolutePath,
                        description = "App / system data partition",
                        totalBytes = dataTotal,
                        freeBytes = dataFree,
                        usedBytes = dataTotal - dataFree,
                        isRemovable = false,
                        isPrimary = true,
                        state = Environment.MEDIA_MOUNTED
                    )
                )
            }
        } catch (_: Exception) {
            // ignore
        }

        return dedupeVolumes(result)
    }

    private fun dedupeVolumes(volumes: List<StorageVolumeInfo>): List<StorageVolumeInfo> {
        if (volumes.size <= 1) return volumes
        val seen = LinkedHashMap<String, StorageVolumeInfo>()
        for (vol in volumes) {
            val key = if (vol.totalBytes > 0) {
                "${vol.totalBytes}_${vol.freeBytes}"
            } else {
                vol.path
            }
            val existing = seen[key]
            if (existing == null) {
                seen[key] = vol
            } else {
                val preferNew =
                    (vol.isPrimary && !existing.isPrimary) ||
                        (existing.name.contains("data", ignoreCase = true) &&
                            !vol.name.contains("data", ignoreCase = true))
                if (preferNew) {
                    seen[key] = vol.copy(
                        description = existing.description + " · also ${existing.path}"
                    )
                } else if (!existing.description.contains(vol.path)) {
                    seen[key] = existing.copy(
                        description = existing.description + " · also ${vol.path}"
                    )
                }
            }
        }
        return seen.values.toList()
    }

    private fun collectDisplay(): DisplayInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val densityDpi = metrics.densityDpi
        val density = metrics.density

        val refreshRate = try {
            windowManager.defaultDisplay.refreshRate
        } catch (_: Exception) {
            60f
        }

        val widthInches = width / metrics.xdpi.toDouble()
        val heightInches = height / metrics.ydpi.toDouble()
        val diagonal = Math.sqrt(widthInches * widthInches + heightInches * heightInches)

        return DisplayInfo(
            widthPx = width,
            heightPx = height,
            densityDpi = densityDpi,
            density = density,
            refreshRate = refreshRate,
            screenSizeInches = diagonal
        )
    }

    private fun collectSensors(live: Boolean): List<SensorEntry> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()

        val sensors = try {
            sm.getSensorList(Sensor.TYPE_ALL)
        } catch (_: Exception) {
            emptyList()
        }

        if (sensors.isEmpty()) return emptyList()

        val liveMap = if (live) sampleLiveSensors(sm, sensors) else emptyMap()

        return sensors.map { s ->
            SensorEntry(
                name = s.name ?: "Unknown",
                type = sensorTypeName(s.type),
                vendor = s.vendor ?: "",
                powerMa = s.power,
                resolution = s.resolution,
                maxRange = s.maximumRange,
                minDelayUs = s.minDelay,
                liveValues = liveMap[s] ?: ""
            )
        }.sortedWith(compareBy({ it.type }, { it.name }))
    }

    private fun sampleLiveSensors(
        sm: SensorManager,
        sensors: List<Sensor>
    ): Map<Sensor, String> {
        // Prefer common interactive sensors; sampling all can be heavy
        val interesting = sensors.filter { s ->
            when (s.type) {
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_LIGHT,
                Sensor.TYPE_PROXIMITY,
                Sensor.TYPE_PRESSURE,
                Sensor.TYPE_AMBIENT_TEMPERATURE,
                Sensor.TYPE_RELATIVE_HUMIDITY,
                Sensor.TYPE_GRAVITY,
                Sensor.TYPE_LINEAR_ACCELERATION,
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_STEP_COUNTER,
                Sensor.TYPE_HEART_RATE -> true
                else -> false
            }
        }.take(12)

        if (interesting.isEmpty()) return emptyMap()

        val results = mutableMapOf<Sensor, String>()
        val latch = CountDownLatch(interesting.size)
        val listeners = mutableListOf<SensorEventListener>()

        interesting.forEach { sensor ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    val values = event.values?.take(3)?.joinToString(", ") {
                        String.format("%.3f", it)
                    } ?: return
                    synchronized(results) {
                        if (!results.containsKey(sensor)) {
                            results[sensor] = values
                            latch.countDown()
                        }
                    }
                    try {
                        sm.unregisterListener(this)
                    } catch (_: Exception) {}
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            listeners.add(listener)
            try {
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            } catch (_: Exception) {
                latch.countDown()
            }
        }

        try {
            latch.await(SENSOR_SAMPLE_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}

        listeners.forEach { l ->
            try {
                sm.unregisterListener(l)
            } catch (_: Exception) {}
        }

        return results
    }

    private fun sensorTypeName(type: Int): String {
        return when (type) {
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
    }

    private fun collectCameras(): List<CameraEntry> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return emptyList()

        val ids = try {
            cm.cameraIdList
        } catch (_: Exception) {
            return emptyList()
        }

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
                    ?.joinToString(", ") { String.format("%.2f mm", it) } ?: "—"
                val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    ?.joinToString(", ") { String.format("f/%.1f", it) } ?: "—"

                result.add(
                    CameraEntry(
                        id = id,
                        facing = facing,
                        sensorOrientation = orientation,
                        hardwareLevel = level,
                        pixelArraySize = pixelArray,
                        focalLengths = focals,
                        aperture = apertures
                    )
                )
            } catch (_: Exception) {
                // skip inaccessible cameras
            }
        }
        return result
    }

    private fun collectNetworkLight(previous: NetworkInfo, networkProbe: Boolean): NetworkInfo {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

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

        if (!networkProbe) {
            return previous.copy(
                isConnected = isConnected,
                networkType = networkType,
                latencyMs = null,
                latencyStatus = "Disabled in Settings"
            )
        }

        return previous.copy(
            isConnected = isConnected,
            networkType = networkType
        )
    }

    private fun collectNetwork(networkProbe: Boolean): NetworkInfo {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

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

        val target = "$LATENCY_HOST:$LATENCY_PORT"

        if (!networkProbe) {
            return NetworkInfo(
                isConnected = isConnected,
                networkType = networkType,
                latencyMs = null,
                latencyTarget = target,
                latencyStatus = "Disabled in Settings"
            )
        }

        if (!isConnected) {
            return NetworkInfo(
                isConnected = false,
                networkType = networkType,
                latencyMs = null,
                latencyTarget = target,
                latencyStatus = "No network"
            )
        }

        return measureLatency(networkType)
    }

    private fun measureLatency(networkType: String): NetworkInfo {
        val target = "$LATENCY_HOST:$LATENCY_PORT"
        return try {
            val socket = Socket()
            val startNs = System.nanoTime()
            socket.connect(InetSocketAddress(LATENCY_HOST, LATENCY_PORT), LATENCY_TIMEOUT_MS)
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            socket.close()

            NetworkInfo(
                isConnected = true,
                networkType = networkType,
                latencyMs = elapsedMs,
                latencyTarget = target,
                latencyStatus = "OK"
            )
        } catch (_: java.net.SocketTimeoutException) {
            NetworkInfo(
                isConnected = true,
                networkType = networkType,
                latencyMs = null,
                latencyTarget = target,
                latencyStatus = "Timeout"
            )
        } catch (e: Exception) {
            NetworkInfo(
                isConnected = true,
                networkType = networkType,
                latencyMs = null,
                latencyTarget = target,
                latencyStatus = "Error: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }
}
