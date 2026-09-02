package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.LatencyStats
import com.phonediagnostic.data.NetworkDetail
import com.phonediagnostic.data.NetworkInfo
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(
    network: NetworkInfo?,
    detail: NetworkDetail?,
    isLoading: Boolean,
    probeEnabled: Boolean,
    latency: LatencyStats?,
    latencyRunning: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRunLatency: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.network_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "connection") {
                InfoCard(title = stringResource(R.string.network_section_connection)) {
                    Column {
                        InfoRow(
                            stringResource(R.string.label_state),
                            if (network?.isConnected == true) {
                                stringResource(R.string.connection_connected)
                            } else {
                                stringResource(R.string.connection_disconnected)
                            }
                        )
                        InfoRow(
                            stringResource(R.string.label_transport),
                            network?.networkType ?: stringResource(R.string.unknown)
                        )
                        InfoRow(
                            stringResource(R.string.label_validated),
                            if (network?.validated == true) {
                                stringResource(R.string.yes)
                            } else {
                                stringResource(R.string.no)
                            }
                        )
                        InfoRow(
                            stringResource(R.string.label_metered),
                            if (network?.metered == true) {
                                stringResource(R.string.yes)
                            } else {
                                stringResource(R.string.no)
                            }
                        )
                        if (network?.downstreamMbps != null || network?.upstreamMbps != null) {
                            InfoRow(
                                stringResource(R.string.label_link_bandwidth),
                                "${network.downstreamMbps ?: "?"} down / " +
                                    "${network.upstreamMbps ?: "?"} up Mbps"
                            )
                        }
                        detail?.interfaceName?.takeIf { it.isNotBlank() }?.let {
                            InfoRow(stringResource(R.string.label_interface), it)
                        }
                    }
                }
            }

            if (isLoading && detail == null) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            detail?.wifi?.let { wifi ->
                item(key = "wifi") {
                    InfoCard(title = stringResource(R.string.network_section_wifi)) {
                        Column {
                            InfoRow(stringResource(R.string.label_network_name), wifi.ssid)
                            InfoRow(stringResource(R.string.label_band), wifi.band)
                            wifi.frequencyMhz?.let {
                                InfoRow(stringResource(R.string.label_frequency), "$it MHz")
                            }
                            wifi.linkSpeedMbps?.let {
                                InfoRow(stringResource(R.string.label_link_speed), "$it Mbps")
                            }
                            wifi.txLinkSpeedMbps?.let {
                                InfoRow(stringResource(R.string.label_tx_rate), "$it Mbps")
                            }
                            wifi.rxLinkSpeedMbps?.let {
                                InfoRow(stringResource(R.string.label_rx_rate), "$it Mbps")
                            }
                            wifi.rssiDbm?.let {
                                InfoRow(
                                    stringResource(R.string.label_signal),
                                    "$it dBm (${wifi.signalLevel}/4)"
                                )
                            }
                        }
                    }
                }
            }

            detail?.cellular?.let { cell ->
                item(key = "cellular") {
                    InfoCard(title = stringResource(R.string.network_section_cellular)) {
                        Column {
                            InfoRow(stringResource(R.string.label_carrier), cell.carrier)
                            if (cell.simOperator.isNotBlank()) {
                                InfoRow(
                                    stringResource(R.string.label_sim_operator),
                                    cell.simOperator
                                )
                            }
                            if (cell.countryIso.isNotBlank()) {
                                InfoRow(
                                    stringResource(R.string.label_country),
                                    cell.countryIso
                                )
                            }
                            InfoRow(stringResource(R.string.label_radio), cell.phoneType)
                            InfoRow(
                                stringResource(R.string.label_roaming),
                                if (cell.roaming) {
                                    stringResource(R.string.yes)
                                } else {
                                    stringResource(R.string.no)
                                }
                            )
                        }
                    }
                }
            }

            item(key = "latency") {
                InfoCard(title = stringResource(R.string.network_section_latency)) {
                    Column {
                        if (!probeEnabled) {
                            Text(
                                text = stringResource(R.string.network_latency_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            InfoRow(
                                stringResource(R.string.label_target),
                                latency?.target ?: network?.latencyTarget ?: "—"
                            )
                            if (latency != null) {
                                InfoRow(
                                    stringResource(R.string.label_samples),
                                    "${latency.samplesMs.size} of ${latency.attempts}"
                                )
                                InfoRow(
                                    stringResource(R.string.label_min),
                                    latency.minMs?.let { "$it ms" } ?: "—"
                                )
                                InfoRow(
                                    stringResource(R.string.label_average),
                                    latency.avgMs?.let { "$it ms" } ?: "—"
                                )
                                InfoRow(
                                    stringResource(R.string.label_max),
                                    latency.maxMs?.let { "$it ms" } ?: "—"
                                )
                                InfoRow(
                                    stringResource(R.string.label_jitter),
                                    latency.jitterMs?.let { "$it ms" } ?: "—"
                                )
                                InfoRow(
                                    stringResource(R.string.label_loss),
                                    "${latency.lossPercent}%"
                                )
                                latency.lastError?.let {
                                    InfoRow(stringResource(R.string.label_error), it)
                                }
                                if (latency.samplesMs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = latency.samplesMs.joinToString(" · ") { "$it" } + " ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                InfoRow(
                                    stringResource(R.string.label_last_single_probe),
                                    network?.latencyMs?.let { "$it ms" } ?: "—"
                                )
                                Text(
                                    text = stringResource(R.string.network_latency_burst_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRunLatency,
                                enabled = !latencyRunning,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (latencyRunning) {
                                        stringResource(R.string.network_measuring)
                                    } else {
                                        stringResource(R.string.network_run_probes)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (detail != null) {
                item(key = "dns") {
                    InfoCard(title = stringResource(R.string.network_section_dns)) {
                        Column {
                            if (detail.dnsServers.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.network_no_dns),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                detail.dnsServers.forEachIndexed { index, server ->
                                    InfoRow(
                                        stringResource(R.string.label_server_n, index + 1),
                                        server
                                    )
                                }
                            }
                            InfoRow(
                                stringResource(R.string.label_private_dns),
                                when {
                                    detail.privateDnsServer != null ->
                                        stringResource(
                                            R.string.private_dns_on_named,
                                            detail.privateDnsServer
                                        )
                                    detail.privateDnsActive ->
                                        stringResource(R.string.private_dns_on_auto)
                                    else -> stringResource(R.string.private_dns_off)
                                }
                            )
                            if (detail.domains.isNotBlank()) {
                                InfoRow(
                                    stringResource(R.string.label_search_domains),
                                    detail.domains
                                )
                            }
                        }
                    }
                }

                item(key = "capabilities") {
                    InfoCard(title = stringResource(R.string.network_section_capabilities)) {
                        Column {
                            if (detail.capabilities.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.none_reported),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = detail.capabilities.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                item(key = "interfaces_header") {
                    Text(
                        text = stringResource(
                            R.string.network_interfaces_header,
                            detail.interfaces.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                items(
                    count = detail.interfaces.size,
                    key = { index -> "nif-${detail.interfaces[index].name}-$index" }
                ) { index ->
                    val nif = detail.interfaces[index]
                    InfoCard(
                        title = nif.name.ifBlank {
                            stringResource(R.string.interface_fallback)
                        }
                    ) {
                        Column {
                            if (nif.displayName.isNotBlank() && nif.displayName != nif.name) {
                                InfoRow(
                                    stringResource(R.string.label_description),
                                    nif.displayName
                                )
                            }
                            InfoRow(
                                stringResource(R.string.label_state),
                                if (nif.isUp) {
                                    stringResource(R.string.state_up)
                                } else {
                                    stringResource(R.string.state_down)
                                }
                            )
                            if (nif.mtu > 0) {
                                InfoRow(
                                    stringResource(R.string.label_mtu),
                                    nif.mtu.toString()
                                )
                            }
                            nif.addresses.forEach { address ->
                                val parts = address.split("  ", limit = 2)
                                InfoRow(
                                    parts.getOrElse(0) {
                                        stringResource(R.string.label_address)
                                    },
                                    parts.getOrElse(1) { address }
                                )
                            }
                        }
                    }
                }

                item(key = "privacy_note") {
                    Text(
                        text = stringResource(R.string.network_privacy_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
