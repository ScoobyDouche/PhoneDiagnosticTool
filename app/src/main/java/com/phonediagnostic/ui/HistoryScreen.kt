package com.phonediagnostic.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
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
    val context = LocalContext.current

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body, samples.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.history_refresh_cd))
                    }
                    IconButton(
                        onClick = { confirmClear = true },
                        enabled = samples.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_clear_cd))
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
        val span = describeSpan(samples, context)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "battery_chart") {
                MetricChartCard(
                    title = stringResource(R.string.history_chart_battery),
                    currentLabel = "${latest.batteryPct}%",
                    rangeLabel = stringResource(
                        R.string.history_range_pct,
                        span,
                        battery.min().toInt(),
                        battery.max().toInt()
                    ),
                    values = battery,
                    lineColor = Color(0xFF43A047),
                    minValue = 0f,
                    maxValue = 100f
                )
            }

            item(key = "temp_chart") {
                MetricChartCard(
                    title = stringResource(R.string.history_chart_temp),
                    currentLabel = String.format(Locale.US, "%.1f C", latest.batteryTempC),
                    rangeLabel = stringResource(
                        R.string.history_range_temp,
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
                    title = stringResource(R.string.history_chart_ram),
                    currentLabel = "${latest.ramPercent}%",
                    rangeLabel = stringResource(
                        R.string.history_range_pct,
                        span,
                        ram.min().toInt(),
                        ram.max().toInt()
                    ),
                    values = ram,
                    lineColor = MaterialTheme.colorScheme.primary,
                    minValue = 0f,
                    maxValue = 100f
                )
            }

            item(key = "summary") {
                InfoCard(title = stringResource(R.string.history_section_samples)) {
                    Column {
                        InfoRow(stringResource(R.string.label_stored), samples.size.toString())
                        InfoRow(stringResource(R.string.label_oldest), formatClock(samples.first().timestampMs))
                        InfoRow(stringResource(R.string.label_newest), formatClock(latest.timestampMs))
                        InfoRow(stringResource(R.string.label_covers), span)
                        InfoRow(
                            stringResource(R.string.label_charging_now),
                            if (latest.charging) stringResource(R.string.yes) else stringResource(R.string.no)
                        )
                        InfoRow(
                            stringResource(R.string.label_background_monitor),
                            if (monitorEnabled) stringResource(R.string.history_monitor_on)
                            else stringResource(R.string.history_monitor_off)
                        )
                    }
                }
            }

            item(key = "note") {
                Text(
                    text = stringResource(R.string.history_note),
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
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (monitorEnabled) {
                stringResource(R.string.history_empty_monitor_on)
            } else {
                stringResource(R.string.history_empty_monitor_off)
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

private fun describeSpan(samples: List<MetricSample>, context: Context): String {
    if (samples.size < 2) return context.getString(R.string.history_span_single)
    val millis = samples.last().timestampMs - samples.first().timestampMs
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    return when {
        minutes < 1L -> context.getString(R.string.history_span_under_minute)
        minutes < 60L -> context.getString(R.string.history_span_minutes, minutes.toInt())
        else -> {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0L) context.getString(R.string.history_span_hours, hours.toInt())
            else context.getString(R.string.history_span_hours_minutes, hours.toInt(), rest.toInt())
        }
    }
}
