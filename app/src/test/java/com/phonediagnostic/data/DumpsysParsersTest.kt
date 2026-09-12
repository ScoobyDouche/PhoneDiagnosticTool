package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DumpsysParsersTest {

    private val meminfo = """
        Applications Memory Usage (in Kilobytes):
        Uptime: 123 Realtime: 456

        Total PSS by process:
            445,876K: com.google.android.gms.persistent (pid 3990 / activities)
            301,000K: system (pid 900)
              1,234K: com.phonediagnostic (pid 4321 / activities)

        Total PSS by OOM adjustment:
            123,000K: Native
    """.trimIndent()

    private val cpuinfo = """
        Load: 3.1 / 2.9 / 2.7
        CPU usage from 12ms to 5000ms ago:
          18% 3990/com.google.android.gms.persistent: 10% user + 8% kernel
           4.5% 900/system: 2% user + 2.5% kernel
          100% TOTAL: 40% user + 60% kernel
    """.trimIndent()

    @Test
    fun parseMeminfoPss_readsOnlyTheByProcessBlock() {
        val pss = DumpsysParsers.parseMeminfoPss(meminfo)
        assertEquals(3, pss.size)
        assertEquals(445_876L, pss[3990]?.pssKb)
        assertEquals("com.google.android.gms.persistent", pss[3990]?.name)
        assertEquals("system", pss[900]?.name)
        assertEquals(1_234L, pss[4321]?.pssKb)
        // The OOM-adjustment block must not leak in.
        assertTrue(pss.values.none { it.name == "Native" })
    }

    @Test
    fun parseCpuinfo_readsPerProcessAndSkipsTotal() {
        val cpu = DumpsysParsers.parseCpuinfo(cpuinfo)
        assertEquals(18f, cpu[3990])
        assertEquals(4.5f, cpu[900])
        // "TOTAL" has no pid/ token, so it is skipped.
        assertEquals(2, cpu.size)
    }

    @Test
    fun parsers_returnEmptyOnGarbage() {
        assertTrue(DumpsysParsers.parseMeminfoPss("nothing useful here").isEmpty())
        assertTrue(DumpsysParsers.parseCpuinfo("nothing useful here").isEmpty())
        assertNull(DumpsysParsers.parseMeminfoPss("").get(1))
    }
}
