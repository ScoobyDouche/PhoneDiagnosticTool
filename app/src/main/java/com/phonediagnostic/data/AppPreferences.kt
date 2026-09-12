package com.phonediagnostic.data

import android.content.Context
import com.phonediagnostic.data.elevated.AccessTier

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
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

    companion object {
        private const val PREFS_NAME = "phone_diagnostic_prefs"
        private const val KEY_NETWORK_PROBE = "network_probe_enabled"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BG_MONITOR = "background_monitor_enabled"
        private const val KEY_ACCESS_TIER = "elevated_access_tier"
    }
}
