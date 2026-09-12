package com.phonediagnostic.data.elevated

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs commands through `su`. This reaches nodes that even the shell user
 * cannot (GPU load counters, flash-wear estimates, dropping caches), at the
 * cost of a rooted device.
 *
 * The first [exec] triggers the superuser manager's grant prompt on most
 * setups; [isAvailable] only checks for the binary and never invokes it, so
 * detection stays silent.
 */
class RootShell : ElevatedShell {

    override val tier: AccessTier = AccessTier.ROOT

    override fun exec(command: String): String? {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()
            // sysfs reads are tiny, so reading fully before waiting cannot
            // deadlock on a full pipe buffer.
            val out = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) return null
            out.trim().ifBlank { null }
        } catch (_: Exception) {
            null
        } finally {
            process?.let { runCatching { it.destroy() } }
        }
    }

    companion object {
        private const val EXEC_TIMEOUT_MS = 4_000L

        private val SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/su/bin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/system/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )

        /** True if a `su` binary is present. Does not invoke it, so no prompt fires. */
        fun isAvailable(): Boolean {
            if (SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) }) return true
            val path = System.getenv("PATH").orEmpty()
            return path.split(':').any { dir ->
                dir.isNotBlank() && runCatching { File(dir, "su").exists() }.getOrDefault(false)
            }
        }
    }
}
