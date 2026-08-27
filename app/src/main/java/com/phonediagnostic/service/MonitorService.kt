package com.phonediagnostic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phonediagnostic.MainActivity
import com.phonediagnostic.R
import com.phonediagnostic.data.AppPreferences
import com.phonediagnostic.data.DeviceInfoCollector
import com.phonediagnostic.data.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optional background sampler. Foreground notification required by Android.
 * Writes a fixed-size rotating log only (no unbounded growth).
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
        }

        startAsForeground()
        if (job?.isActive != true) {
            job = scope.launch { sampleLoop() }
            DiagnosticLog.get(this).append("Monitor started")
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Diagnostics monitoring")
            .setContentText("Sampling battery & RAM · fixed log")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun sampleLoop() {
        val collector = DeviceInfoCollector(applicationContext)
        val log = DiagnosticLog.get(this)
        while (scope.isActive) {
            try {
                val report = collector.collect(networkProbe = false)
                val b = report.battery
                val m = report.memory
                log.append(
                    "BAT ${b.level}% ${b.status} ${String.format("%.1f", b.temperature)}°C · " +
                        "RAM ${m.usedRamMb}/${m.totalRamMb}MB avail ${m.availableRamMb} · " +
                        if (m.isLowMemory) "PRESSURE" else "ok"
                )
            } catch (e: Exception) {
                log.append("Sample error: ${e.message ?: e.javaClass.simpleName}")
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun stopSelfSafely() {
        DiagnosticLog.get(this).append("Monitor stopped")
        AppPreferences(this).backgroundMonitorEnabled = false
        job?.cancel()
        job = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while diagnostic sampling is active"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.phonediagnostic.MONITOR_STOP"
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 42
        private const val SAMPLE_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
