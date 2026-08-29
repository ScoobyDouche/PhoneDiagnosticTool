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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.CpuInfo
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuScreen(
    cpu: CpuInfo?,
    isLive: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CPU / SoC")
                        Text(
                            text = if (isLive) "Live · freqs every 3s" else "Paused",
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
        if (cpu == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Collecting CPU info…")
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
                InfoCard(title = "Processor") {
                    Column {
                        InfoRow("Processor", cpu.processor)
                        InfoRow("Hardware", cpu.hardware)
                        InfoRow("Board / Platform", cpu.boardPlatform)
                        InfoRow("Architecture", cpu.architecture)
                        InfoRow("Cores", cpu.cores.toString())
                        InfoRow("ABIs", cpu.supportedAbis.joinToString(", "))
                    }
                }
            }

            item {
                InfoCard(title = "Frequency range") {
                    Column {
                        val min = cpu.minFreqMhz?.toString() ?: "?"
                        val max = cpu.maxFreqMhz?.toString() ?: "?"
                        InfoRow("Min – Max", "$min – $max MHz")
                        if (cpu.currentFreqMhz.isNotEmpty()) {
                            val avg = cpu.currentFreqMhz.average()
                            InfoRow("Average (online)", String.format("%.0f MHz", avg))
                        }
                    }
                }
            }

            if (cpu.currentFreqMhz.isNotEmpty()) {
                item {
                    InfoCard(title = "Live core clocks") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            cpu.currentFreqMhz.forEachIndexed { index, mhz ->
                                CoreFreqRow(
                                    coreIndex = index,
                                    mhz = mhz,
                                    maxMhz = cpu.maxFreqMhz
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    InfoCard(title = "Live core clocks") {
                        Text(
                            text = "Current frequencies not exposed via /sys on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreFreqRow(coreIndex: Int, mhz: Int, maxMhz: Int?) {
    val fraction = if (maxMhz != null && maxMhz > 0) {
        (mhz.toFloat() / maxMhz).coerceIn(0f, 1f)
    } else {
        0.5f
    }
    val barColor = when {
        fraction >= 0.9f -> Color(0xFFE53935)
        fraction >= 0.7f -> Color(0xFFFB8C00)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Core $coreIndex",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$mhz MHz",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}
