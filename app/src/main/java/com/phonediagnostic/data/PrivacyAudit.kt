package com.phonediagnostic.data

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
import com.phonediagnostic.data.elevated.ElevatedShell

/**
 * Sensitive things another app can do. The runtime-permission groups come
 * from what the app has been *granted*, not merely requested; the last four
 * are special access the user switched on in system settings, which is how
 * stalkerware usually operates. A keyboard sees everything typed into it.
 */
enum class SensitiveAccess(
    val isSpecialAccess: Boolean = false,
    /** Only visible with Shizuku or root, since app-ops of other apps are private. */
    val needsElevation: Boolean = false
) {
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
    KEYBOARD(isSpecialAccess = true),
    NOTIFICATION_LISTENER(isSpecialAccess = true),
    DEVICE_ADMIN(isSpecialAccess = true),
    OVERLAY(isSpecialAccess = true, needsElevation = true),
    INSTALL_APPS(isSpecialAccess = true, needsElevation = true),
    USAGE_ACCESS(isSpecialAccess = true, needsElevation = true)
}

data class AppPrivacyEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val access: Set<SensitiveAccess>,
    /** Installed from a file or over adb rather than through an app store. */
    val sideloaded: Boolean = false,
    /** Has no launcher icon, so it does not appear among the user's apps. */
    val hiddenFromLauncher: Boolean = false,
    /** Exempt from battery optimisation, so it can keep running in the background. */
    val unrestrictedBackground: Boolean = false
) {
    /**
     * Special access held by an app the user cannot see or did not get from a
     * store is the pattern stalkerware fits. Not proof of anything — plenty of
     * legitimate tools match — just the entries worth reading first.
     */
    val worthReview: Boolean
        get() = !isSystem && (sideloaded || hiddenFromLauncher) && access.any { it.isSpecialAccess }
}

/** One of this app's own manifest permissions and whether it is currently granted. */
data class OwnPermission(val name: String, val granted: Boolean)

data class PrivacyAudit(
    /** Apps holding at least one sensitive grant, most access first. */
    val apps: List<AppPrivacyEntry>,
    val ownPermissions: List<OwnPermission>,
    /** Device settings that bear on privacy. */
    val posture: List<PostureResult> = emptyList(),
    /** Whether Shizuku / root was live, so elevated-only sections have data. */
    val elevatedAvailable: Boolean = false,
    /** Apps that used camera, microphone or location recently; elevated only. */
    val recentUse: List<RecentUseEntry> = emptyList()
) {
    /** Non-system apps that fit the stalkerware pattern; see [AppPrivacyEntry.worthReview]. */
    val appsWorthReview: List<AppPrivacyEntry> get() = apps.filter { it.worthReview }

    /** Device checks in the red. */
    val postureWarnings: List<PostureResult> get() = posture.filter { it.state == PostureState.ATTENTION }
}

class PrivacyAuditor(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Blocking — reads every installed package. Call from a background dispatcher.
     * The security and radio state come from the device report when one exists.
     */
    fun collect(
        security: SecurityInfo? = null,
        nfc: NfcInfo? = null,
        bluetooth: BluetoothInfo? = null,
        elevated: ElevatedShell? = null
    ): PrivacyAudit {
        val userId = android.os.Process.myUid() / PER_USER_RANGE
        val elevatedOps: Map<SensitiveAccess, Set<String>> = elevated?.let {
            runCatching { AppOpsProbe.specialAccess(it, userId) }.getOrNull()
        }.orEmpty()
        val recentRaw = elevated?.let { runCatching { AppOpsProbe.recentUse(it) }.getOrNull() }.orEmpty()
        val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
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
        val keyboards = try {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.enabledInputMethodList.orEmpty().map { it.packageName }.toSet()
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
            if (pkg in keyboards) access += SensitiveAccess.KEYBOARD
            for ((op, holders) in elevatedOps) if (pkg in holders) access += op
            if (access.isEmpty()) return@mapNotNull null
            val app = info.applicationInfo
            val isSystem = app != null && (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppPrivacyEntry(
                packageName = pkg,
                label = app?.let { labelFor(it) } ?: pkg,
                isSystem = isSystem,
                access = access,
                // System apps are preinstalled and often icon-less by design.
                sideloaded = !isSystem && isSideloaded(installerOf(pkg)),
                hiddenFromLauncher = !isSystem && !hasLauncherIcon(pkg),
                unrestrictedBackground = !isSystem &&
                    runCatching { power?.isIgnoringBatteryOptimizations(pkg) == true }.getOrDefault(false)
            )
        }.sortedWith(
            compareByDescending<AppPrivacyEntry> { it.worthReview }
                .thenByDescending { it.access.size }
                .thenBy { it.label.lowercase() }
        )

        return PrivacyAudit(
            apps = apps,
            ownPermissions = ownPermissions(),
            posture = PrivacyPosture.evaluate(PrivacyPosture.snapshot(context, security, nfc, bluetooth)),
            elevatedAvailable = elevated != null,
            recentUse = recentRaw.mapNotNull { (pkg, uses) ->
                if (uses.isEmpty() || pkg == context.packageName) return@mapNotNull null
                val app = applicationInfoOrNull(pkg)
                RecentUseEntry(
                    packageName = pkg,
                    label = app?.let { labelFor(it) } ?: pkg,
                    isSystem = app != null && (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    lastUsedAgoMs = uses
                )
            }.sortedBy { it.mostRecentAgoMs }
        )
    }

    private fun applicationInfoOrNull(pkg: String): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0)
        }
    } catch (_: Exception) {
        null
    }

    private fun installerOf(packageName: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }
    } catch (_: Exception) {
        null
    }

    private fun hasLauncherIcon(packageName: String): Boolean = try {
        pm.getLaunchIntentForPackage(packageName) != null
    } catch (_: Exception) {
        true
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
        private const val PER_USER_RANGE = 100_000
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

        /**
         * No installer on record, the system's own package installer (a file
         * the user opened) or the adb shell all mean the app did not come
         * through a store. Any other installer is taken to be one.
         */
        fun isSideloaded(installer: String?): Boolean =
            installer.isNullOrBlank() ||
                installer == "com.android.shell" ||
                installer.endsWith(".packageinstaller")

        /** Secure setting format: "pkg/.Service:pkg2/com.other.Service". */
        fun enabledAccessibilityPackages(setting: String?): Set<String> =
            setting.orEmpty()
                .split(':')
                .mapNotNull { it.substringBefore('/').trim().takeIf { pkg -> pkg.isNotEmpty() } }
                .toSet()
    }
}
