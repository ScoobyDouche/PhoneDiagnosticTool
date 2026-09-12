package com.phonediagnostic.data.elevated

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * The privileged helper Shizuku instantiates in a separate process running as
 * the shell user (UID 2000). Because it lives in the shell SELinux domain, the
 * `sh -c` command it runs can read sysfs nodes the app's own process cannot.
 *
 * Shizuku constructs a UserService reflectively, preferring a `(Context)`
 * constructor and falling back to a no-arg one, so both are provided.
 */
class ElevatedService() : IElevatedService.Stub() {

    @Suppress("unused")
    constructor(context: Context) : this()

    override fun destroy() {
        // Called by Shizuku when the binding is torn down. End the helper
        // process so it does not linger.
        exitProcess(0)
    }

    override fun exec(command: String?): String? {
        if (command.isNullOrBlank()) return null
        var process: Process? = null
        return try {
            process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) null else out
        } catch (_: Exception) {
            null
        } finally {
            process?.let { runCatching { it.destroy() } }
        }
    }

    private companion object {
        const val EXEC_TIMEOUT_MS = 4_000L
    }
}

/** Client-side [ElevatedShell] that forwards commands over the bound Shizuku helper. */
class ShizukuShell(private val service: IElevatedService) : ElevatedShell {

    override val tier: AccessTier = AccessTier.SHIZUKU

    override fun exec(command: String): String? =
        try {
            service.exec(command)?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
}
