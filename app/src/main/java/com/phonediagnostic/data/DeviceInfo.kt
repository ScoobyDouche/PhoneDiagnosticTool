package com.phonediagnostic.data

data class DeviceOverview(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val androidVersion: String,
    val apiLevel: Int,
    val buildId: String,
    val securityPatch: String,
    val uptime: String,
    val fingerprint: String = "",
    val board: String = "",
    val bootloader: String = "",
    val hardware: String = "",
    val host: String = "",
    val tags: String = "",
    val type: String = "",
    val kernelVersion: String = "",
    val radioVersion: String = ""
)

data class CpuInfo(
    val cores: Int,
    val architecture: String,
    val supportedAbis: List<String>,
    val hardware: String,
    val processor: String,
    val boardPlatform: String,
    /** Best-effort current frequencies in MHz (one per online core when readable). */
    val currentFreqMhz: List<Int> = emptyList(),
    /** Best-effort min/max from cpufreq policy when readable. */
    val minFreqMhz: Int? = null,
    val maxFreqMhz: Int? = null,
    /**
     * Name of the elevated tier (SHIZUKU / ROOT) that supplied the clock
     * readings, or null when they came from a direct read. Lets the UI mark
     * values only visible thanks to elevated access.
     */
    val clockSource: String? = null,
    /** Distinct cpufreq governors across clusters. */
    val governors: List<String> = emptyList(),
    /** Frequencies (MHz) any cluster can run at, ascending. */
    val availableFreqMhz: List<Int> = emptyList(),
    /** Online core indices when /sys topology is readable. */
    val onlineCores: List<Int> = emptyList(),
    /** Cluster layout from peak clocks when detectable (e.g. "4 + 3 + 1 cores · 2.0 / 2.8 / 3.3 GHz"). */
    val topologyHint: String = "",
    /**
     * Core number of each [currentFreqMhz] entry. An offline core has no clock
     * to read, so without this the readings after it would be labelled one core
     * too low.
     */
    val freqCoreIndices: List<Int> = emptyList(),
    /** Peak clock of each core's own cluster, so a big core is not measured against a little one. */
    val coreMaxMhz: Map<Int, Int> = emptyMap(),
    /** Core designs from the CPU ID registers, e.g. "4× Cortex-A55", "1× Cortex-X3". */
    val coreNames: List<String> = emptyList(),
    /** Instruction-set extensions the kernel reports (aes, sha2, sve ...). */
    val features: List<String> = emptyList(),
    /** Chip maker and part as the platform names them (API 31+), e.g. "Qualcomm" / "SM8475". */
    val socManufacturer: String = "",
    val socModel: String = ""
)

data class GpuInfo(
    val renderer: String,
    val vendor: String,
    val version: String
)

