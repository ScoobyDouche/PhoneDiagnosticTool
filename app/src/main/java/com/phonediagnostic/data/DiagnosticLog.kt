package com.phonediagnostic.data

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Ring buffer log — never grows past [MAX_ENTRIES].
 * Persisted to SharedPreferences as a JSON array.
 *
 * Size estimate at full capacity (~5000 lines × ~100–150 chars):
 * about 0.5–1 MB on disk. Still tiny vs photos/apps.
 */
class DiagnosticLog(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val entries = CopyOnWriteArrayList<String>()

    init {
        load()
    }

    fun append(message: String) {
        val line = "${timestamp()}  $message"
        synchronized(entries) {
            entries.add(line)
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
            persist()
        }
    }

    fun snapshot(): List<String> = synchronized(entries) { entries.toList() }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            prefs.edit().remove(KEY).apply()
        }
    }

    fun size(): Int = entries.size

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                entries.add(arr.getString(i))
            }
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
        } catch (_: Exception) {
            entries.clear()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        entries.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "diagnostic_log"
        private const val KEY = "lines"
        /** Cap — ~0.5–1 MB when full; oldest lines drop. */
        const val MAX_ENTRIES = 5000

        @Volatile
        private var instance: DiagnosticLog? = null

        fun get(context: Context): DiagnosticLog {
            return instance ?: synchronized(this) {
                instance ?: DiagnosticLog(context.applicationContext).also { instance = it }
            }
        }
    }
}
