package com.phonediagnostic.data.elevated

/**
 * How the app is running privileged reads, if at all.
 *
 * The whole feature is opt-in: [NONE] is the default and keeps the app a
 * no-elevation diagnostic tool. [SHIZUKU] runs helper commands as the shell
 * user (UID 2000) via a Shizuku-bound service — the same reach as `adb shell`,
 * no root, and nothing permanent to the device. [ROOT] runs them via `su`,
 * which reaches sysfs nodes even the shell user cannot.
 */
enum class AccessTier {
    NONE,
    SHIZUKU,
    ROOT;

    companion object {
        fun fromName(name: String?): AccessTier =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * A channel for running a shell command with elevated privileges. Both the
 * Shizuku and root backends reduce to "run this and give me stdout", which is
 * all the privileged sysfs reads need, so the surface is deliberately tiny.
 *
 * Every method must be safe to call off the main thread and must never throw:
 * a failure — no binder, denied read, timeout — comes back as null so callers
 * fall through to their existing non-elevated path.
 */
interface ElevatedShell {
    val tier: AccessTier

    /** Runs [command] as the elevated user; returns trimmed stdout, or null on any failure. */
    fun exec(command: String): String?

    /**
     * Reads a single file as the elevated user. Returns its trimmed contents,
     * or null if it is empty or unreadable. Paths here are fixed sysfs
     * constants, never user input, so they are passed to `cat` unquoted.
     */
    fun readFileOrNull(path: String): String? =
        exec("cat $path")?.trim()?.takeIf { it.isNotBlank() }
}
