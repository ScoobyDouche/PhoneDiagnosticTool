package com.phonediagnostic.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.AppStorageEntry
import java.util.Locale

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Storage by app") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh"
                            )
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
                        text = "Android requires Usage Access to show how much space each app uses. " +
                            "This stays on your device - nothing is uploaded.",
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
                    Button(onClick = onRefresh) {
                        Text(text = "Try again")
                    }
                }
            }

            else -> {
                val rows = entries
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Tap an app to manage storage (App info / Uninstall).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(
                        items = rows,
                        key = { row -> row.packageName }
                    ) { row ->
                        StorageRow(
                            row = row,
                            onClick = { selected = row }
                        )
                    }
                }
            }
        }
    }

    selected?.let { app ->
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
                Box(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total ${formatBytes(app.totalBytes)} · Cache ${formatBytes(app.cacheBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.height(16.dp))
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
                    Text("App info (clear cache / storage)")
                }
                if (!app.isSystemApp) {
                    TextButton(
                        onClick = {
                            onUninstallApp(app.packageName)
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
                            text = "Uninstall",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        text = "System app — uninstall may be blocked. Use App info to disable or clear data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageRow(
    row: AppStorageEntry,
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
            Box(modifier = Modifier.height(6.dp))
            Text(
                text = "App ${formatBytes(row.appBytes)} · Data ${formatBytes(row.dataBytes)} · Cache ${formatBytes(row.cacheBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.2f GB", gb)
    } else {
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    }
}
