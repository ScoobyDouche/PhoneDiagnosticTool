package com.phonediagnostic.data

import com.phonediagnostic.data.elevated.ElevatedShell
import java.text.SimpleDateFormat
import java.util.Locale

enum class StabilityKind { APP_CRASH, NATIVE_CRASH, ANR, SYSTEM_RESTART, KERNEL }

data class StabilityEvent(
    val timeMs: Long,
    val kind: StabilityKind,
    /** Raw DropBox tag, e.g. data_app_crash. */
    val tag: String,
    /** Process or package named in the entry's first line, when there is one. */
    val process: String?
)

data class StabilityReport(
    val events: List<StabilityEvent>,
    /** False when no elevated shell was available, so nothing could be read. */
    val available: Boolean
) {
    fun count(kind: StabilityKind) = events.count { it.kind == kind }

    /** Processes with the most crashes and ANRs, most first. */
    fun topOffenders(limit: Int = 5): List<Pair<String, Int>> =
        events.filter { it.process != null && it.kind != StabilityKind.KERNEL }
            .groupingBy { it.process!! }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key to it.value }
}

/**
 * Crash, ANR and restart history from Android's DropBox — the same store bug
 * reports draw on. Only the shell user or root can list it, so this needs
 * elevated access. Reads `dumpsys dropbox` (list mode: one header line per
 * entry plus a one-line preview), never the full entry text.
 */
object StabilityProbe {

    const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    fun collect(shell: ElevatedShell?, nowMs: Long = System.currentTimeMillis()): StabilityReport {
        if (shell == null) return StabilityReport(emptyList(), available = false)
        val out = shell.exec("dumpsys dropbox") ?: return StabilityReport(emptyList(), available = true)
        val events = parse(out).filter { nowMs - it.timeMs in 0..WINDOW_MS }.sortedByDescending { it.timeMs }
        return StabilityReport(events, available = true)
    }

    fun kindOf(tag: String): StabilityKind? = when {
        tag == "SYSTEM_TOMBSTONE" || tag.endsWith("_native_crash") -> StabilityKind.NATIVE_CRASH
        tag == "system_server_crash" || tag == "system_server_watchdog" ||
            tag == "SYSTEM_RESTART" || tag == "system_server_anr" -> StabilityKind.SYSTEM_RESTART
        tag.endsWith("_crash") -> StabilityKind.APP_CRASH
        tag.endsWith("_anr") -> StabilityKind.ANR
        tag == "SYSTEM_LAST_KMSG" || tag == "SYSTEM_RECOVERY_KMSG" || tag.startsWith("SYSTEM_FSCK") ||
            tag == "kernel_panic" -> StabilityKind.KERNEL
        else -> null
    }

    /** `2026-11-20 09:00:00 data_app_crash (text, 2342 bytes)` */
    private val HEADER = Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\s+(\\S+)\\s+\\(")
    private val PROCESS = Regex("(?:Process|Package|Cmd line|Cmdline|pid: \\d+, tid: \\d+, name: \\S+\\s+>>>)\\s*:?\\s*([A-Za-z0-9_.:/-]+)")

    /** Pure, for tests. Local time, as dumpsys prints it. */
    fun parse(dump: String): List<StabilityEvent> {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val result = ArrayList<StabilityEvent>()
        val lines = dump.lines()
        for ((i, line) in lines.withIndex()) {
            val m = HEADER.find(line) ?: continue
            val tag = m.groupValues[2]
            val kind = kindOf(tag) ?: continue
            val time = runCatching { fmt.parse(m.groupValues[1])?.time }.getOrNull() ?: continue
            // The preview, if any, is the next indented line.
            val preview = lines.getOrNull(i + 1)?.takeIf { it.startsWith(" ") && HEADER.find(it) == null }
            val process = preview?.let { PROCESS.find(it)?.groupValues?.get(1) }
                ?.takeIf { kind != StabilityKind.KERNEL }
            result += StabilityEvent(time, kind, tag, process)
        }
        return result
    }
}
