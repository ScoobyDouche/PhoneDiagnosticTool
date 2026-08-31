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
    val maxFreqMhz: Int? = null
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
    val chargeCounterUah: Long? = null
)

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
    val statusHint: String = ""
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
    val emulatedExternal: Boolean = false
)

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val density: Float,
    val refreshRate: Float,
    val screenSizeInches: Double
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
    val band: String
)

data class CellularDetail(
    val carrier: String,
    val simOperator: String,
    val countryIso: String,
    val phoneType: String,
    val roaming: Boolean
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
    val capabilities: List<String> = emptyList()
)

data class SensorEntry(
    val name: String,
    val type: String,
    val vendor: String,
    val powerMa: Float,
    val resolution: Float,
    val maxRange: Float,
    val minDelayUs: Int,
    /** Live reading when available (joined values). */
    val liveValues: String = ""
)

data class CameraEntry(
    val id: String,
    val facing: String,
    val sensorOrientation: Int,
    val hardwareLevel: String,
    val pixelArraySize: String,
    val focalLengths: String,
    val aperture: String
)

data class ThermalZone(
    val name: String,
    val tempC: Float,
    val type: String = ""
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
    val thermals: List<ThermalZone> = emptyList()
)
