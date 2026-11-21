package com.phonediagnostic.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.phonediagnostic.MainActivity
import com.phonediagnostic.R
import com.phonediagnostic.data.DeviceInfoCollector
import java.util.Locale

/**
 * Home-screen widget: battery level and temperature, charging state, and RAM
 * in use. Reads only battery and memory — the same cheap pair the Quick
 * Settings tile uses — and refreshes every 30 minutes (the platform minimum),
 * whenever the background monitor samples, and when tapped open.
 */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        /** Re-renders every placed widget. Safe to call from any thread; no-op when none are placed. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
            }.getOrNull() ?: return
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val collector = DeviceInfoCollector(context.applicationContext)
            val b = runCatching { collector.collectBattery(DeviceInfoCollector.GaugeRead.SKIP) }.getOrNull()
            val m = runCatching { collector.collectMemory(kernelExtras = false) }.getOrNull()
            val open = PendingIntent.getActivity(
                context, 2, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_status).apply {
                    setTextViewText(R.id.widget_battery, b?.let { "${it.level}%" } ?: "—")
                    setTextViewText(
                        R.id.widget_battery_detail,
                        b?.let {
                            String.format(Locale.US, "%.1f °C", it.temperature) + " · " +
                                context.getString(if (it.isCharging) R.string.widget_charging else R.string.widget_on_battery)
                        } ?: ""
                    )
                    setTextViewText(
                        R.id.widget_ram,
                        m?.let { context.getString(R.string.widget_ram, it.usedRamMb / 1024f, it.totalRamMb / 1024f) } ?: ""
                    )
                    setOnClickPendingIntent(R.id.widget_root, open)
                }
                runCatching { manager.updateAppWidget(id, views) }
            }
        }
    }
}
