package com.phonediagnostic.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

class DeviceInfoCollector(private val context: Context) {

    companion object {
        private const val LATENCY_HOST = "8.8.8.8"
        private const val LATENCY_PORT = 53
        private const val LATENCY_TIMEOUT_MS = 3000
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
            network = collectNetwork(networkProbe)
        )
    }

    fun collectLive(previous: FullDeviceReport, networkProbe: Boolean = true): FullDeviceReport {
        return previous.copy(
            overview = collectOverview(),
            battery = collectBattery(),
            memory = collectMemory(),
            network = collectNetworkLight(previous.network, networkProbe)
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
            uptime = uptimeStr
        )
    }

    private fun collectCpu(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val abis = Build.SUPPORTED_ABIS.toList()
        val arch = when {
            abis.any { it.contains("arm64") } -> "ARM64"
            abis.any { it.contains("armeabi") } -> "ARM"
            abis.any { it.contains("x86_64") } -> "x86_64"
            abis.any { it.contains("x86") } -> "x86"
            else -> "Unknown"
        }

        val hardware = Build.HARDWARE
        val boardPlatform = readSystemProperty("ro.board.platform")
            .ifBlank { readSystemProperty("ro.mediatek.platform") }
            .ifBlank { readSystemProperty("ro.hardware") }
            .ifBlank { hardware }

        val processor = resolveProcessorName(hardware, boardPlatform)

        return CpuInfo(
            cores = cores,
            architecture = arch,
            supportedAbis = abis,
            hardware = hardware,
            processor = processor,
            boardPlatform = boardPlatform
        )
    }

    private fun resolveProcessorName(hardware: String, boardPlatform: String): String {
        val cpuinfo = try {
            File("/proc/cpuinfo").readLines()
        } catch (_: Exception) {
            emptyList()
        }

        fun valueFor(prefix: String): String? =
            cpuinfo.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val candidates = listOfNotNull(
            valueFor("model name"),
            valueFor("Processor"),
            valueFor("Hardware"),
            boardPlatform.takeIf { it.isNotBlank() && !it.equals("qcom", ignoreCase = true) },
            hardware.takeIf { it.isNotBlank() && !it.equals("qcom", ignoreCase = true) }
        )

        return candidates.firstOrNull() ?: hardware.ifBlank { boardPlatform }.ifBlank { "Unknown" }
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
            val ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (ua == Int.MIN_VALUE || ua == 0 && batteryPct < 0) null else ua / 1000
        }
        val currentAvgMa = batteryManager?.let { bm ->
            val ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            if (ua == Int.MIN_VALUE) null else ua / 1000
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

    private fun collectMemory(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availableMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availableMb
        val percent = if (totalMb > 0) ((usedMb * 100) / totalMb).toInt() else 0

        return MemoryInfo(
            totalRamMb = totalMb,
            availableRamMb = availableMb,
            usedRamMb = usedMb,
            usagePercent = percent
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
                    if (volume.isPrimary) "Internal shared" else "Storage"
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

        // Always include data partition if missing from volumes list
        if (result.none { it.path == Environment.getDataDirectory().absolutePath }) {
            val data = Environment.getDataDirectory()
            try {
                val s = StatFs(data.path)
                result.add(
                    0,
                    StorageVolumeInfo(
                        name = "Internal data",
                        path = data.absolutePath,
                        description = "App / system data partition",
                        totalBytes = s.totalBytes,
                        freeBytes = s.availableBytes,
                        usedBytes = s.totalBytes - s.availableBytes,
                        isRemovable = false,
                        isPrimary = true,
                        state = Environment.MEDIA_MOUNTED
                    )
                )
            } catch (_: Exception) {
                // ignore
            }
        }

        return result
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
