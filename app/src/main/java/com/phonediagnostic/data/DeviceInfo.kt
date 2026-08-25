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
    val processor: String
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
    val powerSource: String
)

data class MemoryInfo(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val usedRamMb: Long,
    val usagePercent: Int
)

data class StorageInfo(
    val totalInternalGb: Double,
    val freeInternalGb: Double,
    val usedInternalGb: Double,
    val usagePercent: Int
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
    val networkType: String,          // Wi-Fi, Cellular, Ethernet, None, Unknown
    val latencyMs: Long?,             // null if measurement failed / offline
    val latencyTarget: String,        // e.g. "8.8.8.8:53"
    val latencyStatus: String         // "OK", "Timeout", "No network", "Error: ..."
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
