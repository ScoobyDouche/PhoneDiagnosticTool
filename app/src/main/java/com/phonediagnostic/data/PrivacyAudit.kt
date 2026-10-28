package com.phonediagnostic.data

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Sensitive things another app can do. The runtime-permission groups come
 * from what the app has been *granted*, not merely requested; the last three
 * are special access the user switched on in system settings, which is how
 * stalkerware usually operates.
 */
enum class SensitiveAccess(val isSpecialAccess: Boolean = false) {
    CAMERA,
    MICROPHONE,
    LOCATION,
    BACKGROUND_LOCATION,
    CONTACTS,
    SMS,
    CALL_LOG,
    PHONE,
    CALENDAR,
    BODY_SENSORS,
    NEARBY_DEVICES,
    PHOTOS_MEDIA,
    ACCESSIBILITY(isSpecialAccess = true),
    NOTIFICATION_LISTENER(isSpecialAccess = true),
    DEVICE_ADMIN(isSpecialAccess = true)
}

data class AppPrivacyEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val access: Set<SensitiveAccess>
)

/** One of this app's own manifest permissions and whether it is currently granted. */
data class OwnPermission(val name: String, val granted: Boolean)

data class PrivacyAudit(
    /** Apps holding at least one sensitive grant, most access first. */
    val apps: List<AppPrivacyEntry>,
    val ownPermissions: List<OwnPermission>
)

class PrivacyAuditor(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** Blocking — reads every installed package. Call from a background dispatcher. */
    fun collect(): PrivacyAudit {
        val accessibility = enabledAccessibilityPackages(
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        )
        val listeners = try {
            NotificationManagerCompat.getEnabledListenerPackages(context)
        } catch (_: Exception) {
            emptySet()
        }
        val admins = try {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager)
                ?.activeAdmins.orEmpty().map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val packages = installedPackages()
        val apps = packages.mapNotNull { info ->
            val pkg = info.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val access = categoriesFor(grantedPermissions(info)).toMutableSet()
            if (pkg in accessibility) access += SensitiveAccess.ACCESSIBILITY
            if (pkg in listeners) access += SensitiveAccess.NOTIFICATION_LISTENER
            if (pkg in admins) access += SensitiveAccess.DEVICE_ADMIN
            if (access.isEmpty()) return@mapNotNull null
            val app = info.applicationInfo
            AppPrivacyEntry(
                packageName = pkg,
                label = app?.let { labelFor(it) } ?: pkg,
                isSystem = app != null && (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                access = access
            )
        }.sortedWith(compareByDescending<AppPrivacyEntry> { it.access.size }.thenBy { it.label.lowercase() })

        return PrivacyAudit(apps = apps, ownPermissions = ownPermissions())
    }

    private fun installedPackages(): List<PackageInfo> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun grantedPermissions(info: PackageInfo): List<String> {
        val requested = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags ?: return emptyList()
        return requested.indices
            .filter { it < flags.size && (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
            .map { requested[it] }
    }

    private fun ownPermissions(): List<OwnPermission> {
        val info = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        val granted = grantedPermissions(info).toSet()
        return info.requestedPermissions.orEmpty().map { name ->
            // Usage access is an app-op, not a runtime grant, so the flag never shows it.
            val isGranted = if (name == USAGE_STATS) {
                UsageCollector(context).hasUsageStatsPermission()
            } else {
                name in granted
            }
            OwnPermission(name, isGranted)
        }
    }

    private fun labelFor(app: ApplicationInfo): String = try {
        pm.getApplicationLabel(app).toString()
    } catch (_: Exception) {
        app.packageName
    }

    companion object {
        private const val USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS"

        /** Maps granted runtime permissions onto the categories people recognise. */
        fun categoriesFor(granted: Collection<String>): Set<SensitiveAccess> {
            val result = HashSet<SensitiveAccess>()
            for (permission in granted) {
                val category = when (permission.removePrefix("android.permission.")) {
                    "CAMERA" -> SensitiveAccess.CAMERA
                    "RECORD_AUDIO" -> SensitiveAccess.MICROPHONE
                    "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" -> SensitiveAccess.LOCATION
                    "ACCESS_BACKGROUND_LOCATION" -> SensitiveAccess.BACKGROUND_LOCATION
                    "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS" -> SensitiveAccess.CONTACTS
                    "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "RECEIVE_MMS" -> SensitiveAccess.SMS
                    "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS" -> SensitiveAccess.CALL_LOG
                    "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE",
                    "ANSWER_PHONE_CALLS" -> SensitiveAccess.PHONE
                    "READ_CALENDAR", "WRITE_CALENDAR" -> SensitiveAccess.CALENDAR
                    "BODY_SENSORS", "BODY_SENSORS_BACKGROUND",
                    "ACTIVITY_RECOGNITION" -> SensitiveAccess.BODY_SENSORS
                    "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "NEARBY_WIFI_DEVICES",
                    "UWB_RANGING" -> SensitiveAccess.NEARBY_DEVICES
                    "READ_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO",
                    "READ_MEDIA_VISUAL_USER_SELECTED" -> SensitiveAccess.PHOTOS_MEDIA
                    else -> null
                }
                if (category != null) result += category
            }
            return result
        }

        /** Secure setting format: "pkg/.Service:pkg2/com.other.Service". */
        fun enabledAccessibilityPackages(setting: String?): Set<String> =
            setting.orEmpty()
                .split(':')
                .mapNotNull { it.substringBefore('/').trim().takeIf { pkg -> pkg.isNotEmpty() } }
                .toSet()
    }
}
