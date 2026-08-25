package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    report: FullDeviceReport?,
    isLive: Boolean,
    lastUpdated: String,
    onToggleLive: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Phone Diagnostic")
                        if (lastUpdated.isNotEmpty()) {
                            Text(
                                text = if (isLive) "Live · Updated $lastUpdated" else "Paused · $lastUpdated",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh now")
                    }
                    IconButton(onClick = onToggleLive) {
                        Icon(
                            imageVector = if (isLive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLive) "Pause live updates" else "Resume live updates"
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
        if (report == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.size(16.dp))
                Text("Collecting device info…")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    LiveBadge(isLive = isLive)
                }

                item {
                    InfoCard(title = "Device Overview") {
                        Column {
                            InfoRow("Manufacturer", report.overview.manufacturer)
                            InfoRow("Model", report.overview.model)
                            InfoRow("Brand", report.overview.brand)
                            InfoRow("Android", "${report.overview.androidVersion} (API ${report.overview.apiLevel})")
                            InfoRow("Security Patch", report.overview.securityPatch)
                            InfoRow("Build ID", report.overview.buildId)
                            InfoRow("Uptime", report.overview.uptime)
                        }
                    }
                }

                item {
                    InfoCard(title = "CPU / SoC") {
                        Column {
                            InfoRow("Cores", report.cpu.cores.toString())
                            InfoRow("Architecture", report.cpu.architecture)
                            InfoRow("Hardware", report.cpu.hardware)
                            InfoRow("Processor", report.cpu.processor)
                            InfoRow("Supported ABIs", report.cpu.supportedAbis.joinToString(", "))
                        }
                    }
                }

                item {
                    InfoCard(title = "GPU") {
                        Column {
                            InfoRow("Renderer", report.gpu.renderer)
                            InfoRow("Vendor", report.gpu.vendor)
                            InfoRow("Version", report.gpu.version)
                        }
                    }
                }

                item {
                    InfoCard(title = "Battery ⚡ (Live)") {
                        Column {
                            InfoRow("Level", "${report.battery.level}%")
                            InfoRow("Status", report.battery.status)
                            InfoRow("Health", report.battery.health)
                            InfoRow("Temperature", String.format(Locale.US, "%.1f °C", report.battery.temperature))
                            InfoRow("Voltage", "${report.battery.voltage} mV")
                            InfoRow("Technology", report.battery.technology)
                            InfoRow("Power Source", report.battery.powerSource)
                        }
                    }
                }

                item {
                    InfoCard(title = "Memory (RAM) ⚡ (Live)") {
                        Column {
                            InfoRow("Total", "${report.memory.totalRamMb} MB")
                            InfoRow("Available", "${report.memory.availableRamMb} MB")
                            InfoRow("Used", "${report.memory.usedRamMb} MB (${report.memory.usagePercent}%)")
                        }
                    }
                }

                item {
                    InfoCard(title = "Network ⚡ (Live)") {
                        Column {
                            InfoRow("Connection", if (report.network.isConnected) "Connected" else "Disconnected")
                            InfoRow("Type", report.network.networkType)
                            InfoRow(
                                "Latency",
                                report.network.latencyMs?.let { "${it} ms" } ?: "—"
                            )
                            InfoRow("Target", report.network.latencyTarget)
                            InfoRow("Status", report.network.latencyStatus)
                        }
                    }
                }

                item {
                    InfoCard(title = "Storage") {
                        Column {
                            InfoRow("Total Internal", String.format(Locale.US, "%.2f GB", report.storage.totalInternalGb))
                            InfoRow("Free", String.format(Locale.US, "%.2f GB", report.storage.freeInternalGb))
                            InfoRow("Used", String.format(Locale.US, "%.2f GB (%d%%)", report.storage.usedInternalGb, report.storage.usagePercent))
                        }
                    }
                }

                item {
                    InfoCard(title = "Display") {
                        Column {
                            InfoRow("Resolution", "${report.display.widthPx} × ${report.display.heightPx}")
                            InfoRow("Density", "${report.display.densityDpi} dpi (×${String.format(Locale.US, "%.2f", report.display.density)})")
                            InfoRow("Refresh Rate", String.format(Locale.US, "%.1f Hz", report.display.refreshRate))
                            InfoRow("Approx. Size", String.format(Locale.US, "%.2f\"", report.display.screenSizeInches))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBadge(isLive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isLive) "● LIVE" else "○ PAUSED",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isLive) "Battery, RAM, network & uptime every 2s" else "Tap play to resume live updates",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
