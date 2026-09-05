package com.phonediagnostic.data

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext
import kotlin.system.measureNanoTime

data class StorageSpeedResult(
    val writeMbPerSec: Double,
    val readMbPerSec: Double,
    val sampleMb: Int
)

/**
 * Sequential read/write throughput of internal storage.
 *
 * Deliberately modest and short-lived: flash has finite write endurance, and a
 * diagnostic tool has no business burning it. One [SAMPLE_MB] file is written,
 * read back, and deleted.
 */
object StorageSpeedTester {

    /** Large enough to swamp per-call overhead, small enough not to hurt. */
    const val SAMPLE_MB = 64
    private const val CHUNK_BYTES = 1 shl 20
    private const val BYTES_PER_MB = 1024.0 * 1024.0

    /** Refuse to run if this little would be left afterwards. */
    private const val FREE_SPACE_HEADROOM_MB = 256

    class InsufficientSpace(val freeMb: Long) : Exception("Only $freeMb MB free")

    suspend fun run(context: Context): StorageSpeedResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "speedtest").apply { mkdirs() }
        val file = File(dir, "sample.bin")

        val stat = StatFs(context.cacheDir.absolutePath)
        val freeMb = (stat.availableBytes / (1024 * 1024))
        if (freeMb < SAMPLE_MB + FREE_SPACE_HEADROOM_MB) throw InsufficientSpace(freeMb)

        // One buffer of varied bytes, reused. Zeroes would let a compressing or
        // deduplicating layer return a throughput figure the device cannot
        // actually sustain on real data.
        val chunk = ByteArray(CHUNK_BYTES) { (it * 31 + 7).toByte() }

        try {
            val writeNs = measureNanoTime {
                FileOutputStream(file).use { out ->
                    repeat(SAMPLE_MB) {
                        out.write(chunk)
                    }
                    out.flush()
                    // Without this the figure is memory bandwidth into the page
                    // cache, not what the flash sustains.
                    out.fd.sync()
                }
            }
            coroutineContext.ensureActive()

            // Drop our own page-cache residency as far as an unprivileged app
            // can: reopen, and read into a fresh buffer.
            val readBuf = ByteArray(CHUNK_BYTES)
            val readNs = measureNanoTime {
                RandomAccessFile(file, "r").use { raf ->
                    var got = raf.read(readBuf)
                    while (got > 0) got = raf.read(readBuf)
                }
            }

            val mb = SAMPLE_MB.toDouble()
            StorageSpeedResult(
                writeMbPerSec = mb / (writeNs / 1_000_000_000.0),
                readMbPerSec = mb / (readNs / 1_000_000_000.0),
                sampleMb = SAMPLE_MB
            )
        } finally {
            // Never leave 64 MB of scratch behind, on any exit path.
            runCatching { file.delete() }
            runCatching { dir.delete() }
        }
    }
}
