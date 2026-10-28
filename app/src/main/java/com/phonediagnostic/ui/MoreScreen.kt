package com.phonediagnostic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    report: FullDeviceReport?,
    versionName: String,
    historySamples: Int,
    onOpenRam: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenThermals: () -> Unit,
    onOpenPrivacy: () -> Unit,
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Diagnostics ──────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.more_section_diagnostics)) }
            item {
                MoreGroup {
                    MoreLink(
                        icon = Icons.Outlined.Memory,
                        title = stringResource(R.string.more_ram),
                        subtitle = report?.memory?.let {
                            "${it.usedRamMb} / ${it.totalRamMb} MB"
                        } ?: stringResource(R.string.more_ram_sub_fallback),
                        onClick = onOpenRam
                    )
                    GroupDivider()
                    MoreLink(
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.more_storage),
                        subtitle = report?.storage?.let {
                            stringResource(R.string.more_storage_free, it.freeInternalGb)
                        } ?: stringResource(R.string.more_storage_sub_fallback),
                        onClick = onOpenStorage
                    )
                    GroupDivider()
                    MoreLink(
                        icon = Icons.Filled.Wifi,
                        title = stringResource(R.string.more_network),
                        subtitle = report?.network?.let {
                            stringResource(R.string.more_network_sub, it.networkType)
                        } ?: stringResource(R.string.more_network_sub_fallback),
                        onClick = onOpenNetwork
                    )
                    GroupDivider()
                    MoreLink(
                        icon = Icons.Filled.Thermostat,
                        title = stringResource(R.string.more_thermals),
                        subtitle = report?.thermals?.let { zones ->
                            if (zones.isEmpty()) stringResource(R.string.more_thermals_none)
                            else stringResource(R.string.more_thermals_count, zones.size)
                        } ?: stringResource(R.string.more_thermals_sub_fallback),
                        onClick = onOpenThermals
                    )
                    GroupDivider()
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
                    GroupDivider()
                    MoreLink(
                        icon = Icons.Filled.Shield,
                        title = stringResource(R.string.more_privacy),
                        subtitle = stringResource(R.string.more_privacy_sub),
                        onClick = onOpenPrivacy
                    )
                }
            }

            // ── Report ───────────────────────────────────────────────────
            // Six export actions used to be six full-size cards; they are
            // one card of chips now so the tab fits without much scrolling.
            item { SectionHeader(stringResource(R.string.more_section_report)) }
            item {
                MoreGroup {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                        Text(
                            text = stringResource(R.string.more_export_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(
                                if (hasReport) R.string.more_export_sub else R.string.more_collect_first
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExportChip(Icons.Filled.Share, stringResource(R.string.more_share_text), hasReport, onShareText)
                            ExportChip(Icons.Filled.Share, stringResource(R.string.more_share_json), hasReport, onShareJson)
                            ExportChip(Icons.Filled.AttachFile, stringResource(R.string.more_share_file), hasReport, onShareFile)
                            ExportChip(Icons.Filled.Save, stringResource(R.string.more_save_txt), hasReport, onSaveText)
                            ExportChip(Icons.Filled.Save, stringResource(R.string.more_save_json), hasReport, onSaveJson)
                            ExportChip(Icons.Filled.ContentCopy, stringResource(R.string.more_copy_text), hasReport, onCopyText)
                        }
                    }
                }
            }

            // ── App ──────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.more_section_app)) }
            item {
                MoreGroup {
                    MoreLink(
                        icon = Icons.Filled.Build,
                        title = stringResource(R.string.more_tools),
                        subtitle = stringResource(R.string.more_tools_sub),
                        onClick = onOpenTools
                    )
                    GroupDivider()
                    MoreLink(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.more_settings),
                        subtitle = stringResource(R.string.more_settings_sub),
                        onClick = onOpenSettings
                    )
                    GroupDivider()
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
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

/** One card per section; rows inside are separated by thin dividers. */
@Composable
private fun MoreGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun MoreLink(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportChip(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        }
    )
}
