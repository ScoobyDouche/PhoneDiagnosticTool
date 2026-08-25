package com.phonediagnostic.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

class DeviceInfoCollector(private val context: Context) {

    fun collect(): FullDeviceReport {
        return FullDeviceReport(
            overview = collectOverview(),
            cpu = collectCpu(),
            gpu = collectGpu(),
            battery = collectBattery(),
            memory = collectMemory(),
            storage = collectStorage(),
            display = collectDisplay()
        )
    }

    /**
     * Lightweight update for values that change frequently.
     * Reuses static parts (CPU, GPU, display, storage totals) from the previous report.
     */
    fun collectLive(previous: FullDeviceReport): FullDeviceReport {
        return previous.copy(
            overview = collectOverview(), // uptime changes
            battery = collectBattery(),
            memory = collectMemory()
            // storage free space can change too, but less frequently; keep previous for performance
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
        val processor = try {
            File("/proc/cpuinfo").readLines()
                .firstOrNull { it.startsWith("Processor") || it.startsWith("model name") }
                ?.substringAfter(":")
                ?.trim()
                ?: hardware
        } catch (e: Exception) {
            hardware
        }

        return CpuInfo(
            cores = cores,
            architecture = arch,
            supportedAbis = abis,
            hardware = hardware,
            processor = processor
        )
    }

    private fun collectGpu(): GpuInfo {
        // Best-effort GPU info via EGL / GLES. On some devices this may return generic strings.
        var renderer = "Unknown"
        var vendor = "Unknown"
        var version = "Unknown"

        try {
            val egl = EGLContext.getEGL() as EGL10
            val display: EGLDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            val versionArray = IntArray(2)
            egl.eglInitialize(display, versionArray)

            val configSpec = intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
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
                    intArrayOf(0x3098, 2, EGL10.EGL_NONE) // EGL_CONTEXT_CLIENT_VERSION = 2
                )

                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, ctx)

                renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
                vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
                version = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"

                egl.eglDestroyContext(display, ctx)
            }
            egl.eglTerminate(display)
        } catch (e: Exception) {
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

        return BatteryInfo(
            level = batteryPct,
            status = status,
            health = health,
            temperature = temperature,
            voltage = voltage,
            technology = technology,
            isCharging = isCharging,
            powerSource = powerSource
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
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)

        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val usedBytes = totalBytes - freeBytes

        val totalGb = totalBytes / (1024.0 * 1024 * 1024)
        val freeGb = freeBytes / (1024.0 * 1024 * 1024)
        val usedGb = usedBytes / (1024.0 * 1024 * 1024)
        val percent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0

        return StorageInfo(
            totalInternalGb = totalGb,
            freeInternalGb = freeGb,
            usedInternalGb = usedGb,
            usagePercent = percent
        )
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
        } catch (e: Exception) {
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
}