data class BatteryInfo(
    val level: Int,
    val status: String,
    val health: String,
    val temperature: Float,
    val voltage: Int,
    val technology: String,
    val isCharging: Boolean,
    val powerSource: String,
    /** Instantaneous current in mA; null if unavailable. Negative often means discharging. */
    val currentNowMa: Int?,
    /** Average current in mA; null if unavailable. */
    val currentAvgMa: Int?,
    /** Design / reported capacity in mAh when exposed. */
    val capacityMah: Int? = null,
    /** Charge counter (µAh) when exposed. */
    val chargeCounterUah: Long? = null,
    /** Full capacity measured over a real charge; see BatteryCapacityTracker. */
    val measuredCapacity: MeasuredCapacity? = null,
    /** Present full-charge capacity in mAh, from the fuel gauge. Null if not exposed. */
    val fullChargeMah: Int? = null,
    /** Factory design capacity in mAh, from the fuel gauge. Null if not exposed. */
    val designChargeMah: Int? = null,
    /**
     * Remaining capacity as a percentage of design, i.e. wear. Only produced
     * when both gauge figures are readable and plausible — never estimated,
     * because a made-up battery-health number is worse than none.
     */
    val capacityHealthPercent: Int? = null,
    /**
     * Charge cycles the battery has been through. Android 14 (API 34) exposes
     * this on the battery-changed broadcast; nothing below that reports it, and
     * not every vendor populates it even then.
     */
    val cycleCount: Int? = null,
    /**
     * Name of the elevated tier (SHIZUKU / ROOT) that made the capacity-health
     * figure readable, or null when it came from a direct read. Null health with
     * a non-null source never happens — this is only set when a value exists.
     */
    val healthSource: String? = null,
    /** As [healthSource], for the charge-cycle count. */
    val cycleSource: String? = null,
    /**
     * What the plugged-in charger offers, in watts, from the battery broadcast's
     * max charging current and voltage. Null when unplugged or not reported.
     */
    val chargerMaxWatts: Float? = null,
    /** Android's estimate of time until full, in ms; null when not charging or unknown. */
    val chargeTimeRemainingMs: Long? = null,
    /** Whether Battery Saver is on. */
    val powerSaveMode: Boolean = false,
    /** Remaining energy in mWh from the fuel gauge's energy counter, when exposed. */
    val energyMwh: Int? = null
) {
    /**
     * Instantaneous power in watts: positive charging, negative discharging.
     * Null unless both voltage and current are available.
     */
    val powerWatts: Float?
        get() {
            val ma = currentNowMa ?: return null
            if (voltage <= 0) return null
            return (voltage / 1000f) * (ma / 1000f)
        }
}

data class MemoryInfo(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val usedRamMb: Long,
    val usagePercent: Int,
    /** Android low-memory threshold (MB). Below this, system starts killing caches. */
    val thresholdMb: Long = 0,
    /** True only when system is under real memory pressure. */
    val isLowMemory: Boolean = false,
    /** Short human status for UI. */
    val statusHint: String = "",
    /** Kernel-reported Cached (MB) from /proc/meminfo when readable. */
    val cachedMb: Long? = null,
    /** Kernel-reported Buffers (MB). */
    val buffersMb: Long? = null,
    /** Swap total (MB); 0 or null when no swap. */
    val swapTotalMb: Long? = null,
    /** Swap free (MB). */
    val swapFreeMb: Long? = null,
    /** RAM taken by ZRAM's compressed pages (mm_stat mem_used_total), MB. */
    val zramMb: Long? = null,
    /** Kernel Active summary when readable. */
    val kernelActiveMb: Long? = null
)

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val description: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
    val state: String
) {
    val totalGb: Double get() = totalBytes / (1024.0 * 1024 * 1024)
    val freeGb: Double get() = freeBytes / (1024.0 * 1024 * 1024)
    val usedGb: Double get() = usedBytes / (1024.0 * 1024 * 1024)
    val usagePercent: Int
        get() = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0
}

data class StorageInfo(
    val totalInternalGb: Double,
    val freeInternalGb: Double,
    val usedInternalGb: Double,
    val usagePercent: Int,
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val dataDirectory: String = "",
    val cacheDirectory: String = "",
    val filesDirectory: String = "",
    val externalStorageState: String = "",
    val emulatedExternal: Boolean = false,
    /** UFS / eMMC / NVMe, from the block devices present. */
    val storageType: String = "",
    /** Wear from the flash chip's JEDEC life-time estimate, e.g. "10–20% used". */
    val lifeUsed: String? = null,
    /** JEDEC pre-end-of-life status of the spare block pool. */
    val preEol: String? = null,
    /** Elevated tier that made the wear figures readable, null for a direct read. */
    val healthSource: String? = null
)

/**
 * Display panel characteristics. All fields are best-effort from public APIs;
 * missing values stay at sensible defaults so older call sites remain valid.
 */
data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val density: Float,
    val refreshRate: Float,
    val screenSizeInches: Double,
    /** Refresh rates the panel offers at its current resolution, ascending. */
    val supportedRefreshRatesHz: List<Float> = emptyList(),
    /** HDR formats the panel supports (HDR10, HLG, ...); empty when none. */
    val hdrFormats: List<String> = emptyList(),
    /** Whether the panel reports wide colour gamut support. */
    val wideColorGamut: Boolean = false,
    /** Portrait / Landscape at collection time. */
    val orientation: String = "",
    /** Peak brightness the panel targets for HDR content, in nits; null when unreported. */
    val hdrMaxNits: Float? = null,
    /** Every resolution the panel offers with its refresh rates, e.g. "1080×2400 @ 60/90/120 Hz". */
    val modes: List<String> = emptyList()
)

