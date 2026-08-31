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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
                title = { Text("Network") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
                InfoCard(title = "Connection") {
                    Column {
                        InfoRow(
                            "State",
                            if (network?.isConnected == true) "Connected" else "Disconnected"
                        )
                        InfoRow("Transport", network?.networkType ?: "Unknown")
                        InfoRow("Validated", if (network?.validated == true) "Yes" else "No")
                        InfoRow("Metered", if (network?.metered == true) "Yes" else "No")
                        if (network?.downstreamMbps != null || network?.upstreamMbps != null) {
                            InfoRow(
                                "Link bandwidth",
                                "${network.downstreamMbps ?: "?"} down / " +
                                    "${network.upstreamMbps ?: "?"} up Mbps"
                            )
                        }
                        detail?.interfaceName?.takeIf { it.isNotBlank() }?.let {
                            InfoRow("Interface", it)
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
                    InfoCard(title = "Wi-Fi") {
                        Column {
                            InfoRow("Network", wifi.ssid)
                            InfoRow("Band", wifi.band)
                            wifi.frequencyMhz?.let { InfoRow("Frequency", "$it MHz") }
                            wifi.linkSpeedMbps?.let { InfoRow("Link speed", "$it Mbps") }
                            wifi.txLinkSpeedMbps?.let { InfoRow("Tx rate", "$it Mbps") }
                            wifi.rxLinkSpeedMbps?.let { InfoRow("Rx rate", "$it Mbps") }
                            wifi.rssiDbm?.let {
                                InfoRow("Signal", "$it dBm (${wifi.signalLevel}/4)")
                            }
                        }
                    }
                }
            }

            detail?.cellular?.let { cell ->
                item(key = "cellular") {
                    InfoCard(title = "Cellular") {
                        Column {
                            InfoRow("Carrier", cell.carrier)
                            if (cell.simOperator.isNotBlank()) {
                                InfoRow("SIM operator", cell.simOperator)
                            }
                            if (cell.countryIso.isNotBlank()) {
                                InfoRow("Country", cell.countryIso)
                            }
                            InfoRow("Radio", cell.phoneType)
                            InfoRow("Roaming", if (cell.roaming) "Yes" else "No")
                        }
                    }
                }
            }

            item(key = "latency") {
                InfoCard(title = "Latency") {
                    Column {
                        if (!probeEnabled) {
                            Text(
                                text = "The latency check is switched off in Settings. " +
                                    "Nothing on this screen contacts the network while it is off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            InfoRow("Target", latency?.target ?: network?.latencyTarget ?: "—")
                            if (latency != null) {
                                InfoRow("Samples", "${latency.samplesMs.size} of ${latency.attempts}")
                                InfoRow("Min", latency.minMs?.let { "$it ms" } ?: "—")
                                InfoRow("Average", latency.avgMs?.let { "$it ms" } ?: "—")
                                InfoRow("Max", latency.maxMs?.let { "$it ms" } ?: "—")
                                InfoRow("Jitter", latency.jitterMs?.let { "$it ms" } ?: "—")
                                InfoRow("Loss", "${latency.lossPercent}%")
                                latency.lastError?.let { InfoRow("Error", it) }
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
                                    "Last single probe",
                                    network?.latencyMs?.let { "$it ms" } ?: "—"
                                )
                                Text(
                                    text = "Run a burst of probes to see spread and packet loss.",
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
                                Text(if (latencyRunning) "Measuring…" else "Run 5 probes")
                            }
                        }
                    }
                }
            }

            if (detail != null) {
                item(key = "dns") {
                    InfoCard(title = "DNS") {
                        Column {
                            if (detail.dnsServers.isEmpty()) {
                                Text(
                                    text = "No DNS servers reported for the active network.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                detail.dnsServers.forEachIndexed { index, server ->
                                    InfoRow("Server ${index + 1}", server)
                                }
                            }
                            InfoRow(
                                "Private DNS",
                                when {
                                    detail.privateDnsServer != null ->
                                        "On - ${detail.privateDnsServer}"
                                    detail.privateDnsActive -> "On - automatic"
                                    else -> "Off"
                                }
                            )
                            if (detail.domains.isNotBlank()) {
                                InfoRow("Search domains", detail.domains)
                            }
                        }
                    }
                }

                item(key = "capabilities") {
                    InfoCard(title = "Capabilities") {
                        Column {
                            if (detail.capabilities.isEmpty()) {
                                Text(
                                    text = "None reported.",
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
                        text = "Interfaces (${detail.interfaces.size})",
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
                    InfoCard(title = nif.name.ifBlank { "Interface" }) {
                        Column {
                            if (nif.displayName.isNotBlank() && nif.displayName != nif.name) {
                                InfoRow("Description", nif.displayName)
                            }
                            InfoRow("State", if (nif.isUp) "Up" else "Down")
                            if (nif.mtu > 0) InfoRow("MTU", nif.mtu.toString())
                            nif.addresses.forEach { address ->
                                val parts = address.split("  ", limit = 2)
                                InfoRow(
                                    parts.getOrElse(0) { "Address" },
                                    parts.getOrElse(1) { address }
                                )
                            }
                        }
                    }
                }

                item(key = "privacy_note") {
                    Text(
                        text = "Addresses are read from this device's own interfaces. " +
                            "Nothing here is uploaded. MAC addresses are not shown — " +
                            "Android randomises them per network anyway.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
