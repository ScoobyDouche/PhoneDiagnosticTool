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
 * Brief synthetic load (CPU math) to see how the device responds.
 * Does not expand storage — only returns a result + one log line.
 */
object LoadTester {

    suspend fun run(
        context: Context,
        durationSec: Int = 5,
        threads: Int = 4
    ): LoadTestResult = withContext(Dispatchers.Default) {
        val collector = DeviceInfoCollector(context)
        val before = collector.collect(networkProbe = false)
        val ops = AtomicLong(0)
        val durationMs = (durationSec.coerceIn(2, 15) * 1000L)
        val threadCount = threads.coerceIn(1, 8)
        val endAt = System.nanoTime() + durationMs * 1_000_000L

        coroutineScope {
            (0 until threadCount).map {
                async {
                    var local = 0L
                    var x = 1.000001
                    while (System.nanoTime() < endAt) {
                        // Busy work — not optimized away easily
                        x = sqrt(x * x + 1.000001)
                        local++
                        if (local and 0x3FFL == 0L) {
                            // yield a tiny bit so the system can schedule
                            Thread.yield()
                        }
                    }
                    ops.addAndGet(local)
                }
            }.awaitAll()
        }

        // Encourage GC so after-RAM is meaningful
        System.gc()
        Thread.sleep(200)

        val after = collector.collect(networkProbe = false)
        val summary = buildString {
            append("Load test ${durationSec}s × ${threadCount} threads · ")
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