data class NetworkInfo(
    val isConnected: Boolean,
    val networkType: String,
    val latencyMs: Long?,
    val latencyTarget: String,
    val latencyStatus: String,
    /** Downstream link bandwidth in Mbps when reported by the system. */
    val downstreamMbps: Int? = null,
    /** Upstream link bandwidth in Mbps when reported. */
    val upstreamMbps: Int? = null,
    val validated: Boolean = false,
    val metered: Boolean = false
)

/** Result of repeating the latency probe, so spread and loss are visible. */
data class LatencyStats(
    val target: String,
    val samplesMs: List<Long>,
    val attempts: Int,
    val minMs: Long?,
    val avgMs: Long?,
    val maxMs: Long?,
    /** Mean absolute deviation from the average. */
    val jitterMs: Long?,
    val lossPercent: Int,
    /** Set only when every attempt failed. */
    val lastError: String? = null
)

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val addresses: List<String>,
    val isUp: Boolean,
    val mtu: Int
)

data class WifiDetail(
    val ssid: String,
    val linkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val txLinkSpeedMbps: Int?,
    val rssiDbm: Int?,
    /** 0..4, derived from RSSI. */
    val signalLevel: Int,
    val frequencyMhz: Int?,
    val band: String,
    /** "Wi-Fi 6E", "Wi-Fi 7" ... from the negotiated standard; blank below API 30. */
    val standard: String = ""
)

data class CellularDetail(
    val carrier: String,
    val simOperator: String,
    val countryIso: String,
    val phoneType: String,
    val roaming: Boolean,
    /** "5G (standalone)", "5G (with LTE anchor)", "4G LTE" ... from signal strength, not cell IDs. */
    val generation: String = "",
    val signalDbm: Int? = null,
    /** 0..4 bars. */
    val signalLevel: Int? = null
)

/** On-demand companion to [NetworkInfo] for the Network detail screen. */
data class NetworkDetail(
    val interfaces: List<NetworkInterfaceInfo> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val privateDnsServer: String? = null,
    val privateDnsActive: Boolean = false,
    val interfaceName: String = "",
    val domains: String = "",
    val wifi: WifiDetail? = null,
    val cellular: CellularDetail? = null,
    val capabilities: List<String> = emptyList(),
    /** Android confirmed this network reaches the internet. */
    val validated: Boolean? = null,
    /** The network wants a sign-in page before it carries traffic. */
    val captivePortal: Boolean = false,
    val ipv6: Ipv6Status = Ipv6Status.NONE
)

/** How far IPv6 works on the active network. */
enum class Ipv6Status {
    /** A global address and a default route: IPv6 sites are reachable. */
    WORKING,
    /** A global address but no IPv6 default route. */
    ADDRESS_ONLY,
    /** Link-local only, or nothing. */
    NONE;

    companion object {
        /** Pure, for tests. */
        fun classify(hasGlobalAddress: Boolean, hasDefaultRoute: Boolean): Ipv6Status = when {
            hasGlobalAddress && hasDefaultRoute -> WORKING
            hasGlobalAddress -> ADDRESS_ONLY
            else -> NONE
        }
    }
}

