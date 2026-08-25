package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(report: FullDeviceReport) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Diagnostic") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                InfoCard(title = "Battery") {
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
                InfoCard(title = "Memory (RAM)") {
                    Column {
                        InfoRow("Total", "${report.memory.totalRamMb} MB")
                        InfoRow("Available", "${report.memory.availableRamMb} MB")
                        InfoRow("Used", "${report.memory.usedRamMb} MB (${report.memory.usagePercent}%)")
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
