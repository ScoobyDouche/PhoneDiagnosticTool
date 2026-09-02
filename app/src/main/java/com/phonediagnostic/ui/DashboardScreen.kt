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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import com.phonediagnostic.ui.components.UsageBar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    report: FullDeviceReport?,
    isLive: Boolean,
    lastUpdated: String,
    isRefreshing: Boolean,
    errorMessage: String?,
    versionName: String,
    onToggleLive: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRamDetail: () -> Unit,
    onOpenStorageDetail: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.dashboard_title), fontWeight = FontWeight.SemiBold)
                        if (lastUpdated.isNotEmpty()) {
                            Text(
                                text = if (isLive) {
                                    stringResource(R.string.dashboard_live_status, lastUpdated)
                                } else {
                                    stringResource(R.string.dashboard_paused_status, lastUpdated)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onToggleLive) {
                        Icon(
                            imageVector = if (isLive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLive) {
                                stringResource(R.string.dashboard_pause_cd)
                            } else {
                                stringResource(R.string.dashboard_resume_cd)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                report == null && errorMessage != null -> {
                    ErrorState(message = errorMessage, onRetry = onRefresh)
                }
                report == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.dashboard_collecting))
                        Text(
                            stringResource(R.string.dashboard_pull_to_refresh),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                else -> {
                    ReportList(
                        data = report,
                        isLive = isLive,
                        versionName = versionName,
                        onOpenRamDetail = onOpenRamDetail,
                        onOpenStorageDetail = onOpenStorageDetail,
                        onOpenNetwork = onOpenNetwork,
                        onOpenHistory = onOpenHistory
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportList(
    data: FullDeviceReport,
    isLive: Boolean,
    versionName: String,
    onOpenRamDetail: () -> Unit,
    onOpenStorageDetail: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "live_badge") { LiveBadge(isLive = isLive) }

        item(key = "device") {
            InfoCard(title = stringResource(R.string.section_device)) {
                Column {
                    InfoRow(stringResource(R.string.label_manufacturer), data.overview.manufacturer)
                    InfoRow(stringResource(R.string.label_model), data.overview.model)
                    InfoRow(stringResource(R.string.label_brand), data.overview.brand)
                    InfoRow(
                        stringResource(R.string.label_android),
                        "${data.overview.androidVersion} (API ${data.overview.apiLevel})"
                    )
                    InfoRow(stringResource(R.string.label_security_patch), data.overview.securityPatch)
                    InfoRow(stringResource(R.string.label_build_id), data.overview.buildId)
                    if (data.overview.board.isNotBlank()) {
                        InfoRow(stringResource(R.string.label_board), data.overview.board)
                    }
                    if (data.overview.bootloader.isNotBlank()) {
                        InfoRow(stringResource(R.string.label_bootloader), data.overview.bootloader)
                    }
                    if (data.overview.hardware.isNotBlank()) {
                        InfoRow(stringResource(R.string.label_hardware), data.overview.hardware)
                    }
                    if (data.overview.type.isNotBlank()) {
                        InfoRow(stringResource(R.string.label_build_type), data.overview.type)
                    }
                    InfoRow(stringResource(R.string.label_uptime), data.overview.uptime)
                    if (data.overview.kernelVersion.isNotBlank()) {
                        InfoRow(stringResource(R.string.label_kernel), data.overview.kernelVersion)
                    }
                    if (data.overview.radioVersion.isNotBlank()) {
                        InfoRow(stringResource(R.string.label_radio), data.overview.radioVersion)
                    }
                    if (data.overview.fingerprint.isNotBlank()) {
                        InfoRow(stringResource(R.string.label_fingerprint), data.overview.fingerprint)
                    }
                }
            }
        }

        item(key = "gpu") {
            InfoCard(title = stringResource(R.string.section_gpu)) {
                Column {
                    InfoRow(stringResource(R.string.label_renderer), data.gpu.renderer)
                    InfoRow(stringResource(R.string.label_vendor), data.gpu.vendor)
                    InfoRow(stringResource(R.string.label_version), data.gpu.version)
                }
            }
        }

        item(key = "memory") {
            val m = data.memory
            InfoCard(
                title = stringResource(R.string.section_memory),
                onClick = onOpenRamDetail
            ) {
                Column {
                    UsageBar(
                        label = stringResource(R.string.label_in_use),
                        percent = m.usagePercent,
                        detail = "${m.usedRamMb} / ${m.totalRamMb} MB"
                    )
                    InfoRow(stringResource(R.string.label_available), "${m.availableRamMb} MB")
                    if (m.thresholdMb > 0) {
                        InfoRow(stringResource(R.string.label_low_mem_threshold), "${m.thresholdMb} MB")
                    }
                    InfoRow(
                        stringResource(R.string.label_pressure),
                        if (m.isLowMemory) {
                            stringResource(R.string.pressure_yes)
                        } else {
                            stringResource(R.string.pressure_no)
                        }
                    )
                    if (m.statusHint.isNotBlank()) {
                        Text(
                            text = m.statusHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (m.isLowMemory) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color(0xFF2E7D32)
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        item(key = "trends") {
            InfoCard(
                title = stringResource(R.string.section_trends),
                subtitle = stringResource(R.string.history_chevron),
                onClick = onOpenHistory
            ) {
                Text(
                    text = stringResource(R.string.trends_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "network") {
            InfoCard(
                title = stringResource(R.string.section_network),
                subtitle = stringResource(R.string.details_chevron),
                onClick = onOpenNetwork
            ) {
                Column {
                    InfoRow(
                        stringResource(R.string.label_connection),
                        if (data.network.isConnected) {
                            stringResource(R.string.connection_connected)
                        } else {
                            stringResource(R.string.connection_disconnected)
                        }
                    )
                    InfoRow(stringResource(R.string.label_type), data.network.networkType)
                    InfoRow(
                        stringResource(R.string.label_latency),
                        data.network.latencyMs?.let { "$it ms" } ?: "—"
                    )
                    InfoRow(stringResource(R.string.label_target), data.network.latencyTarget)
                    InfoRow(stringResource(R.string.label_status), data.network.latencyStatus)
                    if (data.network.downstreamMbps != null || data.network.upstreamMbps != null) {
                        val down = data.network.downstreamMbps?.toString() ?: "?"
                        val up = data.network.upstreamMbps?.toString() ?: "?"
                        InfoRow(
                            stringResource(R.string.label_link_bandwidth),
                            "$down ↓ / $up ↑ Mbps"
                        )
                    }
                    InfoRow(
                        stringResource(R.string.label_validated),
                        if (data.network.validated) stringResource(R.string.yes) else stringResource(R.string.no)
                    )
                    InfoRow(
                        stringResource(R.string.label_metered),
                        if (data.network.metered) stringResource(R.string.yes) else stringResource(R.string.no)
                    )
                }
            }
        }

        item(key = "storage") {
            val s = data.storage
            InfoCard(
                title = stringResource(R.string.section_storage),
                onClick = onOpenStorageDetail
            ) {
                Column {
                    UsageBar(
                        label = stringResource(R.string.label_internal_data),
                        percent = s.usagePercent,
                        detail = String.format(
                            Locale.US,
                            "%.1f / %.1f GB",
                            s.usedInternalGb,
                            s.totalInternalGb
                        )
                    )
                    InfoRow(
                        stringResource(R.string.label_free),
                        String.format(Locale.US, "%.2f GB", s.freeInternalGb)
                    )
                    InfoRow(stringResource(R.string.label_volumes), s.volumes.size.toString())
                    InfoRow(
                        stringResource(R.string.label_external),
                        if (s.emulatedExternal) {
                            stringResource(R.string.external_emulated, s.externalStorageState)
                        } else {
                            s.externalStorageState.ifBlank { "—" }
                        }
                    )
                }
            }
        }

        item(key = "display") {
            InfoCard(title = stringResource(R.string.section_display)) {
                Column {
                    InfoRow(
                        stringResource(R.string.label_resolution),
                        "${data.display.widthPx} × ${data.display.heightPx}"
                    )
                    InfoRow(
                        stringResource(R.string.label_density),
                        "${data.display.densityDpi} dpi (×${String.format(Locale.US, "%.2f", data.display.density)})"
                    )
                    InfoRow(
                        stringResource(R.string.label_refresh_rate),
                        String.format(Locale.US, "%.1f Hz", data.display.refreshRate)
                    )
                    InfoRow(
                        stringResource(R.string.label_approx_size),
                        String.format(Locale.US, "%.2f\"", data.display.screenSizeInches)
                    )
                }
            }
        }

        item(key = "footer") {
            Text(
                text = stringResource(R.string.dashboard_footer, versionName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.dashboard_error_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.dashboard_try_again))
        }
    }
}

@Composable
private fun LiveBadge(isLive: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = if (isLive)
            Color(0xFF4CAF50).copy(alpha = 0.12f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isLive) {
                    stringResource(R.string.dashboard_live_badge)
                } else {
                    stringResource(R.string.dashboard_paused_badge)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isLive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isLive) {
                    stringResource(R.string.dashboard_live_hint)
                } else {
                    stringResource(R.string.dashboard_paused_hint)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
