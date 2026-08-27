package com.phonediagnostic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.AppStorageEntry
import com.phonediagnostic.data.StorageInfo
import com.phonediagnostic.data.StorageVolumeInfo
import com.phonediagnostic.ui.components.UsageBar
import java.util.Locale

private enum class StorageFilter {
    ALL,
    SAFE_CACHE,
    USER_APPS,
    LARGE
}

private enum class CleanupRisk {
    SAFE_CACHE,
    REVIEW,
    SYSTEM
}

private const val CACHE_SAFE_BYTES = 50L * 1024 * 1024
private const val LARGE_APP_BYTES = 500L * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDetailScreen(
    storageOverview: StorageInfo?,
    entries: List<AppStorageEntry>?,
    isLoading: Boolean,
    hasPermission: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onUninstallApp: (String) -> Unit
) {
    var selected by remember { mutableStateOf<AppStorageEntry?>(null) }
    var pendingUninstall by remember { mutableStateOf<AppStorageEntry?>(null) }
    var filter by remember { mutableStateOf(StorageFilter.SAFE_CACHE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
                Text(
                    text = "Volumes & partitions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (storageOverview != null) {
                item {
                    OverviewCard(storageOverview)
                }
                items(
                    items = storageOverview.volumes,
                    key = { "${it.name}_${it.path}" }
                ) { vol ->
                    VolumeCard(vol)
                }
                item {
                    PathCard(storageOverview)
                }
            } else {
                item {
                    Text(
                        text = "Loading volume info…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Apps (cleanup)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when {
                !hasPermission -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Usage access needed for per-app sizes",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Volumes above work without it. Grant access to list apps and clean safely.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(modifier = Modifier.height(12.dp))
                                Button(onClick = onRequestPermission) {
                                    Text("Open Usage Access settings")
                                }
                            }
                        }
                    }
                }

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
                            text = "No per-app storage data returned.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onRefresh) { Text("Try again") }
                    }
                }

                else -> {
                    val all = entries
                    val safeCacheTotal = all
                        .filter { it.cacheBytes >= CACHE_SAFE_BYTES }
                        .sumOf { it.cacheBytes }
                    val filtered = when (filter) {
                        StorageFilter.ALL -> all
                        StorageFilter.SAFE_CACHE ->
                            all.filter { it.cacheBytes >= CACHE_SAFE_BYTES }
                                .sortedByDescending { it.cacheBytes }
                        StorageFilter.USER_APPS -> all.filter { !it.isSystemApp }
                        StorageFilter.LARGE -> all.filter { it.totalBytes >= LARGE_APP_BYTES }
                    }

                    item { SafetyBanner(safeCacheTotalBytes = safeCacheTotal) }
                    item {
                        FilterRow(selected = filter, onSelect = { filter = it })
                    }
                    item {
                        Text(
                            text = when (filter) {
                                StorageFilter.SAFE_CACHE ->
                                    "Large cache (≥50 MB). Clearing cache is usually safe."
                                StorageFilter.USER_APPS ->
                                    "Apps you installed. Uninstall removes app + data."
                                StorageFilter.LARGE ->
                                    "≥500 MB total. Check data before uninstall."
                                StorageFilter.ALL ->
                                    "All apps. Prefer clear cache over uninstall."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "Nothing matches this filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filtered, key = { it.packageName }) { row ->
                            StorageRow(
                                row = row,
                                risk = riskFor(row),
                                onClick = { selected = row }
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { app ->
        val risk = riskFor(app)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(app.appLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(modifier = Modifier.height(8.dp))
                Text(
                    "App ${formatBytes(app.appBytes)} · Data ${formatBytes(app.dataBytes)} · Cache ${formatBytes(app.cacheBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Box(modifier = Modifier.height(8.dp))
                RiskHint(risk)
                Box(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                TextButton(
                    onClick = {
                        onOpenAppInfo(app.packageName)
                        selected = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Box(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(
                        if (app.cacheBytes >= CACHE_SAFE_BYTES)
                            "Clear cache in App info (recommended)"
                        else
                            "Open App info (clear cache / storage)"
                    )
                }
                if (!app.isSystemApp) {
                    TextButton(
                        onClick = {
                            pendingUninstall = app
                            selected = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Box(modifier = Modifier.padding(horizontal = 8.dp))
                        Text("Uninstall…", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    pendingUninstall?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text("Uninstall ${app.appLabel}?") },
            text = {
                Column {
                    Text(
                        "Removes the app and its data (${formatBytes(app.dataBytes)} data + ${formatBytes(app.cacheBytes)} cache)."
                    )
                    Box(modifier = Modifier.height(8.dp))
                    Text(
                        "If unsure, cancel and clear cache instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUninstallApp(app.packageName)
                    pendingUninstall = null
                }) { Text("Uninstall", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun OverviewCard(s: StorageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Internal data partition", fontWeight = FontWeight.SemiBold)
            Box(modifier = Modifier.height(8.dp))
            UsageBar(
                label = "Used",
                percent = s.usagePercent,
                detail = String.format(Locale.US, "%.2f / %.2f GB", s.usedInternalGb, s.totalInternalGb)
            )
            Text(
                text = String.format(Locale.US, "Free %.2f GB · %d volume(s)", s.freeInternalGb, s.volumes.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VolumeCard(vol: StorageVolumeInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(vol.name, fontWeight = FontWeight.SemiBold)
            Text(vol.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(vol.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.height(6.dp))
            if (vol.totalBytes > 0) {
                UsageBar(
                    label = "Used",
                    percent = vol.usagePercent,
                    detail = String.format(Locale.US, "%.2f / %.2f GB", vol.usedGb, vol.totalGb)
                )
                Text(
                    text = String.format(Locale.US, "Free %.2f GB · State: %s", vol.freeGb, vol.state),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "State: ${vol.state} (size unavailable)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PathCard(s: StorageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Paths & flags", fontWeight = FontWeight.SemiBold)
            Box(modifier = Modifier.height(6.dp))
            Text("Data: ${s.dataDirectory}", style = MaterialTheme.typography.labelSmall)
            Text("App files: ${s.filesDirectory}", style = MaterialTheme.typography.labelSmall)
            Text("App cache: ${s.cacheDirectory}", style = MaterialTheme.typography.labelSmall)
            Text(
                "External: ${s.externalStorageState}" +
                    if (s.emulatedExternal) " (emulated)" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SafetyBanner(safeCacheTotalBytes: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Safe first: clear cache", fontWeight = FontWeight.SemiBold, color = Color(0xFF1B5E20))
            Box(modifier = Modifier.height(4.dp))
            Text(
                text = if (safeCacheTotalBytes > 0) {
                    "About ${formatBytes(safeCacheTotalBytes)} in large caches."
                } else {
                    "No large caches found."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilterRow(selected: StorageFilter, onSelect: (StorageFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selected == StorageFilter.SAFE_CACHE, onClick = { onSelect(StorageFilter.SAFE_CACHE) }, label = { Text("Safe cache") })
        FilterChip(selected = selected == StorageFilter.USER_APPS, onClick = { onSelect(StorageFilter.USER_APPS) }, label = { Text("My apps") })
        FilterChip(selected = selected == StorageFilter.LARGE, onClick = { onSelect(StorageFilter.LARGE) }, label = { Text("Large") })
        FilterChip(selected = selected == StorageFilter.ALL, onClick = { onSelect(StorageFilter.ALL) }, label = { Text("All") })
    }
}

@Composable
private fun RiskHint(risk: CleanupRisk) {
    val (label, color) = when (risk) {
        CleanupRisk.SAFE_CACHE -> "Safe: large cache" to Color(0xFF2E7D32)
        CleanupRisk.REVIEW -> "Review before uninstall" to Color(0xFFF9A825)
        CleanupRisk.SYSTEM -> "System app" to MaterialTheme.colorScheme.error
    }
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = color)
}

@Composable
private fun StorageRow(row: AppStorageEntry, risk: CleanupRisk, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.appLabel, fontWeight = FontWeight.SemiBold)
                    Text(row.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatBytes(row.totalBytes), fontWeight = FontWeight.Medium)
            }
            Box(modifier = Modifier.height(4.dp))
            RiskHint(risk)
            Text(
                "App ${formatBytes(row.appBytes)} · Data ${formatBytes(row.dataBytes)} · Cache ${formatBytes(row.cacheBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun riskFor(entry: AppStorageEntry): CleanupRisk {
    if (entry.isSystemApp) return CleanupRisk.SYSTEM
    if (entry.cacheBytes >= CACHE_SAFE_BYTES) return CleanupRisk.SAFE_CACHE
    return CleanupRisk.REVIEW
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb)
    else String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
}
