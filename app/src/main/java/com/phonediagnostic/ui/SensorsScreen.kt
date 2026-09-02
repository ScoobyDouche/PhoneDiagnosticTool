package com.phonediagnostic.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
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
    onBack: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onOpenSensor: (String) -> Unit = {}
) {
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.sensors_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                items(cameras, key = { "cam-${it.id}" }) { cam ->
                    CameraCard(cam)
                }
            }

            item {
                Text(
                    text = stringResource(R.string.sensors_section_sensors),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (sensors.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sensors_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(
                    items = sensors,
                    // Vendors sometimes expose two sensors with the same type and
                    // name (wakeup / non-wakeup); the index keeps keys unique so
                    // LazyColumn does not reject the list.
                    key = { index, sensor -> "sensor-$index-${sensor.type}-${sensor.name}" }
                ) { _, sensor ->
                    SensorCard(sensor = sensor, onClick = { onOpenSensor(sensor.name) })
                }
            }
        }
    }
}

@Composable
private fun SensorCard(sensor: SensorEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = sensor.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = sensor.type,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (sensor.vendor.isNotBlank()) {
                MetaRow(stringResource(R.string.label_vendor), sensor.vendor)
            }
            MetaRow(
                stringResource(R.string.label_power),
                String.format(Locale.US, "%.2f mA", sensor.powerMa)
            )
            MetaRow(
                stringResource(R.string.label_resolution),
                String.format(Locale.US, "%.4f", sensor.resolution)
            )
            MetaRow(
                stringResource(R.string.label_max_range),
                String.format(Locale.US, "%.2f", sensor.maxRange)
            )
            if (sensor.minDelayUs > 0) {
                MetaRow(
                    stringResource(R.string.label_min_delay),
                    "${sensor.minDelayUs} µs"
                )
            }
            if (sensor.liveValues.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sensors_live_prefix, sensor.liveValues),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CameraCard(cam: CameraEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.camera_title, cam.id, cam.facing),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            MetaRow(stringResource(R.string.label_hardware_level), cam.hardwareLevel)
            MetaRow(stringResource(R.string.label_pixel_array), cam.pixelArraySize)
            MetaRow(stringResource(R.string.label_orientation), "${cam.sensorOrientation}°")
            MetaRow(stringResource(R.string.label_focal_lengths), cam.focalLengths)
            MetaRow(stringResource(R.string.label_aperture), cam.aperture)
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}
