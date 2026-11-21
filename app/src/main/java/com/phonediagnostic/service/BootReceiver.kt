package com.phonediagnostic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonediagnostic.data.AppPreferences
import com.phonediagnostic.data.DiagnosticLog

/**
 * Brings the background monitor back after a reboot or an app update, but only
 * if the user left it switched on. Without this the Settings toggle stayed on
 * while nothing was actually sampling.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val prefs = runCatching { AppPreferences(context) }.getOrNull() ?: return
        if (!prefs.backgroundMonitorEnabled) return
        runCatching {
            MonitorService.start(context)
            DiagnosticLog.get(context).append("Monitor resumed after ${intent.action?.substringAfterLast('.')}")
        }.onFailure {
            // Starting a foreground service can be refused (battery restrictions,
            // OEM boot limits). Leave the toggle as-is; the app restarts it when opened.
            runCatching { DiagnosticLog.get(context).append("Monitor could not resume: ${it.javaClass.simpleName}") }
        }
    }
}
