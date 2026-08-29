package com.phonediagnostic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    report: FullDeviceReport?,
    versionName: String,
    onOpenRam: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenThermals: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("More") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (report != null) {
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
                    InfoCard(title = "Display") {
                        Column {
                            InfoRow(
                                "Resolution",
                                "${report.display.widthPx} × ${report.display.heightPx}"
                            )
                            InfoRow(
                                "Density",
                                "${report.display.densityDpi} dpi"
                            )
                            InfoRow(
                                "Refresh",
                                String.format(Locale.US, "%.1f Hz", report.display.refreshRate)
                            )
                            InfoRow(
                                "Size",
                                String.format(Locale.US, "%.2f\"", report.display.screenSizeInches)
                            )
                        }
                    }
                }

                item {
                    InfoCard(title = "Network") {
                        Column {
                            InfoRow(
                                "Connection",
                                if (report.network.isConnected) "Connected" else "Disconnected"
                            )
                            InfoRow("Type", report.network.networkType)
                            InfoRow(
                                "Latency",
                                report.network.latencyMs?.let { "$it ms" } ?: "—"
                            )
                            if (report.network.downstreamMbps != null ||
                                report.network.upstreamMbps != null
                            ) {
                                val down = report.network.downstreamMbps?.toString() ?: "?"
                                val up = report.network.upstreamMbps?.toString() ?: "?"
                                InfoRow("Bandwidth", "$down ↓ / $up ↑ Mbps")
                            }
                            InfoRow("Validated", if (report.network.validated) "Yes" else "No")
                            InfoRow("Metered", if (report.network.metered) "Yes" else "No")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Sections",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            item {
                MoreLink(
                    icon = Icons.Outlined.Memory,
                    title = "RAM detail",
                    subtitle = report?.memory?.let {
                        "${it.usedRamMb} / ${it.totalRamMb} MB"
                    } ?: "Process memory",
                    onClick = onOpenRam
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Storage,
                    title = "Storage",
                    subtitle = report?.storage?.let {
                        String.format(Locale.US, "%.1f GB free", it.freeInternalGb)
                    } ?: "Volumes & apps",
                    onClick = onOpenStorage
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Thermostat,
                    title = "All thermal zones",
                    subtitle = "${report?.thermals?.size ?: 0} zones",
                    onClick = onOpenThermals
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Build,
                    title = "Tools",
                    subtitle = "Load test, display, vibrate, log",
                    onClick = onOpenTools
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Settings,
                    title = "Settings",
                    subtitle = "Theme, monitor, network probe",
                    onClick = onOpenSettings
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Info,
                    title = "About",
                    subtitle = "v$versionName · privacy-first",
                    onClick = onOpenAbout
                )
            }
        }
    }
}

@Composable
private fun MoreLink(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
