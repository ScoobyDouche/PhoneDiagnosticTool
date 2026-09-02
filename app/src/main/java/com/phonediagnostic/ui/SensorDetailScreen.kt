package com.phonediagnostic.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import com.phonediagnostic.ui.components.Sparkline
import java.util.Locale

private const val WINDOW_SIZE = 120
private const val PUBLISH_INTERVAL_MS = 100L
private val AXIS_LABELS = listOf("X", "Y", "Z")
private val AXIS_COLORS = listOf(
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFF1E88E5)
)

/**
 * Streams one sensor continuously, rather than the single 250 ms snapshot the
 * report collection takes. Registration is tied to this screen, so nothing keeps
 * sampling once it is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailScreen(
    sensorName: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val sensor = remember(sensorManager, sensorName) {
        if (sensorName == null) {
            null
        } else {
            runCatching {
                sensorManager?.getSensorList(Sensor.TYPE_ALL)?.firstOrNull { it.name == sensorName }
            }.getOrNull()
        }
    }

    var streaming by remember { mutableStateOf(true) }
    var latest by remember { mutableStateOf<List<Float>>(emptyList()) }
    var window by remember { mutableStateOf<List<List<Float>>>(emptyList()) }
    var eventCount by remember { mutableLongStateOf(0L) }
    var accuracy by remember { mutableIntStateOf(Int.MIN_VALUE) }

    DisposableEffect(sensor, streaming) {
        val manager = sensorManager
        if (manager == null || sensor == null || !streaming) {
            return@DisposableEffect onDispose { }
        }
        var lastPublishMs = 0L
        var received = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                received++
                val now = SystemClock.uptimeMillis()
                if (now - lastPublishMs < PUBLISH_INTERVAL_MS) return
                lastPublishMs = now
                val snapshot = values.take(3)
                latest = snapshot
                eventCount = received
                window = (window + listOf(snapshot)).takeLast(WINDOW_SIZE)
            }

            override fun onAccuracyChanged(changed: Sensor?, value: Int) {
                accuracy = value
            }
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { runCatching { manager.unregisterListener(listener) } }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = sensor?.name ?: sensorName ?: stringResource(R.string.sensor_detail_fallback),
                            maxLines = 1
                        )
                        Text(
                            text = if (streaming) stringResource(R.string.sensor_streaming)
                            else stringResource(R.string.state_paused),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (streaming) {
                                Color(0xFF4CAF50)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { streaming = !streaming }, enabled = sensor != null) {
                        Icon(
                            imageVector = if (streaming) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (streaming) stringResource(R.string.sensor_pause_cd)
                            else stringResource(R.string.sensor_resume_cd)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (sensor == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.sensor_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val axisCount = maxOf(latest.size, window.firstOrNull()?.size ?: 0).coerceIn(0, 3)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "reading") {
                InfoCard(title = stringResource(R.string.sensor_current_reading)) {
                    Column {
                        if (latest.isEmpty()) {
                            Text(
                                text = if (streaming) stringResource(R.string.sensor_waiting)
                                else stringResource(R.string.sensor_paused),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            latest.forEachIndexed { index, value ->
                                InfoRow(
                                    AXIS_LABELS.getOrElse(index) { "Value ${index + 1}" },
                                    String.format(Locale.US, "%.4f", value)
                                )
                            }
                        }
                        InfoRow(stringResource(R.string.label_events), eventCount.toString())
                        InfoRow(stringResource(R.string.label_accuracy), accuracyLabel(accuracy))
                    }
                }
            }

            if (axisCount > 0 && window.size >= 2) {
                item(key = "chart") {
                    InfoCard(title = stringResource(R.string.sensor_last_samples, window.size)) {
                        Column {
                            repeat(axisCount) { axis ->
                                val series = window.map { it.getOrElse(axis) { 0f } }
                                Text(
                                    text = AXIS_LABELS.getOrElse(axis) { "Value ${axis + 1}" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AXIS_COLORS.getOrElse(axis) {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Sparkline(
                                    values = series,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    lineColor = AXIS_COLORS.getOrElse(axis) {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.sensor_min_max,
                                        series.min(),
                                        series.max()
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            item(key = "meta") {
                InfoCard(title = stringResource(R.string.sensor_section_meta)) {
                    Column {
                        InfoRow(
                            stringResource(R.string.label_vendor),
                            sensor.vendor.orEmpty().ifBlank { "—" }
                        )
                        InfoRow(stringResource(R.string.label_version), sensor.version.toString())
                        InfoRow(
                            stringResource(R.string.label_max_range),
                            String.format(Locale.US, "%.3f", sensor.maximumRange)
                        )
                        InfoRow(
                            stringResource(R.string.label_resolution),
                            String.format(Locale.US, "%.6f", sensor.resolution)
                        )
                        InfoRow(
                            stringResource(R.string.label_power),
                            stringResource(R.string.sensor_power_ma, sensor.power)
                        )
                        InfoRow(
                            stringResource(R.string.label_min_delay),
                            if (sensor.minDelay > 0) stringResource(R.string.sensor_min_delay_us, sensor.minDelay)
                            else stringResource(R.string.sensor_on_change)
                        )
                    }
                }
            }

            item(key = "note") {
                Text(
                    text = stringResource(R.string.sensor_stop_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun accuracyLabel(accuracy: Int): String = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> stringResource(R.string.accuracy_high)
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> stringResource(R.string.accuracy_medium)
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> stringResource(R.string.accuracy_low)
    SensorManager.SENSOR_STATUS_UNRELIABLE -> stringResource(R.string.accuracy_unreliable)
    SensorManager.SENSOR_STATUS_NO_CONTACT -> stringResource(R.string.accuracy_no_contact)
    else -> stringResource(R.string.accuracy_not_reported)
}
