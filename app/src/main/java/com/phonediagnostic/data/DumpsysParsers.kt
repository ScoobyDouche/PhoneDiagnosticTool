package com.phonediagnostic.data

/**
 * Pure parsers for the `dumpsys` text the elevated process list relies on.
 * Kept free of Android types so they can be unit-tested against captured
 * output — the regexes are the part most likely to drift across OS versions.
 */
internal object DumpsysParsers {

    data class PssEntry(val name: String, val pssKb: Long)

    // "  445,876K: com.foo (pid 3990 / activities)"
    private val PSS_LINE = Regex("""^\s*([\d,]+)K:\s+(.+?)\s+\(pid (\d+)""")
    // "  12% 1234/com.foo: 4% user + 8% kernel"
    private val CPU_LINE = Regex("""^\s*([\d.]+)%\s+(\d+)/(\S+?):""")

    /** Parses the "Total PSS by process:" block of `dumpsys meminfo`, keyed by pid. */
    fun parseMeminfoPss(text: String): Map<Int, PssEntry> {
        val out = LinkedHashMap<Int, PssEntry>()
        var inSection = false
        for (line in text.lineSequence()) {
            if (!inSection) {
                if (line.trimStart().startsWith("Total PSS by process")) inSection = true
                continue
            }
            val trimmed = line.trim()
            // The block ends at a blank line or the next "Total … by" header.
            if (trimmed.isEmpty()) break
            val m = PSS_LINE.find(line)
            if (m == null) {
                if (trimmed.startsWith("Total ")) break else continue
            }
            val pssKb = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
            val pid = m.groupValues[3].toIntOrNull() ?: continue
            out[pid] = PssEntry(m.groupValues[2].trim(), pssKb)
        }
        return out
    }

    /** Parses per-process CPU percentages from `dumpsys cpuinfo`, keyed by pid. */
    fun parseCpuinfo(text: String): Map<Int, Float> {
        val out = HashMap<Int, Float>()
        for (line in text.lineSequence()) {
            val m = CPU_LINE.find(line) ?: continue
            val pct = m.groupValues[1].toFloatOrNull() ?: continue
            val pid = m.groupValues[2].toIntOrNull() ?: continue
            out[pid] = pct
        }
        return out
    }
}
