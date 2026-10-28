package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyAuditTest {

    @Test
    fun grantedPermissionsMapToCategories() {
        val categories = PrivacyAuditor.categoriesFor(
            listOf(
                "android.permission.CAMERA",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.INTERNET",
                "com.example.CUSTOM"
            )
        )
        assertEquals(
            setOf(
                SensitiveAccess.CAMERA,
                SensitiveAccess.LOCATION,
                SensitiveAccess.BACKGROUND_LOCATION,
                SensitiveAccess.PHOTOS_MEDIA
            ),
            categories
        )
    }

    @Test
    fun harmlessPermissionsMapToNothing() {
        assertTrue(
            PrivacyAuditor.categoriesFor(
                listOf("android.permission.INTERNET", "android.permission.VIBRATE")
            ).isEmpty()
        )
    }

    @Test
    fun accessibilitySettingYieldsPackages() {
        assertEquals(
            setOf("com.a", "com.b"),
            PrivacyAuditor.enabledAccessibilityPackages("com.a/.Svc:com.b/com.b.Reader:")
        )
        assertTrue(PrivacyAuditor.enabledAccessibilityPackages(null).isEmpty())
    }
}
