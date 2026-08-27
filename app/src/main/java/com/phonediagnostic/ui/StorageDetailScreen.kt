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

private const val CACHE_SAFE_BYTES = 50L * 1024 * 1024 // 50 MB cache
private const val LARGE_APP_BYTES = 500L * 1024 * 1024 // 500 MB total

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDetailScreen(
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
                title = { Text(text = "Storage cleanup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { contentPadding ->
        when {
            !hasPermission -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Usage access needed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Needed to list app sizes. Nothing is uploaded. " +
                            "We never delete files automatically — you always confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.height(20.dp))
                    Button(onClick = onRequestPermission) {
                        Text(text = "Open Usage Access settings")
                    }
                }
            }

            isLoading && entries == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Box(modifier = Modifier.height(12.dp))
                    Text(text = "Scanning app storage...")
                }
            }

            entries == null || entries.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No per-app storage data returned.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefresh) { Text(text = "Try again") }
                }
            }

            else -> {
                val all = entries
                val safeCacheTotal = all
                    .filter { it.cacheBytes >= CACHE_SAFE_BYTES }
                    .sumOf { it.cacheBytes }
                val filtered = remember(all, filter) {
                    when (filter) {
                        StorageFilter.ALL -> all
                        StorageFilter.SAFE_CACHE ->
                            all.filter { it.cacheBytes >= CACHE_SAFE_BYTES }
                                .sortedByDescending { it.cacheBytes }
                        StorageFilter.USER_APPS ->
                            all.filter { !it.isSystemApp }
                        StorageFilter.LARGE ->
                            all.filter { it.totalBytes >= LARGE_APP_BYTES }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SafetyBanner(safeCacheTotalBytes = safeCacheTotal)
                    }

                    item {
                        FilterRow(
                            selected = filter,
                            onSelect = { filter = it }
                        )
                    }

                    item {
                        Text(
                            text = when (filter) {
                                StorageFilter.SAFE_CACHE ->
                                    "Apps with large cache (≥50 MB). Clearing cache is usually safe — downloads/login stay."
                                StorageFilter.USER_APPS ->
                                    "Apps you installed. Uninstall removes the app and its data."
                                StorageFilter.LARGE ->
                                    "Apps using ≥500 MB total. Check data before uninstalling."
                                StorageFilter.ALL ->
                                    "All apps. Green = safe cache cleanup. Review before uninstall."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "Nothing matches this filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(
                            items = filtered,
                            key = { it.packageName }
                        ) { row ->
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
                Text(
                    text = app.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.height(8.dp))
                Text(
                    text = "App ${formatBytes(app.appBytes)} · Data ${formatBytes(app.dataBytes)} · Cache ${formatBytes(app.cacheBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Box(modifier = Modifier.height(8.dp))
                RiskHint(risk = risk)
                Box(modifier = Modifier.height(12.dp))
                HorizontalDivider()

                // Primary safe action
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
                        if (app.cacheBytes >= CACHE_SAFE_BYTES) {
                            "Clear cache in App info (recommended)"
                        } else {
                            "Open App info (clear cache / storage)"
                        }
                    )
                }

                Text(
                    text = "Android requires the system App info screen to clear cache. " +
                        "We never wipe files in the background.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                if (!app.isSystemApp) {
                    TextButton(
                        onClick = {
                            pendingUninstall = app
                            selected = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Box(modifier = Modifier.padding(horizontal = 8.dp))
                        Text(
                            text = "Uninstall…",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        text = "System app — prefer clear cache / disable in App info. Uninstall is often blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
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
                        "This permanently removes the app and its data " +
                            "(${formatBytes(app.dataBytes)} data + ${formatBytes(app.cacheBytes)} cache)."
                    )
                    Box(modifier = Modifier.height(8.dp))
                    Text(
                        "Photos, chats, and downloads stored only in this app may be lost. " +
                            "If you’re unsure, cancel and use Clear cache instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUninstallApp(app.packageName)
                        pendingUninstall = null
                    }
                ) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SafetyBanner(safeCacheTotalBytes: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2E7D32).copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Safe first: clear cache",
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1B5E20)
            )
            Box(modifier = Modifier.height(4.dp))
            Text(
                text = if (safeCacheTotalBytes > 0) {
                    "About ${formatBytes(safeCacheTotalBytes)} in large caches. " +
                        "Clearing cache does not uninstall apps or wipe your files."
                } else {
                    "No large caches found. Use filters to review big apps carefully."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            label = { Text("Safe cache") }
        )
        FilterChip(
            selected = selected == StorageFilter.USER_APPS,
            onClick = { onSelect(StorageFilter.USER_APPS) },
            label = { Text("My apps") }
        )
        FilterChip(
            selected = selected == StorageFilter.LARGE,
            onClick = { onSelect(StorageFilter.LARGE) },
            label = { Text("Large") }
        )
        FilterChip(
            selected = selected == StorageFilter.ALL,
            onClick = { onSelect(StorageFilter.ALL) },
            label = { Text("All") }
        )
    }
}

@Composable
private fun RiskHint(risk: CleanupRisk) {
    val (label, color) = when (risk) {
        CleanupRisk.SAFE_CACHE -> "Safe: large cache — clear cache first" to Color(0xFF2E7D32)
        CleanupRisk.REVIEW -> "Review: size is mostly app/data — check before uninstall" to Color(0xFFF9A825)
        CleanupRisk.SYSTEM -> "System app — be careful" to MaterialTheme.colorScheme.error
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
            Box(modifier = Modifier.height(4.dp))
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
