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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.FullDeviceReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    report: FullDeviceReport?,
    versionName: String,
    historySamples: Int,
    onOpenRam: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onShareText: () -> Unit,
    onShareJson: () -> Unit,
    onShareFile: () -> Unit,
    onSaveText: () -> Unit,
    onSaveJson: () -> Unit,
    onCopyText: () -> Unit
) {
    val hasReport = report != null

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.more_title)) })
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
                    text = stringResource(R.string.more_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.more_section_report),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.more_share_text),
                    subtitle = if (hasReport) {
                        stringResource(R.string.more_share_text_sub)
                    } else {
                        stringResource(R.string.more_collect_first)
                    },
                    enabled = hasReport,
                    onClick = onShareText
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.more_share_json),
                    subtitle = if (hasReport) {
                        stringResource(R.string.more_share_json_sub)
                    } else {
                        stringResource(R.string.more_collect_first)
                    },
                    enabled = hasReport,
                    onClick = onShareJson
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.AttachFile,
                    title = stringResource(R.string.more_share_file),
                    subtitle = if (hasReport) {
                        stringResource(R.string.more_share_file_sub)
                    } else {
                        stringResource(R.string.more_collect_first)
                    },
                    enabled = hasReport,
                    onClick = onShareFile
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Save,
                    title = stringResource(R.string.more_save_txt),
                    subtitle = if (hasReport) {
                        stringResource(R.string.more_save_txt_sub)
                    } else {
                        stringResource(R.string.more_collect_first)
                    },
                    enabled = hasReport,
                    onClick = onSaveText
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Save,
                    title = stringResource(R.string.more_save_json),
                    subtitle = if (hasReport) {
                        stringResource(R.string.more_save_json_sub)
                    } else {
                        stringResource(R.string.more_collect_first)
                    },
                    enabled = hasReport,
                    onClick = onSaveJson
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.ContentCopy,
                    title = stringResource(R.string.more_copy_text),
                    subtitle = if (hasReport) {
                        stringResource(R.string.more_copy_text_sub)
                    } else {
                        stringResource(R.string.more_collect_first)
                    },
                    enabled = hasReport,
                    onClick = onCopyText
                )
            }

            item {
                Text(
                    text = stringResource(R.string.more_section_details),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            item {
                MoreLink(
                    icon = Icons.Outlined.Memory,
                    title = stringResource(R.string.more_ram),
                    subtitle = report?.memory?.let {
                        "${it.usedRamMb} / ${it.totalRamMb} MB"
                    } ?: stringResource(R.string.more_ram_sub_fallback),
                    onClick = onOpenRam
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.more_storage),
                    subtitle = report?.storage?.let {
                        stringResource(R.string.more_storage_free, it.freeInternalGb)
                    } ?: stringResource(R.string.more_storage_sub_fallback),
                    onClick = onOpenStorage
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Wifi,
                    title = stringResource(R.string.more_network),
                    subtitle = report?.network?.let {
                        stringResource(R.string.more_network_sub, it.networkType)
                    } ?: stringResource(R.string.more_network_sub_fallback),
                    onClick = onOpenNetwork
                )
            }
            item {
                MoreLink(
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    title = stringResource(R.string.more_history),
                    subtitle = if (historySamples > 0) {
                        stringResource(R.string.more_history_samples, historySamples)
                    } else {
                        stringResource(R.string.more_history_fallback)
                    },
                    onClick = onOpenHistory
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Build,
                    title = stringResource(R.string.more_tools),
                    subtitle = stringResource(R.string.more_tools_sub),
                    onClick = onOpenTools
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.more_settings),
                    subtitle = stringResource(R.string.more_settings_sub),
                    onClick = onOpenSettings
                )
            }
            item {
                MoreLink(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.more_about),
                    subtitle = stringResource(R.string.more_about_sub, versionName),
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
