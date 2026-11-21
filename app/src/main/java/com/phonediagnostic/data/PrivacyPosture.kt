package com.phonediagnostic.data

import android.app.KeyguardManager
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings

/** A device setting that bears on privacy. The screen supplies the wording. */
enum class PostureCheck {
    SCREEN_LOCK,
    ENCRYPTION,
    BOOTLOADER,
    PATCH_AGE,
    USB_DEBUGGING,
    WIRELESS_DEBUGGING,
    DEVELOPER_OPTIONS,
    PRIVATE_DNS,
    VPN,
    LOCATION,
    BLUETOOTH,
    NFC,
    SENSOR_TOGGLES,
    USER_CA_CERTS,
    DEVICE_MANAGED,
    WORK_PROFILE,
    WIFI_SECURITY
}

enum class PostureState {
    /** The safer setting. */
    GOOD,

    /** Worth changing, or at least knowing about. */
    ATTENTION,

    /** Neither good nor bad on its own — a radio that is on, a VPN that is off. */
    NEUTRAL
}

data class PostureResult(val check: PostureCheck, val state: PostureState)

/**
 * What the device reports, before any judgement. Null means the platform would
 * not say, and the matching check is left out rather than guessed.
 */
data class PostureSnapshot(
    val deviceSecure: Boolean? = null,
    val encrypted: Boolean? = null,
    val bootloaderLocked: Boolean? = null,
    val patchAgeDays: Int? = null,
    val usbDebugging: Boolean? = null,
    val wirelessDebugging: Boolean? = null,
    val developerOptions: Boolean? = null,
    val privateDnsActive: Boolean? = null,
    val vpnActive: Boolean? = null,
    val locationEnabled: Boolean? = null,
    val bluetoothEnabled: Boolean? = null,
    val nfcEnabled: Boolean? = null,
    val sensorToggles: Boolean? = null,
    /** CA certificates the user (or an app, via the user) added. They can intercept TLS. */
    val userCaCerts: Int? = null,
    /** A device-owner or profile-owner app controls this device. */
    val deviceManaged: Boolean? = null,
    val workProfile: Boolean? = null,
    /** True when the current Wi-Fi network is encrypted; false for open or WEP; null off Wi-Fi. */
    val wifiEncrypted: Boolean? = null
)

object PrivacyPosture {

    /** Past this a device has missed at least three monthly security bulletins. */
    private const val STALE_PATCH_DAYS = 120

    /** Pure, so the rules can be tested without a device. Order is the display order. */
    fun evaluate(s: PostureSnapshot): List<PostureResult> {
        val results = ArrayList<PostureResult>()
        fun add(check: PostureCheck, value: Boolean?, whenTrue: PostureState, whenFalse: PostureState) {
            if (value != null) results += PostureResult(check, if (value) whenTrue else whenFalse)
        }
        add(PostureCheck.SCREEN_LOCK, s.deviceSecure, PostureState.GOOD, PostureState.ATTENTION)
        add(PostureCheck.ENCRYPTION, s.encrypted, PostureState.GOOD, PostureState.ATTENTION)
        add(PostureCheck.BOOTLOADER, s.bootloaderLocked, PostureState.GOOD, PostureState.ATTENTION)
        add(PostureCheck.PATCH_AGE, s.patchAgeDays?.let { it <= STALE_PATCH_DAYS }, PostureState.GOOD, PostureState.ATTENTION)
        add(PostureCheck.USB_DEBUGGING, s.usbDebugging, PostureState.ATTENTION, PostureState.GOOD)
        add(PostureCheck.WIRELESS_DEBUGGING, s.wirelessDebugging, PostureState.ATTENTION, PostureState.GOOD)
        add(PostureCheck.DEVELOPER_OPTIONS, s.developerOptions, PostureState.NEUTRAL, PostureState.GOOD)
        add(PostureCheck.PRIVATE_DNS, s.privateDnsActive, PostureState.GOOD, PostureState.NEUTRAL)
        add(PostureCheck.VPN, s.vpnActive, PostureState.GOOD, PostureState.NEUTRAL)
        add(PostureCheck.LOCATION, s.locationEnabled, PostureState.NEUTRAL, PostureState.GOOD)
        add(PostureCheck.BLUETOOTH, s.bluetoothEnabled, PostureState.NEUTRAL, PostureState.GOOD)
        add(PostureCheck.NFC, s.nfcEnabled, PostureState.NEUTRAL, PostureState.GOOD)
        add(PostureCheck.SENSOR_TOGGLES, s.sensorToggles, PostureState.GOOD, PostureState.NEUTRAL)
        add(PostureCheck.USER_CA_CERTS, s.userCaCerts?.let { it == 0 }, PostureState.GOOD, PostureState.ATTENTION)
        add(PostureCheck.DEVICE_MANAGED, s.deviceManaged, PostureState.ATTENTION, PostureState.GOOD)
        add(PostureCheck.WORK_PROFILE, s.workProfile, PostureState.NEUTRAL, PostureState.GOOD)
        add(PostureCheck.WIFI_SECURITY, s.wifiEncrypted, PostureState.GOOD, PostureState.ATTENTION)
        return results
    }

