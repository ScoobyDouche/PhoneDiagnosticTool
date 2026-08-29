package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import com.phonediagnostic.ui.components.UsageBar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    report: FullDeviceReport?,
    isLive: Boolean,
    lastUpdated: String,
    isRefreshing: Boolean,
    errorMessage: String?,
    versionName: String,
    onToggleLive: () -> Unit,
    onRefresh: () -> Unit,
    onShareText: () -> Unit,
    onShareJson: () -> Unit,
    onCopyText: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRamDetail: () -> Unit,
    onOpenStorageDetail: () -> Unit,
    onOpenSensors: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Phone Diagnostic", fontWeight = FontWeight.SemiBold)
                        if (lastUpdated.isNotEmpty()) {
                            Text(
                                text = if (isLive) "Live · $lastUpdated" else "Paused · $lastUpdated",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onToggleLive) {
                        Icon(
                            imageVector = if (isLive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLive) "Pause live updates" else "Resume live updates"
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share as text") },
                            onClick = {
                                menuOpen = false
                                onShareText()
                            },
                            enabled = report != null
                        )
                        DropdownMenuItem(
                            text = { Text("Share as JSON") },
                            onClick = {
                                menuOpen = false
                                onShareJson()
                            },
                            enabled = report != null
                        )
                        DropdownMenuItem(
                            text = { Text("Copy text") },
                            onClick = {
                                menuOpen = false
                                onCopyText()
                            },
                            enabled = report != null
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuOpen = false
                                onOpenSettings()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                report == null && errorMessage != null -> {
                    ErrorState(message = errorMessage, onRetry = onRefresh)
                }
                report == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.size(16.dp))
                        Text("Collecting device info…")
                        Text(
                            "Pull down to refresh",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                else -> {
                    ReportList(
                        data = report,
                        isLive = isLive,
                        versionName = versionName,
                        onOpenRamDetail = onOpenRamDetail,
                        onOpenStorageDetail = onOpenStorageDetail,
                        onOpenSensors = onOpenSensors
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportList(
    data: FullDeviceReport,
    isLive: Boolean,
    versionName: String,
    onOpenRamDetail: () -> Unit,
    onOpenStorageDetail: () -> Unit,
    onOpenSensors: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "live_badge") { LiveBadge(isLive = isLive) }

        item(key = "device") {
            InfoCard(title = "Device") {
                Column {
                    InfoRow("Manufacturer", data.overview.manufacturer)
                    InfoRow("Model", data.overview.model)
                    InfoRow("Brand", data.overview.brand)
                    InfoRow("Android", "${data.overview.androidVersion} (API ${data.overview.apiLevel})")
                    InfoRow("Security Patch", data.overview.securityPatch)
                    InfoRow("Build ID", data.overview.buildId)
                    if (data.overview.board.isNotBlank()) {
                        InfoRow("Board", data.overview.board)
                    }
                    if (data.overview.bootloader.isNotBlank()) {
                        InfoRow("Bootloader", data.overview.bootloader)
                    }
                    if (data.overview.hardware.isNotBlank()) {
                        InfoRow("Hardware", data.overview.hardware)
                    }
                    if (data.overview.type.isNotBlank()) {
                        InfoRow("Build type", data.overview.type)
                    }
                    InfoRow("Uptime", data.overview.uptime)
                    if (data.overview.kernelVersion.isNotBlank()) {
                        InfoRow("Kernel", data.overview.kernelVersion)
                    }
                    if (data.overview.radioVersion.isNotBlank()) {
                        InfoRow("Radio", data.overview.radioVersion)
                    }
                    if (data.overview.fingerprint.isNotBlank()) {
                        InfoRow("Fingerprint", data.overview.fingerprint)
                    }
                }
            }
        }

        item(key = "cpu") {
            InfoCard(title = "CPU / SoC · Live") {
                Column {
                    InfoRow("Cores", data.cpu.cores.toString())
                    InfoRow("Architecture", data.cpu.architecture)
                    InfoRow("Board / Platform", data.cpu.boardPlatform)
                    InfoRow("Hardware", data.cpu.hardware)
                    InfoRow("Processor", data.cpu.processor)
                    InfoRow("ABIs", data.cpu.supportedAbis.joinToString(", "))
                    if (data.cpu.minFreqMhz != null || data.cpu.maxFreqMhz != null) {
                        val min = data.cpu.minFreqMhz?.toString() ?: "?"
                        val max = data.cpu.maxFreqMhz?.toString() ?: "?"
                        InfoRow("Freq range", "$min – $max MHz")
                    }
                    if (data.cpu.currentFreqMhz.isNotEmpty()) {
                        InfoRow(
                            "Current (cores)",
                            data.cpu.currentFreqMhz.joinToString(", ") { "$it" } + " MHz"
                        )
                    }
                }
            }
        }

        item(key = "gpu") {
            InfoCard(title = "GPU") {
                Column {
                    InfoRow("Renderer", data.gpu.renderer)
                    InfoRow("Vendor", data.gpu.vendor)
                    InfoRow("Version", data.gpu.version)
                }
            }
        }

        item(key = "battery") {
            InfoCard(title = "Battery · Live") {
                Column {
                    UsageBar(
                        label = "Level",
                        percent = data.battery.level.coerceIn(0, 100),
                        detail = "${data.battery.level}%"
                    )
                    InfoRow("Status", data.battery.status)
                    InfoRow("Health", data.battery.health)
                    InfoRow("Temperature", String.format(Locale.US, "%.1f °C", data.battery.temperature))
                    InfoRow("Voltage", "${data.battery.voltage} mV")
                    InfoRow(
                        "Current (now)",
                        data.battery.currentNowMa?.let { "$it mA" } ?: "Unavailable"
                    )
                    InfoRow(
                        "Current (avg)",
                        data.battery.currentAvgMa?.let { "$it mA" } ?: "Unavailable"
                    )
                    if (data.battery.capacityMah != null) {
                        InfoRow("Design capacity", "${data.battery.capacityMah} mAh")
                    }
                    if (data.battery.chargeCounterUah != null) {
                        val mah = data.battery.chargeCounterUah / 1000.0
                        InfoRow("Charge counter", String.format(Locale.US, "%.0f mAh", mah))
                    }
                    InfoRow("Technology", data.battery.technology)
                    InfoRow("Power Source", data.battery.powerSource)
                }
            }
        }

        item(key = "memory") {
            val m = data.memory
            InfoCard(
                title = "Memory (RAM) · Live",
                onClick = onOpenRamDetail
            ) {
                Column {
                    UsageBar(
                        label = "In use (incl. cache)",
                        percent = m.usagePercent,
                        detail = "${m.usedRamMb} / ${m.totalRamMb} MB"
                    )
                    InfoRow("Available", "${m.availableRamMb} MB")
                    if (m.thresholdMb > 0) {
                        InfoRow("Low-mem threshold", "${m.thresholdMb} MB")
                    }
                    InfoRow(
                        "Pressure",
                        if (m.isLowMemory) "Yes — freeing caches" else "No"
                    )
                    if (m.statusHint.isNotBlank()) {
                        Text(
                            text = m.statusHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (m.isLowMemory) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color(0xFF2E7D32)
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        item(key = "network") {
            InfoCard(title = "Network") {
                Column {
                    InfoRow("Connection", if (data.network.isConnected) "Connected" else "Disconnected")
                    InfoRow("Type", data.network.networkType)
                    InfoRow(
                        "Latency",
                        data.network.latencyMs?.let { "$it ms" } ?: "—"
                    )
                    InfoRow("Target", data.network.latencyTarget)
                    InfoRow("Status", data.network.latencyStatus)
                    if (data.network.downstreamMbps != null || data.network.upstreamMbps != null) {
                        val down = data.network.downstreamMbps?.toString() ?: "?"
                        val up = data.network.upstreamMbps?.toString() ?: "?"
                        InfoRow("Link bandwidth", "$down ↓ / $up ↑ Mbps")
                    }
                    InfoRow("Validated", if (data.network.validated) "Yes" else "No")
                    InfoRow("Metered", if (data.network.metered) "Yes" else "No")
                }
            }
        }

        item(key = "storage") {
            val s = data.storage
            InfoCard(
                title = "Storage",
                onClick = onOpenStorageDetail
            ) {
                Column {
                    UsageBar(
                        label = "Internal data",
                        percent = s.usagePercent,
                        detail = String.format(
                            Locale.US,
                            "%.1f / %.1f GB",
                            s.usedInternalGb,
                            s.totalInternalGb
                        )
                    )
                    InfoRow(
                        "Free",
                        String.format(Locale.US, "%.2f GB", s.freeInternalGb)
                    )
                    InfoRow("Volumes", s.volumes.size.toString())
                    InfoRow(
                        "External",
                        if (s.emulatedExternal) "Emulated (${s.externalStorageState})"
                        else s.externalStorageState.ifBlank { "—" }
                    )
                    if (s.dataDirectory.isNotBlank()) {
                        InfoRow("Data path", s.dataDirectory)
                    }
                }
            }
        }

        item(key = "display") {
            InfoCard(title = "Display") {
                Column {
                    InfoRow("Resolution", "${data.display.widthPx} × ${data.display.heightPx}")
                    InfoRow(
                        "Density",
                        "${data.display.densityDpi} dpi (×${String.format(Locale.US, "%.2f", data.display.density)})"
                    )
                    InfoRow(
                        "Refresh Rate",
                        String.format(Locale.US, "%.1f Hz", data.display.refreshRate)
                    )
                    InfoRow(
                        "Approx. Size",
                        String.format(Locale.US, "%.2f\"", data.display.screenSizeInches)
                    )
                }
            }
        }

        if (data.thermals.isNotEmpty()) {
            item(key = "thermals") {
                InfoCard(title = "Thermals · Live") {
                    Column {
                        data.thermals.take(8).forEach { zone ->
                            val label = zone.type.ifBlank { zone.name }
                            InfoRow(
                                label.take(28),
                                String.format(Locale.US, "%.1f °C", zone.tempC)
                            )
                        }
                        if (data.thermals.size > 8) {
                            InfoRow("…", "+${data.thermals.size - 8} more zones")
                        }
                    }
                }
            }
        }

        item(key = "sensors") {
            InfoCard(
                title = "Sensors & cameras",
                onClick = onOpenSensors
            ) {
                Column {
                    InfoRow("Sensors", data.sensors.size.toString())
                    InfoRow("Cameras", data.cameras.size.toString())
                    if (data.cameras.isNotEmpty()) {
                        val summary = data.cameras.joinToString(" · ") {
                            "${it.facing} (${it.pixelArraySize})"
                        }
                        InfoRow("Facing", summary)
                    }
                    val withLive = data.sensors.count { it.liveValues.isNotBlank() }
                    if (withLive > 0) {
                        InfoRow("Live samples", "$withLive sensors")
                    }
                }
            }
        }

        item(key = "footer") {
            Text(
                text = "Phone Diagnostic Tool · v$versionName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Couldn’t load diagnostics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Try again")
        }
    }
}

@Composable
private fun LiveBadge(isLive: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = if (isLive)
            Color(0xFF4CAF50).copy(alpha = 0.12f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isLive) "● LIVE" else "○ PAUSED",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isLive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isLive) "Battery, RAM, CPU freq, sensors, thermals every 3s · Pull to full refresh"
                else "Tap play to resume",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
