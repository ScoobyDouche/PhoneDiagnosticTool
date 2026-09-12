package com.phonediagnostic.data.elevated

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.phonediagnostic.BuildConfig
import com.phonediagnostic.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/** Snapshot of what elevated access is possible and what is actually live. */
data class ElevatedStatus(
    val preferredTier: AccessTier = AccessTier.NONE,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermission: Boolean = false,
    val rootAvailable: Boolean = false,
    /** The tier actually providing a shell right now; NONE when nothing is live. */
    val activeTier: AccessTier = AccessTier.NONE
)

/**
 * Owns the opt-in elevated-access feature: which tier the user chose, whether
 * it is currently usable, and the live [ElevatedShell] the collector reads
 * through. Everything here is best-effort — if Shizuku is not installed or the
 * device is not rooted, the flows simply report that and [activeShell] stays
 * null, leaving the app on its normal no-elevation path.
 *
 * App-scoped: created once by the ViewModel and kept for the process lifetime,
 * so the Shizuku listeners are registered once and never leaked back.
 */
class ElevatedAccessManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)

    private val _status = MutableStateFlow(ElevatedStatus(preferredTier = prefs.accessTier))
    val status: StateFlow<ElevatedStatus> = _status.asStateFlow()

    private val _activeShell = MutableStateFlow<ElevatedShell?>(null)
    val activeShell: StateFlow<ElevatedShell?> = _activeShell.asStateFlow()

    // Cached once found; root availability does not change within a run.
    private val rootAvailable: Boolean by lazy { RootShell.isAvailable() }

    private var boundService: IElevatedService? = null
    private var binding = false

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ElevatedService::class.java.name))
            .daemon(false)
            .processNameSuffix("elevated")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            boundService = if (binder != null && binder.pingBinder()) {
                IElevatedService.Stub.asInterface(binder)
            } else null
            resolve()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            binding = false
            resolve()
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        refresh()
    }
    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        boundService = null
        refresh()
    }

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
        }
        refresh()
    }

    /** Recompute availability and re-resolve the active shell. Cheap; call freely. */
    fun refresh() {
        val installed = isShizukuInstalled()
        val running = installed && runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permission = running && runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        _status.value = _status.value.copy(
            preferredTier = prefs.accessTier,
            shizukuInstalled = installed,
            shizukuRunning = running,
            shizukuPermission = permission,
            rootAvailable = rootAvailable
        )
        resolve()
    }

    fun setPreferredTier(tier: AccessTier) {
        prefs.accessTier = tier
        if (tier != AccessTier.SHIZUKU) unbindShizuku()
        refresh()
    }

    /** Ask Shizuku for permission. No-op unless Shizuku is running. */
    fun requestShizukuPermission() {
        if (!_status.value.shizukuRunning) return
        runCatching {
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    private fun resolve() {
        val s = _status.value
        val shell: ElevatedShell? = when (s.preferredTier) {
            AccessTier.NONE -> null
            AccessTier.ROOT -> if (s.rootAvailable) RootShell() else null
            AccessTier.SHIZUKU -> {
                if (s.shizukuRunning && s.shizukuPermission) {
                    ensureBound()
                    boundService?.let { ShizukuShell(it) }
                } else null
            }
        }
        _activeShell.value = shell
        _status.value = s.copy(activeTier = shell?.tier ?: AccessTier.NONE)
    }

    private fun ensureBound() {
        if (boundService != null || binding) return
        binding = true
        val ok = runCatching { Shizuku.bindUserService(serviceArgs, connection); true }.getOrDefault(false)
        if (!ok) binding = false
    }

    private fun unbindShizuku() {
        boundService = null
        binding = false
        runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
    }

    private fun isShizukuInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST_CODE = 4919
    }
}
