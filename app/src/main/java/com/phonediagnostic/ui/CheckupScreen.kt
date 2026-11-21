package com.phonediagnostic.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.phonediagnostic.R
import com.phonediagnostic.data.CheckStatus
import com.phonediagnostic.data.CheckupHardware
import com.phonediagnostic.data.CheckupResults
import com.phonediagnostic.data.CheckupTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@StringRes
fun checkupTitle(test: CheckupTest): Int = when (test) {
    CheckupTest.TOUCH -> R.string.checkup_touch
    CheckupTest.MULTITOUCH -> R.string.checkup_multitouch
    CheckupTest.DISPLAY -> R.string.checkup_display
    CheckupTest.SPEAKER -> R.string.checkup_speaker
    CheckupTest.EARPIECE -> R.string.checkup_earpiece
    CheckupTest.VIBRATION -> R.string.checkup_vibration
    CheckupTest.FLASHLIGHT -> R.string.checkup_flashlight
    CheckupTest.BUTTONS -> R.string.checkup_buttons
    CheckupTest.PROXIMITY -> R.string.checkup_proximity
    CheckupTest.LIGHT -> R.string.checkup_light
    CheckupTest.MOTION -> R.string.checkup_motion
    CheckupTest.CHARGING -> R.string.checkup_charging
    CheckupTest.HEADPHONES -> R.string.checkup_headphones
}

@StringRes
private fun checkupHint(test: CheckupTest): Int = when (test) {
    CheckupTest.TOUCH -> R.string.checkup_touch_hint
    CheckupTest.MULTITOUCH -> R.string.checkup_multitouch_hint
    CheckupTest.DISPLAY -> R.string.checkup_display_hint
    CheckupTest.SPEAKER -> R.string.checkup_speaker_hint
    CheckupTest.EARPIECE -> R.string.checkup_earpiece_hint
    CheckupTest.VIBRATION -> R.string.checkup_vibration_hint
    CheckupTest.FLASHLIGHT -> R.string.checkup_flashlight_hint
    CheckupTest.BUTTONS -> R.string.checkup_buttons_hint
    CheckupTest.PROXIMITY -> R.string.checkup_proximity_hint
    CheckupTest.LIGHT -> R.string.checkup_light_hint
    CheckupTest.MOTION -> R.string.checkup_motion_hint
    CheckupTest.CHARGING -> R.string.checkup_charging_hint
    CheckupTest.HEADPHONES -> R.string.checkup_headphones_hint
}

@StringRes
fun checkStatusLabel(status: CheckStatus): Int = when (status) {
    CheckStatus.NOT_RUN -> R.string.check_not_run
    CheckStatus.PASS -> R.string.check_pass
    CheckStatus.FAIL -> R.string.check_fail
    CheckStatus.SKIPPED -> R.string.check_skipped
}

