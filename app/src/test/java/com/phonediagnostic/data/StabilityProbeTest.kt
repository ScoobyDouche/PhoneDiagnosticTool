package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StabilityProbeTest {

    private val dump = """
        Drop box contents: 5 entries
        Max entries: 1000

        2026-11-20 08:12:33 system_app_anr (compressed text, 4821 bytes)
            Process: com.android.systemui
        2026-11-20 09:00:00 data_app_crash (text, 2342 bytes)
            Process: com.foo.bar
        2026-11-20 09:05:00 data_app_crash (text, 2342 bytes)
            Process: com.foo.bar
        2026-11-20 10:00:00 SYSTEM_TOMBSTONE (compressed data, 9999 bytes)
        2026-11-20 11:00:00 SYSTEM_BOOT (text, 50 bytes)
            boot
        2026-11-21 01:00:00 system_server_watchdog (text, 100 bytes)
            Subject: Blocked in handler
    """.trimIndent()

    @Test
    fun parsesKnownTagsAndSkipsOthers() {
        val events = StabilityProbe.parse(dump)
        assertEquals(5, events.size)
        assertEquals(StabilityKind.ANR, events[0].kind)
        assertEquals("com.android.systemui", events[0].process)
        assertEquals(StabilityKind.NATIVE_CRASH, events[3].kind)
        assertNull(events[3].process)
        assertEquals(StabilityKind.SYSTEM_RESTART, events[4].kind)
    }

    @Test
    fun topOffendersCountsPerProcess() {
        val report = StabilityReport(StabilityProbe.parse(dump), available = true)
        assertEquals("com.foo.bar" to 2, report.topOffenders().first())
        assertEquals(2, report.count(StabilityKind.APP_CRASH))
    }

    @Test
    fun tagMapping() {
        assertEquals(StabilityKind.NATIVE_CRASH, StabilityProbe.kindOf("data_app_native_crash"))
        assertEquals(StabilityKind.APP_CRASH, StabilityProbe.kindOf("system_app_crash"))
        assertEquals(StabilityKind.SYSTEM_RESTART, StabilityProbe.kindOf("system_server_crash"))
        assertEquals(StabilityKind.KERNEL, StabilityProbe.kindOf("SYSTEM_LAST_KMSG"))
        assertNull(StabilityProbe.kindOf("data_app_strictmode"))
    }
}
