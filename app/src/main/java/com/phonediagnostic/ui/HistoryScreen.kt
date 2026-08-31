package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.MetricSample
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import com.phonediagnostic.ui.components.MetricChartCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    samples: List<MetricSample>,
    monitorEnabled: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Removes all ${samples.size} stored samples. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh history")
                    }
                    IconButton(
                        onClick = { confirmClear = true },
                        enabled = samples.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear history")
                    }
                }
            )
        }
    ) { padding ->
        if (samples.isEmpty()) {
            EmptyHistory(
                monitorEnabled = monitorEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        val battery = samples.map { it.batteryPct.toFloat() }
        val temperature = samples.map { it.batteryTempC }
        val ram = samples.map { it.ramPercent.toFloat() }
        val latest = samples.last()
        val span = describeSpan(samples)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "battery_chart") {
                MetricChartCard(
                    title = "Battery",
                    currentLabel = "${latest.batteryPct}%",
                    rangeLabel = "${span} - low ${battery.min().toInt()}% / high ${battery.max().toInt()}%",
                    values = battery,
                    lineColor = Color(0xFF43A047),
                    minValue = 0f,
                    maxValue = 100f
                )
            }

            item(key = "temp_chart") {
                MetricChartCard(
                    title = "Battery temperature",
                    currentLabel = String.format(Locale.US, "%.1f C", latest.batteryTempC),
                    rangeLabel = String.format(
                        Locale.US,
                        "%s - low %.1f C / high %.1f C",
                        span,
                        temperature.min(),
                        temperature.max()
                    ),
                    values = temperature,
                    lineColor = Color(0xFFFB8C00)
                )
            }

            item(key = "ram_chart") {
                MetricChartCard(
                    title = "RAM in use",
                    currentLabel = "${latest.ramPercent}%",
                    rangeLabel = "$span - low ${ram.min().toInt()}% / high ${ram.max().toInt()}%",
                    values = ram,
                    lineColor = MaterialTheme.colorScheme.primary,
                    minValue = 0f,
                    maxValue = 100f
                )
            }

            item(key = "summary") {
                InfoCard(title = "Samples") {
                    Column {
                        InfoRow("Stored", samples.size.toString())
                        InfoRow("Oldest", formatClock(samples.first().timestampMs))
                        InfoRow("Newest", formatClock(latest.timestampMs))
                        InfoRow("Covers", span)
                        InfoRow(
                            "Charging now",
                            if (latest.charging) "Yes" else "No"
                        )
                        InfoRow(
                            "Background monitor",
                            if (monitorEnabled) "On - samples every 30s" else "Off"
                        )
                    }
                }
            }

            item(key = "note") {
                Text(
                    text = "Samples are recorded while the app is open, and every 30s when " +
                        "the background monitor is on. Kept on this device only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory(monitorEnabled: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No samples yet",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (monitorEnabled) {
                "The background monitor is on. Trends appear after a few samples."
            } else {
                "Keep the app open for a minute, or turn on the background monitor " +
                    "in Settings to collect samples while it is closed."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

private fun formatClock(timestampMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMs))

private fun describeSpan(samples: List<MetricSample>): String {
    if (samples.size < 2) return "single sample"
    val millis = samples.last().timestampMs - samples.first().timestampMs
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    return when {
        minutes < 1L -> "under a minute"
        minutes < 60L -> "$minutes min"
        else -> {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0L) "${hours}h" else "${hours}h ${rest}m"
        }
    }
}
