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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phonediagnostic.data.DiagnosticLog
import com.phonediagnostic.data.LoadTestProgress
import com.phonediagnostic.data.LoadTestResult
import com.phonediagnostic.data.LoadTester
import java.util.Locale

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
    var showDisplayTest by remember { mutableStateOf(false) }
    var displayColorIndex by remember { mutableIntStateOf(0) }

    val displayColors = listOf(
        Color.Red to "Red",
        Color.Green to "Green",
        Color.Blue to "Blue",
        Color.White to "White",
        Color.Black to "Black",
        Color.Cyan to "Cyan",
        Color.Magenta to "Magenta",
        Color.Yellow to "Yellow"
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
            val (color, name) = displayColors[displayColorIndex % displayColors.size]
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
                    text = "$name\nTap to cycle · Back to exit",
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
                title = { Text("Tools") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !loadTesting) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShareLog, enabled = !loadTesting && logLines.isNotEmpty()) {
                        Icon(Icons.Filled.Share, contentDescription = "Share log")
                    }
                    IconButton(onClick = onRefreshLog, enabled = !loadTesting) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh log")
                    }
                    IconButton(onClick = onClearLog, enabled = !loadTesting) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear log")
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
                        Text("Load test", fontWeight = FontWeight.SemiBold)
                        Box(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CPU stress test. Phone may warm up and drain battery. " +
                                "Live stats update while running.",
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
                                    val label = "${seconds / 60} min"
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
                                    text = "Last result",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${lastLoadResult.score}k ops/s across " +
                                        "${lastLoadResult.threads} threads",
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
                        Text("Hardware checks", fontWeight = FontWeight.SemiBold)
                        Box(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Quick on-device tests. No data leaves the phone.",
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
                            ) { Text("Vibrate") }
                            OutlinedButton(
                                onClick = { playTone() },
                                modifier = Modifier.weight(1f),
                                enabled = !loadTesting
                            ) { Text("Tone") }
                            OutlinedButton(
                                onClick = {
                                    displayColorIndex = 0
                                    showDisplayTest = true
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !loadTesting
                            ) { Text("Display") }
                        }
                        Box(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Multi-touch: put fingers on the pad below",
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
                                text = if (maxPointers == 0) "Touch here" else "Max fingers: $maxPointers",
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
                    Text("Rotating log", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${logLines.size} / ${DiagnosticLog.MAX_ENTRIES}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Max ~0.5–1 MB when full. Oldest lines drop. Background monitor adds a line every 30s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (logLines.isEmpty()) {
                item {
                    Text(
                        text = "Log is empty. Enable background monitor in Settings or run a load test.",
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
        Text("Live stats", fontWeight = FontWeight.SemiBold)
        Box(modifier = Modifier.height(6.dp))
        MetricRow("RAM used", "${progress.ramUsedMb} MB")
        MetricRow("Battery", "${progress.batteryPct}%")
        MetricRow("Temperature", String.format(Locale.US, "%.1f °C", progress.tempC))
        MetricRow("CPU ops", formatOps(progress.operations))
        MetricRow("Ops / sec", formatOps(progress.opsPerSec))
        MetricRow("Threads", "${progress.threads} active")
        Box(modifier = Modifier.height(8.dp))
        Text(
            text = "Keep this screen open. UI may feel slower under full CPU load.",
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