data class SensorEntry(
    val name: String,
    val type: String,
    val vendor: String,
    val powerMa: Float,
    val resolution: Float,
    val maxRange: Float,
    val minDelayUs: Int,
    /** Live reading when available (joined values). */
    val liveValues: String = "",
    /** Maximum reporting latency in µs (0 = continuous). */
    val maxDelayUs: Int = 0,
    /** Whether this is a wake-up sensor. */
    val isWakeUp: Boolean = false,
    /** FIFO max event count when the sensor supports batching. */
    val fifoMaxEventCount: Int = 0,
    /** Reporting mode string (continuous / on-change / one-shot / special). */
    val reportingMode: String = "",
    /** `Sensor.TYPE_*` number; vendor sensors start at 65536. */
    val typeId: Int = 0,
    /** The platform's dotted type string, e.g. "android.sensor.accelerometer". */
    val stringType: String = "",
    /** Unit of the readings ("m/s²", "lx" ...); blank when the values are unitless. */
    val unit: String = "",
    val category: SensorCategory = SensorCategory.OTHER,
    /** Driver version the sensor reports. */
    val version: Int = 0
) {
    /**
     * Identifies one sensor for the detail screen. The name alone is not enough:
     * vendors ship wake-up and non-wake-up variants under the same name.
     */
    val key: String get() = "$typeId|${if (isWakeUp) 1 else 0}|$name"
}

data class CameraEntry(
    val id: String,
    val facing: String,
    val sensorOrientation: Int,
    val hardwareLevel: String,
    val pixelArraySize: String,
    val focalLengths: String,
    val aperture: String,
    /** Physical camera IDs when this is a logical multi-camera. */
    val physicalIds: String = "",
    /** Supported capabilities summary (RAW, MANUAL_SENSOR, ...). */
    val capabilities: String = "",
    /** Sensor active array size when different from pixel array. */
    val activeArraySize: String = "",
    /** ISO range (min-max) when exposed. */
    val isoRange: String = "",
    /** Exposure time range (shortest–longest) when exposed. */
    val exposureRange: String = "",
    /** Whether the camera has a flash unit; null when it does not say. */
    val hasFlash: Boolean? = null
)

data class ThermalZone(
    val name: String,
    val tempC: Float,
    val type: String = "",
    /** Human-friendly type when the kernel type can be mapped. */
    val typeLabel: String = ""
)

/**
 * NFC presence and on/off state. Read-only; never starts discovery.
 */
data class NfcInfo(
    val present: Boolean = false,
    val enabled: Boolean = false
)

/**
 * Bluetooth presence and on/off state. Deliberately carries no adapter name or
 * address: PRIVACY.md promises MAC addresses are never shown, and the name is
 * often the owner's own.
 */
data class BluetoothInfo(
    val present: Boolean = false,
    val enabled: Boolean = false,
    val bleSupported: Boolean = false
)

/**
 * Connected audio routes, by kind (Speaker, Wired headset, Bluetooth A2DP...).
 * Nothing is recorded or played during collection.
 */
data class AudioInfo(
    val outputDevices: List<String> = emptyList(),
    val inputDevices: List<String> = emptyList()
)

/**
 * Boot and encryption state from read-only system properties. A blank string
 * or null means the property was not readable, which is common: several are
 * hidden from apps on recent Android builds.
 */
data class SecurityInfo(
    /** green / yellow / orange / red, as the bootloader reports it. */
    val verifiedBootState: String = "",
    val bootloaderLocked: Boolean? = null,
    /** encrypted / unencrypted, plus file or block when known. */
    val encryption: String = "",
    /** SELinux mode: Enforcing / Permissive / Disabled / unknown. */
    val selinux: String = "",
    /** Whether the device reports being a userdebug/eng build. */
    val isDebuggableBuild: Boolean? = null,
    /** Selected safe build tags (user / userdebug / eng). */
    val buildTags: String = "",
    /** Days since the security patch level; null when the date cannot be parsed. */
    val patchAgeDays: Int? = null,
    /** Keys can live in a dedicated security chip (StrongBox); null below API 28. */
    val strongBox: Boolean? = null,
    /** Hardware-backed keystore (TEE) reported by the platform; null below API 31. */
    val hardwareKeystore: Boolean? = null
)

data class WakeSource(val name: String, val activeMs: Long)