/** Tests drawn edge to edge, with no app bar. */
private val FULL_SCREEN = setOf(CheckupTest.TOUCH, CheckupTest.MULTITOUCH, CheckupTest.DISPLAY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckupScreen(
    results: CheckupResults,
    volumeKeysSeen: Set<Int>,
    onResult: (CheckupTest, CheckStatus) -> Unit,
    onCaptureVolumeKeys: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    var active by rememberSaveable { mutableStateOf<CheckupTest?>(null) }
    val finish: (CheckStatus?) -> Unit = { status ->
        val test = active
        if (test != null && status != null) onResult(test, status)
        active = null
    }

    val current = active
    if (current != null) {
        BackHandler { finish(null) }
        if (current in FULL_SCREEN) {
            ImmersiveMode()
            when (current) {
                CheckupTest.TOUCH -> TouchGridTest(finish)
                CheckupTest.MULTITOUCH -> MultiTouchTest(finish)
                else -> DisplayColorTest(finish)
            }
        } else {
            InlineTest(current, volumeKeysSeen, onCaptureVolumeKeys, finish)
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!results.isEmpty) TextButton(onClick = onReset) { Text(stringResource(R.string.checkup_reset)) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.checkup_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!results.isEmpty) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (results.failed > 0) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = stringResource(
                                R.string.checkup_summary, results.passed, results.failed, CheckupTest.entries.size
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    CheckupTest.entries.forEachIndexed { index, test ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        val status = results.status(test)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { active = test }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(checkupTitle(test)),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(checkStatusLabel(status)),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = when (status) {
                                    CheckStatus.PASS -> MaterialTheme.colorScheme.primary
                                    CheckStatus.FAIL -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.checkup_mic_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Hides the status and navigation bars while a full-screen test is up. */
@Composable
private fun ImmersiveMode() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

// ------------------------------------------------------------------ touch grid

@Composable
private fun TouchGridTest(finish: (CheckStatus?) -> Unit) {
    val density = LocalDensity.current
    val cellPx = with(density) { 44.dp.toPx() }
    var cols by remember { mutableIntStateOf(0) }
    var rows by remember { mutableIntStateOf(0) }
    val covered = remember { mutableStateMapOf<Int, Boolean>() }
    val total = cols * rows

    LaunchedEffect(covered.size, total) {
        if (total > 0 && covered.size >= total) {
            delay(300)
            finish(CheckStatus.PASS)
        }
    }

    // Tracked on the container in the Initial pass, so squares under the
    // give-up button still register when a finger slides across them.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    fun mark(o: Offset) {
                        if (cols == 0 || rows == 0) return
                        val c = (o.x / (size.width / cols.toFloat())).toInt().coerceIn(0, cols - 1)
                        val r = (o.y / (size.height / rows.toFloat())).toInt().coerceIn(0, rows - 1)
                        covered[r * cols + c] = true
                    }
                    mark(down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { if (it.pressed) mark(it.position) }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            if (cols == 0) {
                cols = (size.width / cellPx).toInt().coerceAtLeast(4)
                rows = (size.height / cellPx).toInt().coerceAtLeast(6)
            }
            val w = size.width / cols
            val h = size.height / rows
            for (r in 0 until rows) for (c in 0 until cols) {
                val done = covered[r * cols + c] == true
                drawRect(
                    color = if (done) Color(0xFF2E7D32) else Color(0xFF263238),
                    topLeft = Offset(c * w + 1, r * h + 1),
                    size = Size(w - 2, h - 2)
                )
            }
        }
        Column(
            Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.checkup_touch_remaining, (total - covered.size).coerceAtLeast(0)),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { finish(CheckStatus.FAIL) }) {
                Text(stringResource(R.string.checkup_touch_give_up), color = Color.White)
            }
        }
    }
}

// ------------------------------------------------------------------ multi-touch

@Composable
private fun MultiTouchTest(finish: (CheckStatus?) -> Unit) {
    val points = remember { mutableStateListOf<Offset>() }
    var maxSeen by remember { mutableIntStateOf(0) }
    val colors = listOf(
        Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFCA28), Color(0xFFAB47BC),
        Color(0xFF26C6DA), Color(0xFFFF7043), Color(0xFF8D6E63), Color(0xFFEC407A), Color(0xFF9CCC65)
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }.map { it.position }
                            points.clear()
                            points.addAll(pressed)
                            if (pressed.size > maxSeen) maxSeen = pressed.size
                            if (pressed.isEmpty()) break
                        }
                    }
                }
        ) {
            points.forEachIndexed { i, p ->
                drawCircle(colors[i % colors.size], radius = 70f, center = p)
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.checkup_multitouch_count, points.size, maxSeen),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
        PassFailBar(Modifier.align(Alignment.BottomCenter), finish, onDark = true)
    }
}

// ------------------------------------------------------------------ display colours

@Composable
private fun DisplayColorTest(finish: (CheckStatus?) -> Unit) {
    val colors = listOf(
        Color.Red, Color.Green, Color.Blue, Color.White, Color.Black, Color(0xFF808080)
    )
    var index by remember { mutableIntStateOf(0) }
    val done = index >= colors.size
    Box(
        Modifier
            .fillMaxSize()
            .background(if (done) Color.Black else colors[index])
            .clickable(enabled = !done) { index++ }
    ) {
        if (done) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.checkup_display_question),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { index = 0 }) { Text(stringResource(R.string.checkup_again)) }
            }
            PassFailBar(Modifier.align(Alignment.BottomCenter), finish, onDark = true)
        }
    }
}

