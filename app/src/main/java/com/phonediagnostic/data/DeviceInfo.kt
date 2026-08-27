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
    val uptime: String
)

data class CpuInfo(
    val cores: Int,
    val architecture: String,
    val supportedAbis: List<String>,
    val hardware: String,
    val processor: String,
    val boardPlatform: String
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
    val currentAvgMa: Int?
)

data class MemoryInfo(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val usedRamMb: Long,
    val usagePercent: Int
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
    val latencyStatus: String
)

data class FullDeviceReport(
    val overview: DeviceOverview,
    val cpu: CpuInfo,
    val gpu: GpuInfo,
    val battery: BatteryInfo,
    val memory: MemoryInfo,
    val storage: StorageInfo,
    val display: DisplayInfo,
    val network: NetworkInfo
)
