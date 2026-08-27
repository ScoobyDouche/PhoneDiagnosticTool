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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextField
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
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val volumes = storageOverview?.volumes.orEmpty()
    val overview = storageOverview

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh"
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
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (overview != null) {
                item(key = "overview") {
                    OverviewCard(s = overview)
                }
                // Only list extra volume cards if more than one distinct volume
                if (volumes.size > 1) {
                    item(key = "vol_header") {
                        Text(
                            text = "Other volumes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(
                        items = volumes,
                        key = { vol -> vol.path + vol.name }
                    ) { vol ->
                        VolumeCard(vol = vol)
                    }
                } else if (volumes.size == 1) {
                    item(key = "single_vol_meta") {
                        val v = volumes.first()
                        Text(
                            text = "${v.name} · ${v.path} · ${v.state}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item(key = "vol_loading") {
                    Text(
                        text = "Loading storage…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "apps_header") {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            appCleanupItems(
                hasPermission = hasPermission,
                isLoading = isLoading,
                entries = entries,
                filter = filter,
                query = query,
                onQueryChange = { query = it },
                onFilterChange = { filter = it },
                onRequestPermission = onRequestPermission,
                onRefresh = onRefresh,
                onSelect = { selected = it }
            )
        }
    }

    val selectedApp = selected
    if (selectedApp != null) {
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
                Text(
                    text = selectedApp.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = selectedApp.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.height(10.dp))
                Text(
                    text = "App ${formatBytes(selectedApp.appBytes)} · Data ${formatBytes(selectedApp.dataBytes)} · Cache ${formatBytes(selectedApp.cacheBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Box(modifier = Modifier.height(8.dp))
                RiskHint(risk = riskFor(selectedApp))
                Box(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                TextButton(
                    onClick = {
                        onOpenAppInfo(selectedApp.packageName)
                        selected = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = null)
                    Box(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(
                        text = if (selectedApp.cacheBytes >= CACHE_SAFE_BYTES) {
                            "Clear cache in App info (recommended)"
                        } else {
                            "Open App info"
                        }
                    )
                }
                if (!selectedApp.isSystemApp) {
                    TextButton(
                        onClick = {
                            pendingUninstall = selectedApp
                            selected = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Box(modifier = Modifier.padding(horizontal = 8.dp))
                        Text(
                            text = "Uninstall…",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    val uninstallTarget = pendingUninstall
    if (uninstallTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text(text = "Uninstall ${uninstallTarget.appLabel}?") },
            text = {
                Column {
                    Text(
                        text = "Removes the app and its data (${formatBytes(uninstallTarget.dataBytes)} data + ${formatBytes(uninstallTarget.cacheBytes)} cache)."
                    )
                    Box(modifier = Modifier.height(8.dp))
                    Text(
                        text = "If unsure, cancel and clear cache instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUninstallApp(uninstallTarget.packageName)
                        pendingUninstall = null
                    }
                ) {
                    Text(text = "Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

private fun LazyListScope.appCleanupItems(
    hasPermission: Boolean,
    isLoading: Boolean,
    entries: List<AppStorageEntry>?,
    filter: StorageFilter,
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (StorageFilter) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (AppStorageEntry) -> Unit
) {
    if (!hasPermission) {
        item(key = "need_permission") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Usage access for per-app sizes",
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Overall storage works without this. Grant access to see which apps use space.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.height(12.dp))
                    Button(onClick = onRequestPermission) {
                        Text(text = "Open Usage Access")
                    }
                }
            }
        }
        return
    }

    if (isLoading && entries == null) {
        item(key = "apps_loading") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    if (entries == null || entries.isEmpty()) {
        item(key = "apps_empty") {
            Column {
                Text(
                    text = "No per-app storage data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.height(8.dp))
                Button(onClick = onRefresh) {
                    Text(text = "Try again")
                }
            }
        }
        return
    }

    val all = entries
    val totalAppBytes = all.sumOf { it.totalBytes }
    val totalCacheBytes = all.sumOf { it.cacheBytes }
    val safeCacheTotal = all
        .filter { it.cacheBytes >= CACHE_SAFE_BYTES }
        .sumOf { it.cacheBytes }

    val q = query.trim().lowercase(Locale.US)
    val searched = if (q.isEmpty()) {
        all
    } else {
        all.filter {
            it.appLabel.lowercase(Locale.US).contains(q) ||
                it.packageName.lowercase(Locale.US).contains(q)
        }
    }

    val filtered = when (filter) {
        StorageFilter.ALL -> searched
        StorageFilter.SAFE_CACHE ->
            searched.filter { it.cacheBytes >= CACHE_SAFE_BYTES }
                .sortedByDescending { it.cacheBytes }
        StorageFilter.USER_APPS -> searched.filter { !it.isSystemApp }
        StorageFilter.LARGE -> searched.filter { it.totalBytes >= LARGE_APP_BYTES }
    }

    item(key = "totals") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Measured across apps", fontWeight = FontWeight.SemiBold)
                Box(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatBytes(totalAppBytes)} total · ${formatBytes(totalCacheBytes)} cache",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (safeCacheTotal > 0L) {
                    Text(
                        text = "~${formatBytes(safeCacheTotal)} in large caches (safe to clear)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }

    item(key = "search") {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search apps") },
            placeholder = { Text("Name or package") }
        )
    }

    item(key = "filters") {
        FilterRow(selected = filter, onSelect = onFilterChange)
    }

    item(key = "filter_help") {
        Text(
            text = when (filter) {
                StorageFilter.SAFE_CACHE -> "Large cache (≥50 MB). Clear cache is usually safe."
                StorageFilter.USER_APPS -> "Apps you installed."
                StorageFilter.LARGE -> "≥500 MB total."
                StorageFilter.ALL -> "All apps. Prefer clear cache over uninstall."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (filtered.isEmpty()) {
        item(key = "filter_empty") {
            Text(
                text = "Nothing matches.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        items(
            items = filtered,
            key = { entry -> entry.packageName }
        ) { row ->
            StorageRow(
                row = row,
                risk = riskFor(row),
                onClick = { onSelect(row) }
            )
        }
    }
}

@Composable
private fun OverviewCard(s: StorageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Internal storage", fontWeight = FontWeight.SemiBold)
            Box(modifier = Modifier.height(8.dp))
            UsageBar(
                label = "Used",
                percent = s.usagePercent,
                detail = String.format(
                    Locale.US,
                    "%.1f / %.1f GB",
                    s.usedInternalGb,
                    s.totalInternalGb
                )
            )
            Text(
                text = String.format(Locale.US, "%.1f GB free", s.freeInternalGb),
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = vol.name, fontWeight = FontWeight.SemiBold)
            Text(
                text = vol.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = vol.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(modifier = Modifier.height(6.dp))
            if (vol.totalBytes > 0L) {
                UsageBar(
                    label = "Used",
                    percent = vol.usagePercent,
                    detail = String.format(
                        Locale.US,
                        "%.1f / %.1f GB",
                        vol.usedGb,
                        vol.totalGb
                    )
                )
            } else {
                Text(
                    text = "State: ${vol.state}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: StorageFilter,
    onSelect: (StorageFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == StorageFilter.SAFE_CACHE,
            onClick = { onSelect(StorageFilter.SAFE_CACHE) },
            label = { Text(text = "Safe cache") }
        )
        FilterChip(
            selected = selected == StorageFilter.USER_APPS,
            onClick = { onSelect(StorageFilter.USER_APPS) },
            label = { Text(text = "My apps") }
        )
        FilterChip(
            selected = selected == StorageFilter.LARGE,
            onClick = { onSelect(StorageFilter.LARGE) },
            label = { Text(text = "Large") }
        )
        FilterChip(
            selected = selected == StorageFilter.ALL,
            onClick = { onSelect(StorageFilter.ALL) },
            label = { Text(text = "All") }
        )
    }
}

@Composable
private fun RiskHint(risk: CleanupRisk) {
    val label: String
    val color: Color
    when (risk) {
        CleanupRisk.SAFE_CACHE -> {
            label = "Safe: large cache"
            color = Color(0xFF2E7D32)
        }
        CleanupRisk.REVIEW -> {
            label = "Review before uninstall"
            color = Color(0xFFF9A825)
        }
        CleanupRisk.SYSTEM -> {
            label = "System app"
            color = MaterialTheme.colorScheme.error
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

@Composable
private fun StorageRow(
    row: AppStorageEntry,
    risk: CleanupRisk,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.appLabel, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = row.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatBytes(row.totalBytes),
                    fontWeight = FontWeight.Medium
                )
            }
            Box(modifier = Modifier.height(4.dp))
            RiskHint(risk = risk)
            Text(
                text = "App ${formatBytes(row.appBytes)} · Data ${formatBytes(row.dataBytes)} · Cache ${formatBytes(row.cacheBytes)}",
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
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.2f GB", gb)
    } else {
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    }
}
