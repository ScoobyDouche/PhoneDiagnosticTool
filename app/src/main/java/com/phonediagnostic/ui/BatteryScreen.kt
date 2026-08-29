package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.BatteryInfo
import com.phonediagnostic.data.ThermalZone
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import com.phonediagnostic.ui.components.UsageBar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    battery: BatteryInfo?,
    thermals: List<ThermalZone>,
    isLive: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenThermals: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Battery")
                        Text(
                            text = if (isLive) "Live · every 3s" else "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (battery == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Collecting battery info…")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                InfoCard(title = "Charge") {
                    Column {
                        UsageBar(
                            label = "Level",
                            percent = battery.level.coerceIn(0, 100),
                            detail = "${battery.level}%"
                        )
                        InfoRow("Status", battery.status)
                        InfoRow("Power source", battery.powerSource)
                        InfoRow("Health", battery.health)
                        InfoRow("Technology", battery.technology)
                    }
                }
            }

            item {
                InfoCard(title = "Electrical") {
                    Column {
                        InfoRow(
                            "Temperature",
                            String.format(Locale.US, "%.1f °C", battery.temperature)
                        )
                        InfoRow("Voltage", "${battery.voltage} mV")
                        InfoRow(
                            "Current (now)",
                            battery.currentNowMa?.let { "$it mA" } ?: "Unavailable"
                        )
                        InfoRow(
                            "Current (avg)",
                            battery.currentAvgMa?.let { "$it mA" } ?: "Unavailable"
                        )
                        if (battery.capacityMah != null) {
                            InfoRow("Design capacity", "${battery.capacityMah} mAh")
                        }
                        if (battery.chargeCounterUah != null) {
                            val mah = battery.chargeCounterUah / 1000.0
                            InfoRow(
                                "Charge counter",
                                String.format(Locale.US, "%.0f mAh", mah)
                            )
                        }
                    }
                }
            }

            item {
                InfoCard(
                    title = "Thermals",
                    onClick = onOpenThermals
                ) {
                    Column {
                        InfoRow("Zones", thermals.size.toString())
                        if (thermals.isEmpty()) {
                            Text(
                                text = "No readable zones on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            thermals.take(6).forEach { zone ->
                                val label = zone.type.ifBlank { zone.name }
                                InfoRow(
                                    label.take(28),
                                    String.format(Locale.US, "%.1f °C", zone.tempC)
                                )
                            }
                            if (thermals.size > 6) {
                                Text(
                                    text = "Tap for all ${thermals.size} zones",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
