package com.phonediagnostic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

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
 * Synthetic CPU load to see how the device responds under stress.
 * Supports 1 / 5 / 10 minute runs. One log line only — no storage growth.
 */
object LoadTester {

    /** Allowed durations in seconds: 1 min, 5 min, 10 min. */
    val ALLOWED_DURATIONS_SEC = listOf(60, 300, 600)

    suspend fun run(
        context: Context,
        durationSec: Int = 60,
        threads: Int = 4
    ): LoadTestResult = withContext(Dispatchers.Default) {
        val collector = DeviceInfoCollector(context)
        val before = collector.collect(networkProbe = false)
        val ops = AtomicLong(0)
        val sec = durationSec.coerceIn(60, 600)
        val durationMs = sec * 1000L
        val threadCount = threads.coerceIn(1, 8)
        val endAt = System.nanoTime() + durationMs * 1_000_000L

        coroutineScope {
            (0 until threadCount).map {
                async {
                    var local = 0L
                    var x = 1.000001
                    while (System.nanoTime() < endAt) {
                        x = sqrt(x * x + 1.000001)
                        local++
                        // Yield often so the UI / system can still schedule
                        if (local and 0x3FFL == 0L) {
                            Thread.yield()
                        }
                    }
                    ops.addAndGet(local)
                }
            }.awaitAll()
        }

        System.gc()
        Thread.sleep(300)

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
