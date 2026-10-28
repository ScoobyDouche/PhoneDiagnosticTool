package com.phonediagnostic.data

import android.content.Context
import com.phonediagnostic.data.elevated.AccessTier

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Where the optional latency probe connects (TCP port 53, DNS). The resolvers
 * see only a connection attempt, never a query; [GATEWAY] keeps the probe on
 * the local network entirely.
 */
enum class LatencyTarget(val host: String?) {
    CLOUDFLARE("1.1.1.1"),
    QUAD9("9.9.9.9"),
    GOOGLE("8.8.8.8"),
    GATEWAY(null);

    companion object {
        fun fromName(name: String?): LatencyTarget =
            entries.firstOrNull { it.name == name } ?: CLOUDFLARE
    }
}

class AppPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var networkProbeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NETWORK_PROBE, true)
        set(value) = prefs.edit().putBoolean(KEY_NETWORK_PROBE, value).apply()

    var themeMode: ThemeMode
        get() = when (prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var backgroundMonitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_MONITOR, false)
        set(value) = prefs.edit().putBoolean(KEY_BG_MONITOR, value).apply()

    /** Which elevated-access tier the user opted into. Defaults to none. */
    var accessTier: AccessTier
        get() = AccessTier.fromName(prefs.getString(KEY_ACCESS_TIER, AccessTier.NONE.name))
        set(value) = prefs.edit().putString(KEY_ACCESS_TIER, value.name).apply()

    var latencyTarget: LatencyTarget
        get() = LatencyTarget.fromName(prefs.getString(KEY_LATENCY_TARGET, null))
        set(value) = prefs.edit().putString(KEY_LATENCY_TARGET, value.name).apply()

    /** Strip build fingerprint, bootloader and similar identifiers from exports. On by default. */
    var redactReports: Boolean
        get() = prefs.getBoolean(KEY_REDACT_REPORTS, true)
        set(value) = prefs.edit().putBoolean(KEY_REDACT_REPORTS, value).apply()

    /** Block screenshots and hide the app in Recents. */
    var secureScreens: Boolean
        get() = prefs.getBoolean(KEY_SECURE_SCREENS, false)
        set(value) = prefs.edit().putBoolean(KEY_SECURE_SCREENS, value).apply()

    companion object {
        private const val PREFS_NAME = "phone_diagnostic_prefs"
        private const val KEY_NETWORK_PROBE = "network_probe_enabled"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BG_MONITOR = "background_monitor_enabled"
        private const val KEY_ACCESS_TIER = "elevated_access_tier"
        private const val KEY_LATENCY_TARGET = "latency_target"
        private const val KEY_REDACT_REPORTS = "redact_reports"
        private const val KEY_SECURE_SCREENS = "secure_screens"
    }
}
