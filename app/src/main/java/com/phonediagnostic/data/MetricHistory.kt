package com.phonediagnostic.data

import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/**
 * One sampled point in time. Deliberately tiny — this is written on every
 * background sample and kept for a day.
 */
data class MetricSample(
    val timestampMs: Long,
    val batteryPct: Int,
    val batteryTempC: Float,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val charging: Boolean
) {
    val ramPercent: Int
        get() = if (ramTotalMb > 0) ((ramUsedMb * 100) / ramTotalMb).toInt() else 0
}

/**
 * Rolling history of battery / thermal / RAM samples, so the app can draw
 * trends instead of only printing log lines.
 *
 * Same append-only file strategy as [DiagnosticLog]: one short CSV line per
 * sample, full rewrite only when enough samples have aged out.
 */
class MetricHistory(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)

    private val lock = Any()
    private val samples = ArrayList<MetricSample>(MAX_SAMPLES / 4)
    private var droppedSinceCompact = 0

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "metric-history").apply { isDaemon = true }
    }

    init {
        load()
    }

    /**
     * Records a sample, unless the previous one is younger than [MIN_INTERVAL_MS].
     * Foreground refreshes tick every few seconds; the history only needs a
     * point every half minute.
     *
     * @return true when the sample was stored.
     */
    fun record(sample: MetricSample): Boolean {
        val line: String
        val needsCompaction: Boolean
        synchronized(lock) {
            val last = samples.lastOrNull()
            if (last != null && sample.timestampMs - last.timestampMs < MIN_INTERVAL_MS) {
                return false
            }
            samples.add(sample)
            while (samples.size > MAX_SAMPLES) {
                samples.removeAt(0)
                droppedSinceCompact++
            }
            needsCompaction = droppedSinceCompact >= COMPACT_THRESHOLD
            if (needsCompaction) droppedSinceCompact = 0
            line = encode(sample)
        }
        if (needsCompaction) {
            io.execute { rewriteFile() }
        } else {
            io.execute { runCatching { file.appendText(line + "\n") } }
        }
        return true
    }

    fun snapshot(): List<MetricSample> = synchronized(lock) { samples.toList() }

    fun clear() {
        synchronized(lock) {
            samples.clear()
            droppedSinceCompact = 0
        }
        // Delete straight away so the data is gone even if the process dies here,
        // then again behind anything still queued, so an in-flight append cannot
        // resurrect the file.
        runCatching { file.delete() }
        io.execute { runCatching { file.delete() } }
    }

    private fun encode(s: MetricSample): String = listOf(
        s.timestampMs.toString(),
        s.batteryPct.toString(),
        // Locale-independent on purpose: this is a data file, not display text.
        s.batteryTempC.toString(),
        s.ramUsedMb.toString(),
        s.ramTotalMb.toString(),
        if (s.charging) "1" else "0"
    ).joinToString(",")

    private fun decode(line: String): MetricSample? {
        val parts = line.split(",")
        if (parts.size < 6) return null
        return MetricSample(
            timestampMs = parts[0].toLongOrNull() ?: return null,
            batteryPct = parts[1].toIntOrNull() ?: return null,
            batteryTempC = parts[2].toFloatOrNull() ?: return null,
            ramUsedMb = parts[3].toLongOrNull() ?: return null,
            ramTotalMb = parts[4].toLongOrNull() ?: return null,
            charging = parts[5] == "1"
        )
    }

    private fun rewriteFile() {
        val current = snapshot()
        runCatching {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(current.joinToString("\n", postfix = "\n") { encode(it) })
            if (!temp.renameTo(file)) {
                file.writeText(current.joinToString("\n", postfix = "\n") { encode(it) })
                temp.delete()
            }
        }
    }

    private fun load() {
        val restored = runCatching {
            if (!file.exists()) emptyList() else file.readLines().mapNotNull { decode(it) }
        }.getOrDefault(emptyList())

        synchronized(lock) {
            samples.clear()
            samples.addAll(restored.takeLast(MAX_SAMPLES))
        }
        if (restored.size > MAX_SAMPLES) {
            io.execute { rewriteFile() }
        }
    }

    companion object {
        private const val FILE_NAME = "metric_history.csv"

        /** 24 h at one sample every 30 s. ~120 KB of text when full. */
        const val MAX_SAMPLES = 2880

        /** Ignore samples offered more often than this. */
        private const val MIN_INTERVAL_MS = 25_000L

        private const val COMPACT_THRESHOLD = 200

        @Volatile
        private var instance: MetricHistory? = null

        fun get(context: Context): MetricHistory {
            return instance ?: synchronized(this) {
                instance ?: MetricHistory(context.applicationContext).also { instance = it }
            }
        }
    }
}
