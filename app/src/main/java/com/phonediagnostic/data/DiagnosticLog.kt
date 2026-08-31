package com.phonediagnostic.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Rotating log — never grows past [MAX_ENTRIES].
 *
 * Backed by an append-only file in internal storage, so writing one line costs
 * one small append instead of re-serialising the whole log. The file is only
 * rewritten in full when enough lines have aged out ([COMPACT_THRESHOLD]).
 *
 * Size at full capacity (~5000 lines x ~100-150 chars): about 0.5-1 MB.
 */
class DiagnosticLog(context: Context) {

    private val appContext = context.applicationContext
    private val logFile = File(appContext.filesDir, FILE_NAME)

    /** Guards [entries] and [droppedSinceCompact]. */
    private val lock = Any()
    private val entries = ArrayList<String>(MAX_ENTRIES / 4)
    private var droppedSinceCompact = 0

    /** All disk work happens here, in submission order, off the caller's thread. */
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "diagnostic-log").apply { isDaemon = true }
    }

    init {
        load()
    }

    fun append(message: String) {
        val line = "${timestamp()}  $message"
        val needsCompaction: Boolean
        synchronized(lock) {
            entries.add(line)
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
                droppedSinceCompact++
            }
            needsCompaction = droppedSinceCompact >= COMPACT_THRESHOLD
            if (needsCompaction) droppedSinceCompact = 0
        }
        if (needsCompaction) {
            io.execute { rewriteFile() }
        } else {
            io.execute { appendToFile(line) }
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            droppedSinceCompact = 0
        }
        // Delete straight away so the data is gone even if the process dies here,
        // then again behind anything still queued, so an in-flight append cannot
        // resurrect the file.
        runCatching { logFile.delete() }
        io.execute { runCatching { logFile.delete() } }
    }

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun appendToFile(line: String) {
        runCatching {
            logFile.appendText(line + "\n")
        }
    }

    private fun rewriteFile() {
        val current = snapshot()
        runCatching {
            val temp = File(logFile.parentFile, "$FILE_NAME.tmp")
            temp.writeText(current.joinToString("\n", postfix = "\n"))
            if (!temp.renameTo(logFile)) {
                logFile.writeText(current.joinToString("\n", postfix = "\n"))
                temp.delete()
            }
        }
    }

    private fun load() {
        val fromFile = runCatching {
            if (logFile.exists()) logFile.readLines().filter { it.isNotBlank() } else emptyList()
        }.getOrDefault(emptyList())

        val restored = if (fromFile.isEmpty()) migrateLegacyPrefs() else fromFile

        synchronized(lock) {
            entries.clear()
            entries.addAll(restored.takeLast(MAX_ENTRIES))
        }
        // Whatever we dropped while loading is not on disk yet; normalise the file once.
        if (restored.size > MAX_ENTRIES || (fromFile.isEmpty() && restored.isNotEmpty())) {
            io.execute { rewriteFile() }
        }
    }

    /**
     * Earlier versions stored the log as a JSON array in SharedPreferences.
     * Import it once, then drop it so it is not carried around twice.
     */
    private fun migrateLegacyPrefs(): List<String> {
        val prefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(LEGACY_KEY, null) ?: return emptyList()
        val imported = runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        prefs.edit().remove(LEGACY_KEY).apply()
        return imported
    }

    companion object {
        private const val FILE_NAME = "diagnostic_log.txt"
        private const val LEGACY_PREFS = "diagnostic_log"
        private const val LEGACY_KEY = "lines"

        /** Cap — ~0.5-1 MB when full; oldest lines drop. */
        const val MAX_ENTRIES = 5000

        /** Rewrite the file once this many lines have aged out. */
        private const val COMPACT_THRESHOLD = 250

        @Volatile
        private var instance: DiagnosticLog? = null

        fun get(context: Context): DiagnosticLog {
            return instance ?: synchronized(this) {
                instance ?: DiagnosticLog(context.applicationContext).also { instance = it }
            }
        }
    }
}
