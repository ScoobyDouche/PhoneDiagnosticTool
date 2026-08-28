package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.DiagnosticLog
import com.phonediagnostic.data.LoadTestProgress
import com.phonediagnostic.data.LoadTestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    logLines: List<String>,
    loadTesting: Boolean,
    loadProgress: LoadTestProgress?,
    lastLoadResult: LoadTestResult?,
    onBack: () -> Unit,
    onRefreshLog: () -> Unit,
    onClearLog: () -> Unit,
    onRunLoadTest: (durationSec: Int) -> Unit
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !loadTesting) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshLog, enabled = !loadTesting) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh log")
                    }
                    IconButton(onClick = onClearLog, enabled = !loadTesting) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear log")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Load test", fontWeight = FontWeight.SemiBold)
                        Box(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CPU stress test. Phone may warm up and drain battery. " +
                                "Live stats update while running.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(modifier = Modifier.height(12.dp))

                        if (loadTesting && loadProgress != null) {
                            LiveLoadPanel(progress = loadProgress)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onRunLoadTest(60) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !loadTesting
                                ) { Text("1 min") }
                                OutlinedButton(
                                    onClick = { onRunLoadTest(300) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !loadTesting
                                ) { Text("5 min") }
                                Button(
                                    onClick = { onRunLoadTest(600) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !loadTesting
                                ) { Text("10 min") }
                            }
                            if (lastLoadResult != null) {
                                Box(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Last result",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lastLoadResult.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rotating log", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${logLines.size} / ${DiagnosticLog.MAX_ENTRIES}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Max ~0.5–1 MB when full. Oldest lines drop. Background monitor adds a line every 30s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (logLines.isEmpty()) {
                item {
                    Text(
                        text = "Log is empty. Enable background monitor in Settings or run a load test.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(
                    items = logLines.asReversed(),
                    key = { index, line -> "$index-$line" }
                ) { _, line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveLoadPanel(progress: LoadTestProgress) {
    val elapsedLabel = formatClock(progress.elapsedSec)
    val totalLabel = formatClock(progress.durationSec)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = progress.phase,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Box(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        Box(modifier = Modifier.height(6.dp))
        Text(
            text = "$elapsedLabel / $totalLabel",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace
        )
        Box(modifier = Modifier.height(12.dp))
        Text("Live stats", fontWeight = FontWeight.SemiBold)
        Box(modifier = Modifier.height(6.dp))
        MetricRow("RAM used", "${progress.ramUsedMb} MB")
        MetricRow("Battery", "${progress.batteryPct}%")
        MetricRow(
            "Temperature",
            String.format("%.1f °C", progress.tempC)
        )
        MetricRow("CPU ops", formatOps(progress.operations))
        MetricRow("Threads", "4 active")
        Box(modifier = Modifier.height(8.dp))
        Text(
            text = "Keep this screen open. UI may feel slower under full CPU load.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatClock(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}

private fun formatOps(ops: Long): String {
    return when {
        ops >= 1_000_000_000L -> String.format("%.2fB", ops / 1_000_000_000.0)
        ops >= 1_000_000L -> String.format("%.1fM", ops / 1_000_000.0)
        ops >= 1_000L -> String.format("%.1fK", ops / 1_000.0)
        else -> ops.toString()
    }
}
