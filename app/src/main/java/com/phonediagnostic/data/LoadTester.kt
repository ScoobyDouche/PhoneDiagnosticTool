package com.phonediagnostic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

data class LoadTestProgress(
    val running: Boolean,
    val durationSec: Int,
    val elapsedSec: Int,
    val operations: Long,
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
    val beforeRamUsedMb: Long,
    val afterRamUsedMb: Long,
    val beforeBatteryPct: Int,
    val afterBatteryPct: Int,
    val beforeTempC: Float,
    val afterTempC: Float,
    val summary: String
)

/**
 * Synthetic CPU load with live progress updates (task-manager style).
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

        fun sampleProgress(elapsed: Int, phase: String) {
            val snap = try {
                collector.collect(networkProbe = false)
            } catch (_: Exception) {
                null
            }
            onProgress(
                LoadTestProgress(
                    running = true,
                    durationSec = sec,
                    elapsedSec = elapsed,
                    operations = ops.get(),
                    ramUsedMb = snap?.memory?.usedRamMb ?: 0L,
                    batteryPct = snap?.battery?.level ?: 0,
                    tempC = snap?.battery?.temperature ?: 0f,
                    phase = phase
                )
            )
        }

        sampleProgress(0, "Starting")
        val before = collector.collect(networkProbe = false)
        val endAt = System.nanoTime() + durationMs * 1_000_000L
        val startMs = System.currentTimeMillis()

        // Progress sampler on a sibling coroutine
        val sampler = launch {
            var tick = 0
            while (isActive && !stopFlag.get()) {
                delay(1000L)
                tick++
                val elapsed = ((System.currentTimeMillis() - startMs) / 1000L).toInt().coerceAtMost(sec)
                sampleProgress(elapsed, "Stressing CPU")
            }
        }

        try {
            coroutineScope {
                (0 until threadCount).map {
                    async {
                        var local = 0L
                        var x = 1.000001
                        while (System.nanoTime() < endAt && !stopFlag.get()) {
                            x = sqrt(x * x + 1.000001)
                            local++
                            if (local and 0x3FFL == 0L) {
                                Thread.yield()
                            }
                        }
                        ops.addAndGet(local)
                    }
                }.awaitAll()
            }
        } finally {
            stopFlag.set(true)
            sampler.cancel()
        }

        sampleProgress(sec, "Cooling down")
        System.gc()
        delay(400)

        val after = collector.collect(networkProbe = false)
        val mins = sec / 60
        val summary = buildString {
            append("Load test ${mins}m × ${threadCount} threads · ")
            append("${ops.get()} ops · ")
            append("RAM ${before.memory.usedRamMb}→${after.memory.usedRamMb} MB · ")
            append("Bat ${before.battery.level}%→${after.battery.level}% · ")
            append(
                String.format(
                    "%.1f→%.1f°C",
                    before.battery.temperature,
                    after.battery.temperature
                )
            )
        }

        DiagnosticLog.get(context).append(summary)

        onProgress(
            LoadTestProgress(
                running = false,
                durationSec = sec,
                elapsedSec = sec,
                operations = ops.get(),
                ramUsedMb = after.memory.usedRamMb,
                batteryPct = after.battery.level,
                tempC = after.battery.temperature,
                phase = "Done"
            )
        )

        LoadTestResult(
            durationMs = durationMs,
            threads = threadCount,
            operations = ops.get(),
            beforeRamUsedMb = before.memory.usedRamMb,
            afterRamUsedMb = after.memory.usedRamMb,
            beforeBatteryPct = before.battery.level,
            afterBatteryPct = after.battery.level,
            beforeTempC = before.battery.temperature,
            afterTempC = after.battery.temperature,
            summary = summary
        )
    }
}
