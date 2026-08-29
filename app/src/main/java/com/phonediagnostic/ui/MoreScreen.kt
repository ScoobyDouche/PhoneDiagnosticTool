package com.phonediagnostic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.data.FullDeviceReport
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    report: FullDeviceReport?,
    versionName: String,
    onOpenRam: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onShareText: () -> Unit,
    onShareJson: () -> Unit,
    onCopyText: () -> Unit
) {
    val hasReport = report != null

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text("More") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(
                    text = "CPU, Battery, and Sensors are in the bottom tabs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            item {
                Text(
                    text = "Report",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Share,
                    title = "Share as text",
                    subtitle = if (hasReport) "Plain-text diagnostic report" else "Collect a report first",
                    enabled = hasReport,
                    onClick = onShareText
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Share,
                    title = "Share as JSON",
                    subtitle = if (hasReport) "Machine-readable export" else "Collect a report first",
                    enabled = hasReport,
                    onClick = onShareJson
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.ContentCopy,
                    title = "Copy text",
                    subtitle = if (hasReport) "Copy report to clipboard" else "Collect a report first",
                    enabled = hasReport,
                    onClick = onCopyText
                )
            }

            item {
                Text(
                    text = "Details & tools",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            item {
                MoreLink(
                    icon = Icons.Outlined.Memory,
                    title = "RAM detail",
                    subtitle = report?.memory?.let {
                        "${it.usedRamMb} / ${it.totalRamMb} MB"
                    } ?: "Process memory",
                    onClick = onOpenRam
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Storage,
                    title = "Storage",
                    subtitle = report?.storage?.let {
                        String.format(Locale.US, "%.1f GB free", it.freeInternalGb)
                    } ?: "Volumes & apps",
                    onClick = onOpenStorage
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Build,
                    title = "Tools",
                    subtitle = "Load test, display, vibrate, log",
                    onClick = onOpenTools
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Settings,
                    title = "Settings",
                    subtitle = "Theme, monitor, network probe",
                    onClick = onOpenSettings
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Info,
                    title = "About",
                    subtitle = "v$versionName · privacy-first",
                    onClick = onOpenAbout
                )
            }
        }
    }
}

@Composable
private fun MoreLink(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = alpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