    /**
     * Reads the settings. None of these needs a permission; several are simply
     * unreadable on some builds, which leaves the field null.
     */
    fun snapshot(context: Context, security: SecurityInfo?, nfc: NfcInfo?, bluetooth: BluetoothInfo?): PostureSnapshot {
        val resolver = context.contentResolver
        fun globalFlag(key: String): Boolean? = try {
            when (Settings.Global.getInt(resolver, key, -1)) {
                -1 -> null
                0 -> false
                else -> true
            }
        } catch (_: Exception) {
            null
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = try { cm?.activeNetwork } catch (_: Exception) { null }
        val capabilities = try { active?.let { cm?.getNetworkCapabilities(it) } } catch (_: Exception) { null }
        val link = try { active?.let { cm?.getLinkProperties(it) } } catch (_: Exception) { null }

        return PostureSnapshot(
            deviceSecure = try {
                (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure
            } catch (_: Exception) {
                null
            },
            encrypted = security?.encryption?.takeIf { it.isNotBlank() && it != "Unsupported" }
                ?.startsWith("Encrypted"),
            bootloaderLocked = security?.bootloaderLocked,
            patchAgeDays = security?.patchAgeDays,
            usbDebugging = globalFlag(Settings.Global.ADB_ENABLED),
            // Not a public key, so builds that hide it simply leave this out.
            wirelessDebugging = globalFlag("adb_wifi_enabled"),
            developerOptions = globalFlag(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            privateDnsActive = if (link != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                link.isPrivateDnsActive
            } else null,
            vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            locationEnabled = locationEnabled(context),
            bluetoothEnabled = bluetooth?.takeIf { it.present }?.enabled,
            nfcEnabled = nfc?.takeIf { it.present }?.enabled,
            sensorToggles = sensorToggles(context),
            userCaCerts = userCaCertCount(),
            deviceManaged = deviceManaged(context),
            workProfile = workProfile(context),
            wifiEncrypted = wifiEncrypted(context, capabilities)
        )
    }

    /** Whether location services are switched on. Reads the switch, never a position. */
    private fun locationEnabled(context: Context): Boolean? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }
    } catch (_: Exception) {
        null
    }

    /** Android 12+ can offer system-wide camera and microphone kill switches. */
    private fun sensorToggles(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val spm = context.getSystemService(SensorPrivacyManager::class.java) ?: return null
            spm.supportsSensorToggle(SensorPrivacyManager.Sensors.CAMERA) &&
                spm.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Counts CA certificates in the user store. A certificate there lets whoever
     * installed it read this phone's "secure" traffic — legitimate on a managed
     * work phone, and a classic sign of monitoring software anywhere else.
     * The AndroidCAStore keystore is readable by any app; user entries are
     * prefixed "user:".
     */
    fun userCaCertCount(): Int? = try {
        val ks = java.security.KeyStore.getInstance("AndroidCAStore")
        ks.load(null, null)
        countUserCaAliases(ks.aliases().toList())
    } catch (_: Exception) {
        null
    }

    /** Pure, for tests. */
    fun countUserCaAliases(aliases: Collection<String>): Int = aliases.count { it.startsWith("user:") }

    /** Whether some app is device owner or profile owner of this user. */
    private fun deviceManaged(context: Context): Boolean? = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            ?: return null
        dpm.activeAdmins.orEmpty().any { admin ->
            dpm.isDeviceOwnerApp(admin.packageName) || dpm.isProfileOwnerApp(admin.packageName)
        }
    } catch (_: Exception) {
        null
    }

    /** More than one profile for this user means a work (or other managed) profile exists. */
    private fun workProfile(context: Context): Boolean? = try {
        (context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager)
            ?.userProfiles?.let { it.size > 1 }
    } catch (_: Exception) {
        null
    }

    /**
     * Android 12+ reports the security type of the connected Wi-Fi network
     * without any location permission. Open and WEP networks let anyone nearby
     * see unencrypted traffic; OWE ("Enhanced Open") is encrypted.
     */
    private fun wifiEncrypted(context: Context, capabilities: NetworkCapabilities?): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        return try {
            val info = (capabilities.transportInfo as? android.net.wifi.WifiInfo)
                ?: @Suppress("DEPRECATION")
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager)
                    ?.connectionInfo
            wifiSecurityIsEncrypted(info?.currentSecurityType ?: return null)
        } catch (_: Exception) {
            null
        }
    }

    /** Pure, for tests. Maps WifiInfo.SECURITY_TYPE_* onto encrypted / not; unknown is null. */
    fun wifiSecurityIsEncrypted(securityType: Int): Boolean? = when (securityType) {
        0, 1 -> false // SECURITY_TYPE_OPEN, SECURITY_TYPE_WEP
        -1 -> null    // SECURITY_TYPE_UNKNOWN
        else -> true
    }
}
