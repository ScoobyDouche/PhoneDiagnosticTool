package com.phonediagnostic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.CameraEntry
import com.phonediagnostic.data.SensorEntry
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(
    sensors: List<SensorEntry>,
    cameras: List<CameraEntry>,
    isRefreshing: Boolean,
    listState: LazyListState? = null,
    onBack: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onOpenSensor: (String) -> Unit = {}
) {
    val state = listState ?: rememberLazyListState()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sensors_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(
                        R.string.sensors_summary,
                        sensors.size,
                        cameras.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(sensors, key = { "${it.type}:${it.name}" }) { sensor ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSensor(sensor.name) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(sensor.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            sensor.type,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (sensor.vendor.isNotBlank()) {
                            Text(
                                sensor.vendor,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            String.format(
                                Locale.US,
                                "%.2f mA · res %.4f · max %.1f",
                                sensor.powerMa,
                                sensor.resolution,
                                sensor.maxRange
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (sensor.liveValues.isNotBlank()) {
                            Text(
                                "Live: ${sensor.liveValues}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            if (cameras.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sensors_section_cameras),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(cameras, key = { it.id }) { cam ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${cam.facing} · ${cam.id}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${cam.pixelArraySize} · ${cam.hardwareLevel}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Focal: ${cam.focalLengths} · ${cam.aperture}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