/** Sleep and wake behaviour since boot. */
data class SystemState(
    val sinceBootMs: Long = 0,
    /** Time the CPU was running, i.e. not in deep sleep. */
    val awakeMs: Long = 0,
    val deepSleepPercent: Int? = null,
    /** Why the phone last woke from suspend; needs elevated access on most builds. */
    val lastWakeReason: String? = null,
    /** Sources that held the phone awake longest since boot. */
    val topWakeSources: List<WakeSource> = emptyList(),
    /** Elevated tier that made the wake data readable, null for a direct read. */
    val wakeSource: String? = null
)

/** Android's own thermal verdict, as opposed to raw zone temperatures. */
data class ThermalState(
    /** None / Light / Moderate / Severe / Critical / Emergency / Shutdown; blank below API 29. */
    val status: String = "",
    /**
     * Forecast 10 s ahead where 1.0 is the point the system starts severe
     * throttling. Null below API 30 or when the device does not support it.
     */
    val headroom: Float? = null
)

/** One screen the system knows about; a foldable reports its cover screen here too. */
data class DisplayEntry(
    val id: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val refreshRate: Float,
    /** The screen the system treats as primary. */
    val isDefault: Boolean
)

/** A hardware capability and whether this device has it. */
data class HardwareFeature(val name: String, val present: Boolean)

/**
 * Hardware and platform facts that do not change while the app runs. All from
 * public APIs that need no permission; nothing here identifies the device or
 * its owner.
 */
data class HardwareInfo(
    val displays: List<DisplayEntry> = emptyList(),
    val features: List<HardwareFeature> = emptyList(),
    /** OpenGL ES version the device declares, e.g. "3.2". */
    val glEsVersion: String = "",
    /** Vulkan API version, e.g. "1.3"; blank when Vulkan is unsupported. */
    val vulkanVersion: String = "",
    val hasVibrator: Boolean = false,
    /** Whether vibration strength can be varied, not just switched on and off. */
    val vibratorAmplitudeControl: Boolean = false,
    /** Native output sample rate in Hz. */
    val audioSampleRate: Int? = null,
    /** Native output buffer size in frames; smaller means lower latency. */
    val audioFramesPerBuffer: Int? = null,
    val audioLowLatency: Boolean = false,
    val audioPro: Boolean = false,
    /** Satellite-positioning chip model and year; read without a location permission. */
    val gnssModel: String = "",
    val gnssYear: Int? = null,
    /** Number of SIM slots the modem can use at once. */
    val simSlots: Int? = null,
    /** Kernel memory page size in KB (4 or 16). */
    val pageSizeKb: Int? = null,
    /** Kernel architecture from uname, e.g. "aarch64". */
    val kernelArch: String = "",
    /** True when the device cannot run 32-bit apps at all. */
    val is64BitOnly: Boolean = false,
    val isLowRamDevice: Boolean = false,
    /** Per-app Java heap limits in MB. */
    val heapLimitMb: Int? = null,
    val largeHeapLimitMb: Int? = null,
    /** Android runtime (ART) version. */
    val runtimeVersion: String = "",
    /** API level the device originally shipped with. */
    val firstApiLevel: Int? = null,
    val trebleEnabled: Boolean? = null,
    /** Seamless (A/B) updates. */
    val abUpdates: Boolean? = null,
    val dynamicPartitions: Boolean? = null
)

data class FullDeviceReport(
    val overview: DeviceOverview,
    val cpu: CpuInfo,
    val gpu: GpuInfo,
    val battery: BatteryInfo,
    val memory: MemoryInfo,
    val storage: StorageInfo,
    val display: DisplayInfo,
    val network: NetworkInfo,
    val sensors: List<SensorEntry> = emptyList(),
    val cameras: List<CameraEntry> = emptyList(),
    val thermals: List<ThermalZone> = emptyList(),
    val nfc: NfcInfo = NfcInfo(),
    val bluetooth: BluetoothInfo = BluetoothInfo(),
    val audio: AudioInfo = AudioInfo(),
    val security: SecurityInfo = SecurityInfo(),
    val system: SystemState = SystemState(),
    val mediaCodecs: List<CodecSupport> = emptyList(),
    val thermalState: ThermalState = ThermalState(),
    val hardware: HardwareInfo = HardwareInfo()
)
