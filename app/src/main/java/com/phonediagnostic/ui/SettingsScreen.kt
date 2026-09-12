package com.phonediagnostic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedButton
import com.phonediagnostic.R
import com.phonediagnostic.data.DiagnosticLog
import com.phonediagnostic.data.ThemeMode
import com.phonediagnostic.data.elevated.AccessTier
import com.phonediagnostic.data.elevated.ElevatedStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    networkProbeEnabled: Boolean,
    backgroundMonitorEnabled: Boolean,
    themeMode: ThemeMode,
    elevatedStatus: ElevatedStatus,
    onNetworkProbeChange: (Boolean) -> Unit,
    onBackgroundMonitorChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccessTierChange: (AccessTier) -> Unit,
    onRequestShizuku: () -> Unit,
    onRefreshElevated: () -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTools: () -> Unit,
    onAddQuickTile: (() -> Unit)?
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_privacy_heading),
                style = MaterialTheme.typography.titleMedium
            )
            Box(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_latency_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_latency_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = networkProbeEnabled,
                    onCheckedChange = onNetworkProbeChange
                )
            }

            Box(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Box(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_background_heading),
                style = MaterialTheme.typography.titleMedium
            )
            Box(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_monitor_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_monitor_description,
                            DiagnosticLog.MAX_ENTRIES
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backgroundMonitorEnabled,
                    onCheckedChange = onBackgroundMonitorChange
                )
            }

            Box(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Box(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_tile_heading),
                style = MaterialTheme.typography.titleMedium
            )
            Box(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_tile_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_tile_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(modifier = Modifier.height(8.dp))
            // Android 13 can prompt to add the tile in one tap. Below that the
            // user has to go and find it, so say where it is rather than
            // offering a button that cannot work.
            if (onAddQuickTile != null) {
                Button(onClick = onAddQuickTile) {
                    Text(stringResource(R.string.settings_tile_add))
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_tile_manual),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Box(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_tools_link),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTools)
                    .padding(vertical = 12.dp)
            )

            Box(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Box(modifier = Modifier.height(16.dp))

            ElevatedAccessSection(
                status = elevatedStatus,
                onAccessTierChange = onAccessTierChange,
                onRequestShizuku = onRequestShizuku,
                onRefreshElevated = onRefreshElevated
            )

            Box(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Box(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_appearance_heading),
                style = MaterialTheme.typography.titleMedium
            )
            Box(modifier = Modifier.height(8.dp))
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) }
                    )
                    Text(
                        text = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            }
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Box(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Box(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_about_link),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAbout)
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ElevatedAccessSection(
    status: ElevatedStatus,
    onAccessTierChange: (AccessTier) -> Unit,
    onRequestShizuku: () -> Unit,
    onRefreshElevated: () -> Unit
) {
    Text(
        text = stringResource(R.string.settings_elevated_heading),
        style = MaterialTheme.typography.titleMedium
    )
    Box(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.settings_elevated_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Box(modifier = Modifier.height(8.dp))

    AccessTier.entries.forEach { tier ->
        val selected = status.preferredTier == tier
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = { onAccessTierChange(tier) },
                    role = Role.RadioButton
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = { onAccessTierChange(tier) })
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = stringResource(
                        when (tier) {
                            AccessTier.NONE -> R.string.elevated_tier_off
                            AccessTier.SHIZUKU -> R.string.elevated_tier_shizuku
                            AccessTier.ROOT -> R.string.elevated_tier_root
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(
                        when (tier) {
                            AccessTier.NONE -> R.string.elevated_tier_off_desc
                            AccessTier.SHIZUKU -> R.string.elevated_tier_shizuku_desc
                            AccessTier.ROOT -> R.string.elevated_tier_root_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Live status for the chosen tier, so the user knows why it is or is not working.
    when (status.preferredTier) {
        AccessTier.NONE -> Unit
        AccessTier.SHIZUKU -> {
            val active = status.activeTier == AccessTier.SHIZUKU
            val (msgRes, isError) = when {
                active -> R.string.elevated_active to false
                !status.shizukuInstalled -> R.string.elevated_shizuku_not_installed to true
                !status.shizukuRunning -> R.string.elevated_shizuku_not_running to true
                !status.shizukuPermission -> R.string.elevated_shizuku_needs_permission to false
                else -> R.string.elevated_shizuku_needs_permission to false
            }
            Box(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(msgRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status.shizukuRunning && !status.shizukuPermission) {
                Box(modifier = Modifier.height(8.dp))
                Button(onClick = onRequestShizuku) {
                    Text(stringResource(R.string.elevated_shizuku_grant))
                }
            }
        }
        AccessTier.ROOT -> {
            val active = status.activeTier == AccessTier.ROOT
            Box(modifier = Modifier.height(4.dp))
            if (active) {
                Text(
                    text = stringResource(R.string.elevated_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!status.rootAvailable) {
                Text(
                    text = stringResource(R.string.elevated_root_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Box(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.elevated_root_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (status.preferredTier != AccessTier.NONE) {
        Box(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onRefreshElevated) {
            Text(stringResource(R.string.elevated_refresh))
        }
    }
}