@Composable
private fun PassFailBar(modifier: Modifier, finish: (CheckStatus?) -> Unit, onDark: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = { finish(CheckStatus.FAIL) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.check_fail), color = if (onDark) Color.White else Color.Unspecified)
        }
        Button(onClick = { finish(CheckStatus.PASS) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.check_pass))
        }
    }
}

// ------------------------------------------------------------------ inline tests

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineTest(
    test: CheckupTest,
    volumeKeysSeen: Set<Int>,
    onCaptureVolumeKeys: (Boolean) -> Unit,
    finish: (CheckStatus?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(checkupTitle(test))) },
                navigationIcon = {
                    IconButton(onClick = { finish(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(checkupHint(test)), style = MaterialTheme.typography.bodyLarge)

            when (test) {
                CheckupTest.SPEAKER, CheckupTest.EARPIECE -> {
                    val earpiece = test == CheckupTest.EARPIECE
                    Button(onClick = {
                        scope.launch {
                            val ok = CheckupHardware.playTone(context, earpiece)
                            if (!ok) status = context.getString(R.string.checkup_unavailable)
                        }
                    }) { Text(stringResource(R.string.checkup_play)) }
                }
                CheckupTest.VIBRATION -> {
                    Button(onClick = {
                        if (!CheckupHardware.vibrate(context)) status = context.getString(R.string.checkup_unavailable)
                    }) { Text(stringResource(R.string.checkup_vibrate)) }
                }
                CheckupTest.FLASHLIGHT -> FlashlightPanel(context) { status = it }
                CheckupTest.BUTTONS -> {
                    DisposableEffect(Unit) {
                        onCaptureVolumeKeys(true)
                        onDispose { onCaptureVolumeKeys(false) }
                    }
                    val up = android.view.KeyEvent.KEYCODE_VOLUME_UP in volumeKeysSeen
                    val down = android.view.KeyEvent.KEYCODE_VOLUME_DOWN in volumeKeysSeen
                    SeenRow(stringResource(R.string.checkup_volume_up), up)
                    SeenRow(stringResource(R.string.checkup_volume_down), down)
                    LaunchedEffect(up, down) { if (up && down) { delay(400); finish(CheckStatus.PASS) } }
                }
                CheckupTest.PROXIMITY -> ProximityPanel(context, finish) { status = it }
                CheckupTest.LIGHT -> LightPanel(context, finish) { status = it }
                CheckupTest.MOTION -> MotionPanel(context, finish) { status = it }
                CheckupTest.CHARGING -> PollPanel(
                    seenLabel = stringResource(R.string.checkup_charger_seen),
                    check = { CheckupHardware.isPlugged(context) },
                    finish = finish
                )
                CheckupTest.HEADPHONES -> PollPanel(
                    seenLabel = stringResource(R.string.checkup_headphones_seen),
                    check = { CheckupHardware.headphonesConnected(context) },
                    finish = finish
                )
                else -> Unit
            }

            if (status.isNotEmpty()) {
                Text(status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.weight(1f))
            if (!test.automatic) {
                PassFailBar(Modifier, finish)
            }
            TextButton(onClick = { finish(CheckStatus.SKIPPED) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (test.automatic) R.string.checkup_skip_or_fail else R.string.checkup_skip))
            }
            if (test.automatic) {
                TextButton(onClick = { finish(CheckStatus.FAIL) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.checkup_mark_failed))
                }
            }
        }
    }
}

