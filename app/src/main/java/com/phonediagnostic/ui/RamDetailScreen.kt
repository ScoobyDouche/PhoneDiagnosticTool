package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.MemoryInfo
import com.phonediagnostic.data.ProcessRamEntry
import com.phonediagnostic.ui.components.UsageBar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamDetailScreen(
    memory: MemoryInfo?,
    entries: List<ProcessRamEntry>?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ram_title)) },
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
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                IdleRamExplainer(memory = memory)
            }

            if (memory != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.ram_system_memory),
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(modifier = Modifier.height(8.dp))
                            UsageBar(
                                label = stringResource(R.string.label_in_use),
                                percent = memory.usagePercent,
                                detail = "${memory.usedRamMb} / ${memory.totalRamMb} MB"
                            )
                            Text(
                                text = stringResource(
                                    R.string.ram_available_threshold,
                                    memory.availableRamMb,
                                    memory.thresholdMb
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (memory.statusHint.isNotBlank()) {
                                Box(modifier = Modifier.height(6.dp))
                                Text(
                                    text = memory.statusHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (memory.isLowMemory) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color(0xFF2E7D32)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.ram_processes_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            when {
                isLoading && entries == null -> {
                    item {
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

                entries == null || entries.isEmpty() -> {
                    item {
                        Text(
                            text = stringResource(R.string.ram_privacy_blocked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val rows = entries
                    item {
                        Text(
                            text = if (rows.size <= 2) {
                                stringResource(R.string.ram_only_this_app)
                            } else {
                                stringResource(R.string.ram_sorted_pss)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(
                        items = rows,
                        key = { row -> "${row.pid}_${row.processName}" }
                    ) { row ->
                        ProcessRow(row = row)
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleRamExplainer(memory: MemoryInfo?) {
    val high = (memory?.usagePercent ?: 0) >= 70
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1565C0).copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (high) stringResource(R.string.ram_idle_high, memory?.usagePercent ?: 0)
                else stringResource(R.string.ram_how_android),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0D47A1)
            )
            Box(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ram_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProcessRow(row: ProcessRamEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.appLabel, fontWeight = FontWeight.SemiBold)
                Text(
                    text = row.processName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = row.importance,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = String.format(Locale.US, "%.1f MB", row.pssMb),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
