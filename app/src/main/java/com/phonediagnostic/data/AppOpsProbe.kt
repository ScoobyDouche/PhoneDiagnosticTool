package com.phonediagnostic.data

import com.phonediagnostic.data.elevated.ElevatedShell

/** A sensor whose recent use the system records per app. */
enum class SensorUse { CAMERA, MICROPHONE, LOCATION }

/** When one app last used camera, microphone or location, as "how long ago". */
data class RecentUseEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    /** Milliseconds since last use, per sensor. Only sensors used inside the window appear. */
    val lastUsedAgoMs: Map<SensorUse, Long>
) {
    val mostRecentAgoMs: Long get() = lastUsedAgoMs.values.minOrNull() ?: Long.MAX_VALUE
}

/**
 * App-op reads that only the shell user or root can make. An ordinary app may
 * check app-ops for itself alone, so who can draw over other apps, install
 * apps or read usage — and when camera, microphone and location were last
 * used — is only visible with elevated access on.
 *
 * Every command here is read-only (`cmd appops query-op`, `dumpsys appops`).
 */
object AppOpsProbe {

    /** Special-access app-ops, mapped onto the categories the audit shows. */
    val SPECIAL_OPS: Map<String, SensitiveAccess> = linkedMapOf(
        "SYSTEM_ALERT_WINDOW" to SensitiveAccess.OVERLAY,
        "REQUEST_INSTALL_PACKAGES" to SensitiveAccess.INSTALL_APPS,
        "GET_USAGE_STATS" to SensitiveAccess.USAGE_ACCESS
    )

    private val USE_OPS: Map<String, SensorUse> = linkedMapOf(
        "CAMERA" to SensorUse.CAMERA,
        "RECORD_AUDIO" to SensorUse.MICROPHONE,
        "FINE_LOCATION" to SensorUse.LOCATION,
        "COARSE_LOCATION" to SensorUse.LOCATION
    )

    /** Recent-use entries older than this are left out. */
    const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    /** Packages explicitly allowed each special op, keyed by the audit category. */
    fun specialAccess(shell: ElevatedShell, userId: Int): Map<SensitiveAccess, Set<String>> =
        SPECIAL_OPS.entries.associate { (op, access) ->
            val out = shell.exec("cmd appops query-op --user $userId $op allow")
            access to parseQueryOp(out)
        }

    /** Package -> sensor -> ms since last use, within [RECENT_WINDOW_MS]. */
    fun recentUse(shell: ElevatedShell): Map<String, Map<SensorUse, Long>> {
        val result = HashMap<String, MutableMap<SensorUse, Long>>()
        for ((op, use) in USE_OPS) {
            val dump = shell.exec("dumpsys appops --op $op") ?: continue
            for ((pkg, ago) in parseLastAccess(dump)) {
                if (ago > RECENT_WINDOW_MS) continue
                val perPkg = result.getOrPut(pkg) { HashMap() }
                val prev = perPkg[use]
                if (prev == null || ago < prev) perPkg[use] = ago
            }
        }
        return result
    }

    /** `cmd appops query-op` prints one package per line, or "No operations." */
    fun parseQueryOp(output: String?): Set<String> =
        output.orEmpty().lineSequence()
            .map { it.trim() }
            .filter { PACKAGE_NAME.matches(it) }
            .toSet()

    private val PACKAGE_LINE = Regex("^\\s*Package (\\S+):\\s*$")

    /** Android 11+: `Access: [fg-s] 2026-11-21 10:00:00.000 (-5h1m2s3ms)`. */
    private val ACCESS_RELATIVE = Regex("Access:.*\\(-([0-9dhms]+)\\)")

    /** Android 9-10: `CAMERA: mode=allow; time=+21h18m31s437ms ago; ...`. */
    private val LEGACY_TIME = Regex("(?<![A-Za-z])time=\\+([0-9dhms]+) ago")

    private val DURATION_PART = Regex("(\\d+)(ms|d|h|m|s)")

    /**
     * Most recent access per package from a `dumpsys appops --op X` dump. The
     * format has changed between releases, so this looks only for the package
     * headers and the relative "how long ago" figures, and ignores rejections.
     */
    fun parseLastAccess(dump: String): Map<String, Long> {
        val result = HashMap<String, Long>()
        var pkg: String? = null
        for (line in dump.lineSequence()) {
            val header = PACKAGE_LINE.find(line)
            if (header != null) {
                pkg = header.groupValues[1]
                continue
            }
            if (line.trimStart().startsWith("Uid ")) {
                pkg = null
                continue
            }
            val current = pkg ?: continue
            val match = ACCESS_RELATIVE.find(line) ?: LEGACY_TIME.find(line) ?: continue
            val ago = parseDurationMs(match.groupValues[1]) ?: continue
            val prev = result[current]
            if (prev == null || ago < prev) result[current] = ago
        }
        return result
    }

    /** "1d2h3m4s5ms" -> milliseconds. Null if nothing in it parses. */
    fun parseDurationMs(text: String): Long? {
        var total = 0L
        var any = false
        for (m in DURATION_PART.findAll(text)) {
            val n = m.groupValues[1].toLongOrNull() ?: continue
            total += when (m.groupValues[2]) {
                "d" -> n * 86_400_000L
                "h" -> n * 3_600_000L
                "m" -> n * 60_000L
                "s" -> n * 1_000L
                else -> n
            }
            any = true
        }
        return if (any) total else null
    }
}