@Composable
private fun SeenRow(label: String, seen: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (seen) R.string.checkup_seen else R.string.checkup_waiting),
            color = if (seen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FlashlightPanel(context: Context, onError: (String) -> Unit) {
    val id = remember { CheckupHardware.torchCameraId(context) }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(id) { onDispose { if (id != null) CheckupHardware.setTorch(context, id, false) } }
    if (id == null) {
        Text(stringResource(R.string.checkup_no_flash), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Button(onClick = {
        if (CheckupHardware.setTorch(context, id, !on)) on = !on
        else onError(context.getString(R.string.checkup_unavailable))
    }) { Text(stringResource(if (on) R.string.checkup_torch_off else R.string.checkup_torch_on)) }
}

/** Registers one sensor for the lifetime of the panel; reports missing hardware. */
@Composable
private fun SensorListener(context: Context, type: Int, onMissing: () -> Unit, onEvent: (FloatArray) -> Unit) {
    DisposableEffect(type) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(type)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onEvent(event.values.clone())
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor == null) onMissing() else sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
}

@Composable
private fun ProximityPanel(context: Context, finish: (CheckStatus?) -> Unit, onError: (String) -> Unit) {
    var near by remember { mutableStateOf(false) }
    var far by remember { mutableStateOf(false) }
    var maxRange by remember { mutableFloatStateOf(0f) }
    val missing = stringResource(R.string.checkup_no_sensor)
    LaunchedEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        maxRange = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.maximumRange ?: 0f
    }
    SensorListener(context, Sensor.TYPE_PROXIMITY, onMissing = { onError(missing) }) { v ->
        // Most proximity sensors are binary: 0 for near, maxRange for far.
        if (v[0] < maxRange.coerceAtLeast(1f)) near = true else far = true
    }
    SeenRow(stringResource(R.string.checkup_near), near)
    SeenRow(stringResource(R.string.checkup_far), far)
    LaunchedEffect(near, far) { if (near && far) { delay(400); finish(CheckStatus.PASS) } }
}

@Composable
private fun LightPanel(context: Context, finish: (CheckStatus?) -> Unit, onError: (String) -> Unit) {
    var lux by remember { mutableFloatStateOf(-1f) }
    var min by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var max by remember { mutableFloatStateOf(0f) }
    val missing = stringResource(R.string.checkup_no_sensor)
    SensorListener(context, Sensor.TYPE_LIGHT, onMissing = { onError(missing) }) { v ->
        lux = v[0]
        if (v[0] < min) min = v[0]
        if (v[0] > max) max = v[0]
    }
    Text(
        if (lux < 0) stringResource(R.string.checkup_waiting) else stringResource(R.string.checkup_lux, lux),
        style = MaterialTheme.typography.headlineMedium
    )
    val passed = min != Float.MAX_VALUE && CheckupResults.lightPasses(min, max)
    LaunchedEffect(passed) { if (passed) { delay(400); finish(CheckStatus.PASS) } }
}

@Composable
private fun MotionPanel(context: Context, finish: (CheckStatus?) -> Unit, onError: (String) -> Unit) {
    val mins = remember { FloatArray(3) { Float.MAX_VALUE } }
    val maxs = remember { FloatArray(3) { -Float.MAX_VALUE } }
    var progress by remember { mutableIntStateOf(0) }
    val missing = stringResource(R.string.checkup_no_sensor)
    SensorListener(context, Sensor.TYPE_ACCELEROMETER, onMissing = { onError(missing) }) { v ->
        for (i in 0 until 3) {
            if (v[i] < mins[i]) mins[i] = v[i]
            if (v[i] > maxs[i]) maxs[i] = v[i]
        }
        val spans = FloatArray(3) { maxs[it] - mins[it] }
        progress = spans.count { it >= 12f }
        if (CheckupResults.motionPasses(spans)) progress = 3
    }
    Text(stringResource(R.string.checkup_motion_progress, progress.coerceAtMost(2), 2), style = MaterialTheme.typography.titleMedium)
    LaunchedEffect(progress) { if (progress >= 3) { delay(400); finish(CheckStatus.PASS) } }
}

/** Polls [check] once a second and passes when it turns true. */
@Composable
private fun PollPanel(seenLabel: String, check: () -> Boolean, finish: (CheckStatus?) -> Unit) {
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (!seen) {
            seen = check()
            if (!seen) delay(1000)
        }
        delay(600)
        finish(CheckStatus.PASS)
    }
    SeenRow(seenLabel, seen)
}
