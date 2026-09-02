package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
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
    onOpenThermals: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.battery_title))
                        Text(
                            text = if (isLive) {
                                stringResource(R.string.battery_live_status)
                            } else {
                                stringResource(R.string.state_paused)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
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
                Text(stringResource(R.string.battery_collecting))
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
                InfoCard(title = stringResource(R.string.battery_section_charge)) {
                    Column {
                        UsageBar(
                            label = stringResource(R.string.label_level),
                            percent = battery.level.coerceIn(0, 100),
                            detail = "${battery.level}%"
                        )
                        InfoRow(stringResource(R.string.label_status), battery.status)
                        InfoRow(stringResource(R.string.label_power_source), battery.powerSource)
                        InfoRow(stringResource(R.string.label_health), battery.health)
                        InfoRow(stringResource(R.string.label_technology), battery.technology)
                    }
                }
            }

            item {
                InfoCard(title = stringResource(R.string.battery_section_electrical)) {
                    Column {
                        InfoRow(
                            stringResource(R.string.label_temperature),
                            String.format(Locale.US, "%.1f °C", battery.temperature)
                        )
                        InfoRow(stringResource(R.string.label_voltage), "${battery.voltage} mV")
                        InfoRow(
                            stringResource(R.string.label_current_now),
                            battery.currentNowMa?.let { "$it mA" }
                                ?: stringResource(R.string.unavailable)
                        )
                        InfoRow(
                            stringResource(R.string.label_current_avg),
                            battery.currentAvgMa?.let { "$it mA" }
                                ?: stringResource(R.string.unavailable)
                        )
                        if (battery.capacityMah != null) {
                            InfoRow(
                                stringResource(R.string.label_design_capacity),
                                "${battery.capacityMah} mAh"
                            )
                        }
                        if (battery.chargeCounterUah != null) {
                            val mah = battery.chargeCounterUah / 1000.0
                            InfoRow(
                                stringResource(R.string.label_charge_counter),
                                String.format(Locale.US, "%.0f mAh", mah)
                            )
                        }
                    }
                }
            }

            item {
                InfoCard(
                    title = stringResource(R.string.section_trends),
                    subtitle = stringResource(R.string.history_chevron),
                    onClick = onOpenHistory
                ) {
                    Text(
                        text = stringResource(R.string.battery_trends_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                InfoCard(
                    title = stringResource(R.string.battery_section_thermals),
                    subtitle = stringResource(R.string.thermals_all_zones),
                    onClick = onOpenThermals
                ) {
                    Column {
                        InfoRow(
                            stringResource(R.string.label_zones),
                            thermals.size.toString()
                        )
                        if (thermals.isEmpty()) {
                            Text(
                                text = stringResource(R.string.thermals_none),
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
                                    text = stringResource(R.string.thermals_tap_all, thermals.size),
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
