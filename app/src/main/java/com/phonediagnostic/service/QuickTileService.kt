package com.phonediagnostic.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.phonediagnostic.MainActivity
import com.phonediagnostic.R
import com.phonediagnostic.data.DeviceInfoCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Battery temperature and RAM use at a glance in the notification shade.
 *
 * Only battery and memory are sampled. A full collect would spin up an EGL
 * context and wake every sensor, which is far too much for a tile the shade
 * refreshes whenever it is pulled down.
 */
class QuickTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        // The shade can start listening again after a stop, so the scope is
        // rebuilt here rather than held for the service's lifetime.
        scope?.cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch {
            val text = withContext(Dispatchers.Default) { sample() }
            // qsTile is null once the shade stops listening, and the collect
            // above is long enough for that to happen mid-flight.
            val tile = qsTile ?: return@launch
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && text != null) {
                tile.subtitle = text
            }
            tile.contentDescription = text ?: getString(R.string.app_name)
            tile.updateTile()
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun sample(): String? = try {
        val collector = DeviceInfoCollector(applicationContext)
        val b = collector.collectBattery()
        val m = collector.collectMemory()
        getString(
            R.string.tile_subtitle,
            String.format(Locale.US, "%.1f", b.temperature),
            m.usagePercent
        )
    } catch (_: Exception) {
        null
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34 dropped the Intent overload; it throws rather than opening.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
