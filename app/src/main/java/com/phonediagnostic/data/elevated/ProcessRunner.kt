package com.phonediagnostic.data.elevated

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs a process and returns its stdout, giving up after [timeoutMs].
 *
 * stdout is drained on its own thread so the deadline is enforced even if the
 * command never writes or never exits — a `su` waiting on its grant prompt, or
 * a sysfs node that blocks. Reading first and waiting afterwards (the earlier
 * approach) meant the timeout only started once the read had already returned,
 * so a hung command held up every collect behind it.
 */
internal object ProcessRunner {

    /** Caps what is kept from a runaway command; sysfs reads are a few bytes. */
    private const val MAX_OUTPUT_CHARS = 256 * 1024

    fun run(command: List<String>, timeoutMs: Long): String? {
        var process: Process? = null
        return try {
            val p = ProcessBuilder(command).redirectErrorStream(false).start().also { process = it }
            runCatching { p.outputStream.close() }
            val out = StringBuilder()
            val reader = thread(isDaemon = true, name = "elevated-stdout") {
                runCatching {
                    p.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(4096)
                        while (true) {
                            val n = r.read(buf)
                            if (n < 0) break
                            synchronized(out) {
                                if (out.length < MAX_OUTPUT_CHARS) out.append(buf, 0, n)
                            }
                        }
                    }
                }
            }
            // stderr is discarded but must be drained, or a chatty command can block on it.
            thread(isDaemon = true, name = "elevated-stderr") {
                runCatching { p.errorStream.use { it.readBytes() } }
            }
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) return null
            // The process has exited; give the reader a moment to hit EOF.
            reader.join(500)
            synchronized(out) { out.toString() }
        } catch (_: Exception) {
            null
        } finally {
            process?.let { runCatching { it.destroyForcibly() } }
        }
    }
}
