package com.phonediagnostic.data.elevated

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessTierTest {

    @Test
    fun fromName_roundTripsEveryTier() {
        for (tier in AccessTier.entries) {
            assertEquals(tier, AccessTier.fromName(tier.name))
        }
    }

    @Test
    fun fromName_defaultsToNoneForUnknownOrNull() {
        assertEquals(AccessTier.NONE, AccessTier.fromName(null))
        assertEquals(AccessTier.NONE, AccessTier.fromName(""))
        assertEquals(AccessTier.NONE, AccessTier.fromName("SHELL"))
        assertEquals(AccessTier.NONE, AccessTier.fromName("shizuku")) // case-sensitive by design
    }

    @Test
    fun readFileOrNull_usesCatAndTrimsBlankToNull() {
        val calls = mutableListOf<String>()
        val shell = object : ElevatedShell {
            override val tier = AccessTier.ROOT
            override fun exec(command: String): String? {
                calls += command
                return when {
                    command.endsWith("/full") -> "  4200  \n"
                    command.endsWith("/blank") -> "   \n"
                    else -> null
                }
            }
        }
        assertEquals("4200", shell.readFileOrNull("/sys/x/full"))
        assertEquals(null, shell.readFileOrNull("/sys/x/blank"))
        assertEquals(null, shell.readFileOrNull("/sys/x/missing"))
        assertEquals("cat /sys/x/full", calls.first())
    }
}
