package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsProbeTest {

    @Test
    fun durationParsesAllUnits() {
        assertEquals(5_000L, AppOpsProbe.parseDurationMs("5s"))
        assertEquals(3_723_004L, AppOpsProbe.parseDurationMs("1h2m3s4ms"))
        assertEquals(86_400_000L + 60_000L, AppOpsProbe.parseDurationMs("1d1m"))
        assertEquals(250L, AppOpsProbe.parseDurationMs("250ms"))
        assertNull(AppOpsProbe.parseDurationMs("abc"))
    }

    @Test
    fun modernDumpTakesMostRecentAccessAndIgnoresRejects() {
        val dump = """
            Current AppOps Service state:
              Uid u0a215:
                state=cch
                Package com.example.cam:
                  CAMERA (allow):
                    null=[
                      Access: [fg-s] 2026-11-21 10:00:00.000 (-5h1m2s3ms) duration=+2s
                      Access: [top-s] 2026-11-21 14:00:00.000 (-1h0m0s0ms) duration=+1s
                      Reject: [bg-s] 2026-11-21 15:00:00.000 (-1m0s0ms)
                    ]
              Uid u0a300:
                Package com.other.app:
                  CAMERA (allow):
                    null=[
                      Access: [fg-s] 2026-11-14 10:00:00.000 (-7d2h0m0s0ms)
                    ]
        """.trimIndent()
        val result = AppOpsProbe.parseLastAccess(dump)
        assertEquals(3_600_000L, result["com.example.cam"])
        assertEquals(7 * 86_400_000L + 2 * 3_600_000L, result["com.other.app"])
    }

    @Test
    fun legacyDumpFormatParses() {
        val dump = """
              Uid u0a100:
                Package com.legacy.app:
                  RECORD_AUDIO: mode=allow; time=+21h18m31s437ms ago; rejectTime=+1s ago; duration=+1s23ms
        """.trimIndent()
        assertEquals(
            21 * 3_600_000L + 18 * 60_000L + 31_437L,
            AppOpsProbe.parseLastAccess(dump)["com.legacy.app"]
        )
    }

    @Test
    fun queryOpKeepsOnlyPackageNames() {
        val out = "com.foo.bar\nNo operations.\n  com.baz.qux  \nError: x\n"
        assertEquals(setOf("com.foo.bar", "com.baz.qux"), AppOpsProbe.parseQueryOp(out))
        assertTrue(AppOpsProbe.parseQueryOp(null).isEmpty())
    }

    /** Captured from a Galaxy Z Flip4 on Android 16 (`dumpsys appops --op CAMERA`). */
    @Test
    fun realAndroid16Excerpt() {
        val dump = """
              Uid u0a245:
                state=cch
                capability=--------
                appWidgetVisible=false
                  CAMERA: mode=foreground
                Package com.sec.android.app.camera:
                  CAMERA (allow): 
                    null=[
                      Access: [top-s] 2026-11-20 10:00:26.956 (-1d5h52m4s43ms) duration=+2s855ms
                    ]
        """.trimIndent()
        assertEquals(
            86_400_000L + 5 * 3_600_000L + 52 * 60_000L + 4_043L,
            AppOpsProbe.parseLastAccess(dump)["com.sec.android.app.camera"]
        )
    }
}
