package com.phonediagnostic.data

import android.content.Context

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

    companion object {
        private const val PREFS_NAME = "phone_diagnostic_prefs"
        private const val KEY_NETWORK_PROBE = "network_probe_enabled"
        private const val KEY_THEME = "theme_mode"
    }
}
