package com.phonediagnostic.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Vibrator
import android.system.Os
import android.system.OsConstants
import android.telephony.TelephonyManager
import android.view.Display

/**
 * Collects [HardwareInfo]: what the device is built from, as opposed to what it
 * is doing right now. Everything here is fixed for the life of the process, so
 * the collector reads it once per full collect and never on the live tick.
 *
 * No call below needs a permission, and none returns an identifier — feature
 * flags, counts and model names only.
 */
class HardwareProbe(
    private val context: Context,
    private val readProperty: (String) -> String
) {

    private val pm: PackageManager = context.packageManager

    fun collect(): HardwareInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val vibrator = vibrator()
        val (gnssModel, gnssYear) = gnss()
        return HardwareInfo(
            displays = displays(),
            features = features(),
            glEsVersion = attempt { am?.deviceConfigurationInfo?.glEsVersion }.orEmpty(),
            vulkanVersion = vulkanVersion(),
            hasVibrator = attempt { vibrator?.hasVibrator() } == true,
            vibratorAmplitudeControl = attempt { vibrator?.hasAmplitudeControl() } == true,
            audioSampleRate = attempt {
                audio?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            }?.takeIf { it > 0 },
            audioFramesPerBuffer = attempt {
                audio?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
            }?.takeIf { it > 0 },
            audioLowLatency = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY),
            audioPro = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO),
            gnssModel = gnssModel,
            gnssYear = gnssYear,
            simSlots = simSlots(),
            pageSizeKb = attempt { Os.sysconf(OsConstants._SC_PAGESIZE) }
                ?.takeIf { it > 0 }?.let { (it / 1024).toInt() },
            kernelArch = attempt { Os.uname().machine }.orEmpty(),
            is64BitOnly = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && Build.SUPPORTED_32_BIT_ABIS.isEmpty(),
            isLowRamDevice = attempt { am?.isLowRamDevice } == true,
            heapLimitMb = attempt { am?.memoryClass }?.takeIf { it > 0 },
            largeHeapLimitMb = attempt { am?.largeMemoryClass }?.takeIf { it > 0 },
            runtimeVersion = attempt { System.getProperty("java.vm.version") }.orEmpty(),
            firstApiLevel = readProperty("ro.product.first_api_level").toIntOrNull()?.takeIf { it > 0 },
            trebleEnabled = booleanProperty("ro.treble.enabled"),
            abUpdates = booleanProperty("ro.build.ab_update"),
            dynamicPartitions = booleanProperty("ro.boot.dynamic_partitions")
        )
    }

    private fun displays(): List<DisplayEntry> = attempt {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        dm?.displays.orEmpty().map { display ->
            val mode = attempt { display.mode }
            DisplayEntry(
                id = display.displayId,
                name = display.name.orEmpty(),
                widthPx = mode?.physicalWidth ?: 0,
                heightPx = mode?.physicalHeight ?: 0,
                refreshRate = attempt { display.refreshRate } ?: 0f,
                isDefault = display.displayId == Display.DEFAULT_DISPLAY
            )
        }
    }.orEmpty()

    private fun features(): List<HardwareFeature> {
        val flags = FEATURE_FLAGS.map { (name, flag) -> HardwareFeature(name, pm.hasSystemFeature(flag)) }
        return flags + wifiBands()
    }

    /** Band support comes from the Wi-Fi chip, not a feature flag. */
    private fun wifiBands(): List<HardwareFeature> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()
        val bands = ArrayList<HardwareFeature>()
        attempt { wifi.is5GHzBandSupported }?.let { bands += HardwareFeature("Wi-Fi 5 GHz", it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            attempt { wifi.is6GHzBandSupported }?.let { bands += HardwareFeature("Wi-Fi 6 GHz", it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            attempt { wifi.isWpa3SaeSupported }?.let { bands += HardwareFeature("WPA3", it) }
        }
        return bands
    }

    private fun vulkanVersion(): String {
        val feature = attempt {
            pm.systemAvailableFeatures.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
        } ?: return ""
        return decodeVulkanVersion(feature.version)
    }

    @Suppress("DEPRECATION") // VibratorManager (API 31) only wraps the same default vibrator.
    private fun vibrator(): Vibrator? =
        attempt { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    /** Chip model and year are device facts; no position is requested or read. */
    private fun gnss(): Pair<String, Int?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "" to null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return "" to null
        val model = attempt { lm.gnssHardwareModelName }.orEmpty()
        val year = attempt { lm.gnssYearOfHardware }?.takeIf { it > 2000 }
        return model to year
    }

    private fun simSlots(): Int? {
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return null
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        return attempt {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tm.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                tm.phoneCount
            }
        }?.takeIf { it > 0 }
    }

    private fun booleanProperty(key: String): Boolean? = when (readProperty(key).lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }

    /** Vendor services throw unexpected things; one unreadable value must not lose the rest. */
    private inline fun <T> attempt(block: () -> T?): T? = try {
        block()
    } catch (_: Throwable) {
        null
    }

    companion object {
        private val FEATURE_FLAGS = listOf(
            "Fingerprint reader" to PackageManager.FEATURE_FINGERPRINT,
            // Literals where the constant is newer than minSdk; an unknown
            // feature name simply reports false on older releases.
            "Face recognition" to "android.hardware.biometrics.face",
            "Iris scanner" to "android.hardware.biometrics.iris",
            "NFC" to PackageManager.FEATURE_NFC,
            "NFC card emulation" to PackageManager.FEATURE_NFC_HOST_CARD_EMULATION,
            "Embedded secure element" to "android.hardware.se.omapi.ese",
            "Ultra-wideband (UWB)" to "android.hardware.uwb",
            "Cellular" to PackageManager.FEATURE_TELEPHONY,
            "eSIM" to "android.hardware.telephony.euicc",
            "Satellite positioning" to PackageManager.FEATURE_LOCATION_GPS,
            "Bluetooth LE" to PackageManager.FEATURE_BLUETOOTH_LE,
            "Wi-Fi Direct" to PackageManager.FEATURE_WIFI_DIRECT,
            "Wi-Fi Aware" to PackageManager.FEATURE_WIFI_AWARE,
            "Wi-Fi ranging (RTT)" to "android.hardware.wifi.rtt",
            "Infrared emitter" to PackageManager.FEATURE_CONSUMER_IR,
            "USB host (OTG)" to PackageManager.FEATURE_USB_HOST,
            "Camera flash" to PackageManager.FEATURE_CAMERA_FLASH
        )

        /** Vulkan packs its version as major (10 bits) . minor (10 bits) . patch (12 bits). */
        internal fun decodeVulkanVersion(packed: Int): String {
            if (packed <= 0) return ""
            return "${packed ushr 22}.${(packed ushr 12) and 0x3ff}"
        }
    }
}
