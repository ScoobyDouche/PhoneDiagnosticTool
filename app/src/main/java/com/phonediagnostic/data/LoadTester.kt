package com.phonediagnostic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

data class LoadTestProgress(
    val running: Boolean,
    val durationSec: Int,
    val elapsedSec: Int,
    val operations: Long,
    val opsPerSec: Long,
    val threads: Int,
    val ramUsedMb: Long,
    val batteryPct: Int,
    val tempC: Float,
    val phase: String
) {
    val fraction: Float
        get() = if (durationSec <= 0) 0f else (elapsedSec.toFloat() / durationSec).coerceIn(0f, 1f)
}

data class LoadTestResult(
    val durationMs: Long,
    val threads: Int,
    val operations: Long,
    /** Thousands of operations per second across all worker threads. */
    val score: Long,
    val beforeRamUsedMb: Long,
    val afterRamUsedMb: Long,
    val beforeBatteryPct: Int,
    val afterBatteryPct: Int,
    val beforeTempC: Float,
    val afterTempC: Float,
    /** Highest battery temperature seen while the test ran. */
    val peakTempC: Float,
    val summary: String
)

/**
 * Synthetic CPU load with live progress updates (task-manager style).
 *
 * The workers run on plain threads rather than coroutines: they never suspend,
 * so scheduling them onto [Dispatchers.Default] starved the sampler that is
 * supposed to report progress.
 */
object LoadTester {

    val ALLOWED_DURATIONS_SEC = listOf(60, 300, 600)

    suspend fun run(
        context: Context,
        durationSec: Int = 60,
        threads: Int = 4,
        onProgress: (LoadTestProgress) -> Unit = {}
    ): LoadTestResult = withContext(Dispatchers.Default) {
        val collector = DeviceInfoCollector(context)
        val sec = durationSec.coerceIn(60, 600)
        val durationMs = sec * 1000L
        val threadCount = threads.coerceIn(1, 8)
        val ops = AtomicLong(0)
        val stopFlag = AtomicBoolean(false)

        // Battery + memory only. A full collect here would spin up an EGL context,
        // enumerate cameras and wake every sensor once per second, which both
        // distorts the measurement and burns the battery we are reporting on.
        val before = collector.collectSample()
        var peakTempC = before.batteryTempC

        fun emit(elapsed: Int, phase: String, running: Boolean, sample: MetricSample) {
            if (sample.batteryTempC > peakTempC) peakTempC = sample.batteryTempC
            val total = ops.get()
            onProgress(
                LoadTestProgress(
                    running = running,
                    durationSec = sec,
                    elapsedSec = elapsed,
                    operations = total,
                    opsPerSec = if (elapsed > 0) total / elapsed else 0L,
                    threads = threadCount,
                    ramUsedMb = sample.ramUsedMb,
                    batteryPct = sample.batteryPct,
                    tempC = sample.batteryTempC,
                    phase = phase
                )
            )
        }

        emit(0, "Starting", running = true, sample = before)

        val startMs = System.currentTimeMillis()
        val endAt = System.nanoTime() + durationMs * 1_000_000L

        val workers = (0 until threadCount).map { index ->
            Thread({
                var local = 0L
                var x = 1.000001
                while (System.nanoTime() < endAt && !stopFlag.get()) {
                    x = sqrt(x * x + 1.000001)
                    local++
                    // Publish periodically so the live counter actually moves;
                    // it previously only landed once the worker finished.
                    if (local % PUBLISH_EVERY == 0L) {
                        ops.addAndGet(PUBLISH_EVERY)
                    }
                }
                // The tail that never reached a publish boundary.
                ops.addAndGet(local % PUBLISH_EVERY)
            }, "load-test-$index").apply {
                isDaemon = true
                start()
            }
        }

        try {
            while (isActive && System.nanoTime() < endAt && !stopFlag.get()) {
                delay(PROGRESS_INTERVAL_MS)
                val elapsed = ((System.currentTimeMillis() - startMs) / 1000L)
                    .toInt().coerceIn(0, sec)
                emit(elapsed, "Stressing CPU", running = true, sample = collector.collectSample())
            }
        } finally {
            stopFlag.set(true)
            workers.forEach { worker ->
                runCatching { worker.join(WORKER_JOIN_MS) }
            }
        }

        emit(sec, "Cooling down", running = true, sample = collector.collectSample())
        delay(COOLDOWN_MS)

        val after = collector.collectSample()
        if (after.batteryTempC > peakTempC) peakTempC = after.batteryTempC

        val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(1L)
        val totalOps = ops.get()
        val score = totalOps / elapsedSec / 1000L

        val summary = buildString {
            append("Load test ${sec / 60}m x $threadCount threads - ")
            append("${score}k ops/s - ")
            append("RAM ${before.ramUsedMb}->${after.ramUsedMb} MB - ")
            append("Bat ${before.batteryPct}%->${after.batteryPct}% - ")
            append(
                String.format(
                    Locale.US,
                    "%.1f->%.1f C (peak %.1f)",
                    before.batteryTempC,
                    after.batteryTempC,
                    peakTempC
                )
            )
        }

        DiagnosticLog.get(context).append(summary)

        emit(sec, "Done", running = false, sample = after)

        LoadTestResult(
            durationMs = durationMs,
            threads = threadCount,
            operations = totalOps,
            score = score,
            beforeRamUsedMb = before.ramUsedMb,
            afterRamUsedMb = after.ramUsedMb,
            beforeBatteryPct = before.batteryPct,
            afterBatteryPct = after.batteryPct,
            beforeTempC = before.batteryTempC,
            afterTempC = after.batteryTempC,
            peakTempC = peakTempC,
            summary = summary
        )
    }

    /** Workers batch their counter updates this coarsely to avoid contention. */
    private const val PUBLISH_EVERY = 65_536L
    private const val PROGRESS_INTERVAL_MS = 1000L
    private const val WORKER_JOIN_MS = 2000L
    private const val COOLDOWN_MS = 400L
}
