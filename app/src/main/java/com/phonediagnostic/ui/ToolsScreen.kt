package com.phonediagnostic.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phonediagnostic.R
import com.phonediagnostic.data.StorageSpeedResult
import com.phonediagnostic.data.StorageSpeedTester
import com.phonediagnostic.data.DiagnosticLog
import com.phonediagnostic.data.LoadTestProgress
import com.phonediagnostic.data.LoadTestResult
import com.phonediagnostic.data.LoadTester
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    logLines: List<String>,
    loadTesting: Boolean,
    loadProgress: LoadTestProgress?,
    lastLoadResult: LoadTestResult?,
    onBack: () -> Unit,
    onRefreshLog: () -> Unit,
    onClearLog: () -> Unit,
    onShareLog: () -> Unit,
    onRunLoadTest: (durationSec: Int) -> Unit
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var maxPointers by remember { mutableIntStateOf(0) }
    var storageTesting by remember { mutableStateOf(false) }
    var storageResult by remember { mutableStateOf<StorageSpeedResult?>(null) }
    var storageError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var showDisplayTest by remember { mutableStateOf(false) }
    var displayColorIndex by remember { mutableIntStateOf(0) }

    val displayColors = listOf(
        Color.Red to R.string.color_red,
        Color.Green to R.string.color_green,
        Color.Blue to R.string.color_blue,
        Color.White to R.string.color_white,
        Color.Black to R.string.color_black,
        Color.Cyan to R.string.color_cyan,
        Color.Magenta to R.string.color_magenta,
        Color.Yellow to R.string.color_yellow
    )

    if (showDisplayTest) {
        Dialog(
            onDismissRequest = { showDisplayTest = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            val (color, nameRes) = displayColors[displayColorIndex % displayColors.size]
            val name = stringResource(nameRes)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .clickable {
                        displayColorIndex = (displayColorIndex + 1) % displayColors.size
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tools_display_hint, name),
                    color = if (color == Color.Black || color == Color.Blue) Color.White else Color.Black,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !loadTesting) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShareLog, enabled = !loadTesting && logLines.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.tools_share_log_cd)
                        )
                    }
                    IconButton(onClick = onRefreshLog, enabled = !loadTesting) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.tools_refresh_log_cd)
                        )
                    }
                    IconButton(onClick = onClearLog, enabled = !loadTesting) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.tools_clear_log_cd)
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
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.tools_load_test_title),
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.tools_load_test_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(modifier = Modifier.height(12.dp))

                        if (loadTesting && loadProgress != null) {
                            LiveLoadPanel(progress = loadProgress)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val durations = LoadTester.ALLOWED_DURATIONS_SEC
                                durations.forEachIndexed { index, seconds ->
                                    val label = stringResource(
                                        R.string.tools_duration_min,
                                        seconds / 60
                                    )
                                    if (index == durations.lastIndex) {
                                        Button(
                                            onClick = { onRunLoadTest(seconds) },
                                            modifier = Modifier.weight(1f),
                                            enabled = !loadTesting
                                        ) { Text(label) }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onRunLoadTest(seconds) },
                                            modifier = Modifier.weight(1f),
                                            enabled = !loadTesting
                                        ) { Text(label) }
                                    }
                                }
                            }
                            if (lastLoadResult != null) {
                                Box(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.tools_last_result),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.tools_score_line,
                                        lastLoadResult.score,
                                        lastLoadResult.threads
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lastLoadResult.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.tools_hardware_title),
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.tools_hardware_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { vibrateShort(context) },
                                modifier = Modifier.weight(1f),
                                enabled = !loadTesting
                            ) { Text(stringResource(R.string.tools_vibrate)) }
                            OutlinedButton(
                                onClick = { playTone() },
                                modifier = Modifier.weight(1f),
                                enabled = !loadTesting
                            ) { Text(stringResource(R.string.tools_tone)) }
                            OutlinedButton(
                                onClick = {
                                    displayColorIndex = 0
                                    showDisplayTest = true
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !loadTesting
                            ) { Text(stringResource(R.string.tools_display)) }
                        }
                        Box(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Box(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.tools_storage_title),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Box(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.tools_storage_body, StorageSpeedTester.SAMPLE_MB),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                storageError = null
                                storageResult = null
                                storageTesting = true
                                scope.launch {
                                    try {
                                        storageResult = StorageSpeedTester.run(context)
                                    } catch (e: StorageSpeedTester.InsufficientSpace) {
                                        storageError = context.getString(
                                            R.string.tools_storage_no_space, e.freeMb
                                        )
                                    } catch (e: Exception) {
                                        storageError = context.getString(
                                            R.string.tools_storage_failed,
                                            e.message ?: e.javaClass.simpleName
                                        )
                                    } finally {
                                        storageTesting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            // The CPU load test saturates every core, which would
                            // make any I/O figure taken alongside it meaningless.
                            enabled = !loadTesting && !storageTesting
                        ) {
                            Text(
                                stringResource(
                                    if (storageTesting) R.string.tools_storage_running
                                    else R.string.tools_storage_run
                                )
                            )
                        }
                        storageResult?.let { r ->
                            Box(modifier = Modifier.height(8.dp))
                            MetricRow(
                                stringResource(R.string.tools_storage_write),
                                stringResource(R.string.tools_storage_mbps, r.writeMbPerSec)
                            )
                            MetricRow(
                                stringResource(R.string.tools_storage_read),
                                stringResource(R.string.tools_storage_mbps, r.readMbPerSec)
                            )
                        }
                        storageError?.let { msg ->
                            Box(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Box(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Box(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.tools_multitouch_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Box(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val count = event.changes.count { it.pressed }
                                            if (count > maxPointers) maxPointers = count
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (maxPointers == 0) {
                                    stringResource(R.string.tools_touch_here)
                                } else {
                                    stringResource(R.string.tools_max_fingers, maxPointers)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.tools_log_title),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            R.string.tools_log_count,
                            logLines.size,
                            DiagnosticLog.MAX_ENTRIES
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.tools_log_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (logLines.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.tools_log_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(
                    items = logLines.asReversed(),
                    key = { index, line -> "$index-$line" }
                ) { _, line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun vibrateShort(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(80)
            }
        }
    } catch (_: Exception) {
        // no vibrator
    }
}

private fun playTone() {
    try {
        val generator = ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
        generator.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)
        // ToneGenerator holds a native AudioTrack that the GC will not reclaim
        // promptly. Without this, every tap leaked one until the process died.
        Handler(Looper.getMainLooper()).postDelayed(
            { runCatching { generator.release() } },
            TONE_DURATION_MS + TONE_RELEASE_GRACE_MS
        )
    } catch (_: Exception) {
        // no audio
    }
}

private const val TONE_VOLUME = 80
private const val TONE_DURATION_MS = 200
private const val TONE_RELEASE_GRACE_MS = 150L

@Composable
private fun LiveLoadPanel(progress: LoadTestProgress) {
    val elapsedLabel = formatClock(progress.elapsedSec)
    val totalLabel = formatClock(progress.durationSec)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = progress.phase,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Box(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        Box(modifier = Modifier.height(6.dp))
        Text(
            text = "$elapsedLabel / $totalLabel",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace
        )
        Box(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.tools_live_stats),
            fontWeight = FontWeight.SemiBold
        )
        Box(modifier = Modifier.height(6.dp))
        MetricRow(
            stringResource(R.string.tools_metric_ram),
            "${progress.ramUsedMb} MB"
        )
        MetricRow(
            stringResource(R.string.tools_metric_battery),
            "${progress.batteryPct}%"
        )
        MetricRow(
            stringResource(R.string.tools_metric_temp),
            String.format(Locale.US, "%.1f °C", progress.tempC)
        )
        MetricRow(
            stringResource(R.string.tools_metric_ops),
            formatOps(progress.operations)
        )
        MetricRow(
            stringResource(R.string.tools_metric_ops_sec),
            formatOps(progress.opsPerSec)
        )
        MetricRow(
            stringResource(R.string.tools_metric_threads),
            stringResource(R.string.tools_threads_active, progress.threads)
        )
        Box(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tools_load_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatClock(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

private fun formatOps(ops: Long): String {
    return when {
        ops >= 1_000_000_000L -> String.format(Locale.US, "%.2fB", ops / 1_000_000_000.0)
        ops >= 1_000_000L -> String.format(Locale.US, "%.1fM", ops / 1_000_000.0)
        ops >= 1_000L -> String.format(Locale.US, "%.1fK", ops / 1_000.0)
        else -> ops.toString()
    }
}
