package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPostureTest {

    private fun stateOf(snapshot: PostureSnapshot, check: PostureCheck): PostureState? =
        PrivacyPosture.evaluate(snapshot).firstOrNull { it.check == check }?.state

    @Test
    fun `unreadable settings are left out rather than guessed`() {
        assertTrue(PrivacyPosture.evaluate(PostureSnapshot()).isEmpty())
    }

    @Test
    fun `no screen lock and usb debugging need attention`() {
        val snapshot = PostureSnapshot(deviceSecure = false, usbDebugging = true, wirelessDebugging = true)
        assertEquals(PostureState.ATTENTION, stateOf(snapshot, PostureCheck.SCREEN_LOCK))
        assertEquals(PostureState.ATTENTION, stateOf(snapshot, PostureCheck.USB_DEBUGGING))
        assertEquals(PostureState.ATTENTION, stateOf(snapshot, PostureCheck.WIRELESS_DEBUGGING))
    }

    @Test
    fun `a locked down phone reads as good`() {
        val snapshot = PostureSnapshot(
            deviceSecure = true, encrypted = true, bootloaderLocked = true, patchAgeDays = 30,
            usbDebugging = false, developerOptions = false
        )
        assertTrue(PrivacyPosture.evaluate(snapshot).all { it.state == PostureState.GOOD })
    }

    @Test
    fun `old patch level needs attention`() {
        assertEquals(PostureState.GOOD, stateOf(PostureSnapshot(patchAgeDays = 120), PostureCheck.PATCH_AGE))
        assertEquals(PostureState.ATTENTION, stateOf(PostureSnapshot(patchAgeDays = 121), PostureCheck.PATCH_AGE))
    }

    @Test
    fun `radios and location being on is information not a warning`() {
        val snapshot = PostureSnapshot(
            locationEnabled = true, bluetoothEnabled = true, nfcEnabled = true,
            vpnActive = false, privateDnsActive = false
        )
        assertTrue(PrivacyPosture.evaluate(snapshot).all { it.state == PostureState.NEUTRAL })
    }

    @Test
    fun `sideloaded means no store installer`() {
        assertTrue(PrivacyAuditor.isSideloaded(null))
        assertTrue(PrivacyAuditor.isSideloaded(""))
        assertTrue(PrivacyAuditor.isSideloaded("com.google.android.packageinstaller"))
        assertTrue(PrivacyAuditor.isSideloaded("com.android.shell"))
        assertFalse(PrivacyAuditor.isSideloaded("com.android.vending"))
        assertFalse(PrivacyAuditor.isSideloaded("org.fdroid.fdroid"))
    }

    @Test
    fun `only user apps with special access and an odd origin are flagged`() {
        val entry = AppPrivacyEntry(
            packageName = "com.example.helper", label = "Helper", isSystem = false,
            access = setOf(SensitiveAccess.ACCESSIBILITY), sideloaded = true
        )
        assertTrue(entry.worthReview)
        assertTrue(entry.copy(sideloaded = false, hiddenFromLauncher = true).worthReview)
        assertFalse(entry.copy(sideloaded = false).worthReview)
        assertFalse(entry.copy(isSystem = true).worthReview)
        // Runtime permissions alone are ordinary, wherever the app came from.
        assertFalse(entry.copy(access = setOf(SensitiveAccess.CAMERA)).worthReview)
    }
}

class PrivacyPostureAdditionsTest {

    private fun stateOf(snapshot: PostureSnapshot, check: PostureCheck) =
        PrivacyPosture.evaluate(snapshot).firstOrNull { it.check == check }?.state

    @org.junit.Test
    fun userCaCertsFlagged() {
        org.junit.Assert.assertEquals(PostureState.GOOD, stateOf(PostureSnapshot(userCaCerts = 0), PostureCheck.USER_CA_CERTS))
        org.junit.Assert.assertEquals(PostureState.ATTENTION, stateOf(PostureSnapshot(userCaCerts = 2), PostureCheck.USER_CA_CERTS))
        org.junit.Assert.assertNull(stateOf(PostureSnapshot(), PostureCheck.USER_CA_CERTS))
    }

    @org.junit.Test
    fun userAliasCounting() {
        org.junit.Assert.assertEquals(
            2,
            PrivacyPosture.countUserCaAliases(listOf("system:abc.0", "user:1a2b.0", "user:ffff.1"))
        )
    }

    @org.junit.Test
    fun openWifiAndManagementFlagged() {
        org.junit.Assert.assertEquals(false, PrivacyPosture.wifiSecurityIsEncrypted(0))
        org.junit.Assert.assertEquals(false, PrivacyPosture.wifiSecurityIsEncrypted(1))
        org.junit.Assert.assertEquals(true, PrivacyPosture.wifiSecurityIsEncrypted(4))
        org.junit.Assert.assertNull(PrivacyPosture.wifiSecurityIsEncrypted(-1))
        org.junit.Assert.assertEquals(PostureState.ATTENTION, stateOf(PostureSnapshot(wifiEncrypted = false), PostureCheck.WIFI_SECURITY))
        org.junit.Assert.assertEquals(PostureState.ATTENTION, stateOf(PostureSnapshot(deviceManaged = true), PostureCheck.DEVICE_MANAGED))
        org.junit.Assert.assertEquals(PostureState.NEUTRAL, stateOf(PostureSnapshot(workProfile = true), PostureCheck.WORK_PROFILE))
    }
}
