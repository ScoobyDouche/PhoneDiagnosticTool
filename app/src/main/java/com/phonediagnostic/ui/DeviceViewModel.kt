package com.phonediagnostic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonediagnostic.data.AppPreferences
import com.phonediagnostic.data.AppStorageEntry
import com.phonediagnostic.data.DeviceInfoCollector
import com.phonediagnostic.data.DiagnosticLog
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.data.LatencyStats
import com.phonediagnostic.data.LatencyTarget
import com.phonediagnostic.data.LoadTestProgress
import com.phonediagnostic.data.LoadTestResult
import com.phonediagnostic.data.LoadMode
import com.phonediagnostic.data.LoadTester
import com.phonediagnostic.data.MetricHistory
import com.phonediagnostic.data.MetricSample
import com.phonediagnostic.data.NetworkDetail
import com.phonediagnostic.data.PrivacyAudit
import com.phonediagnostic.data.PrivacyAuditor
import com.phonediagnostic.data.ProcessRamEntry
import com.phonediagnostic.data.ThemeMode
import com.phonediagnostic.data.UsageCollector
import com.phonediagnostic.data.elevated.AccessTier
import com.phonediagnostic.data.elevated.ElevatedAccessManager
import com.phonediagnostic.data.elevated.ElevatedStatus
import com.phonediagnostic.service.MonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class AppScreen {
    DASHBOARD,
    CPU,
    BATTERY,
    SENSORS,
    MORE,
    SETTINGS,
    ABOUT,
    RAM_DETAIL,
    STORAGE_DETAIL,
    TOOLS,
    THERMALS,
    HISTORY,
    NETWORK,
    SENSOR_DETAIL,
    PRIVACY
}

fun AppScreen.isMainTab(): Boolean = when (this) {
    AppScreen.DASHBOARD,
    AppScreen.CPU,
    AppScreen.BATTERY,
    AppScreen.SENSORS,
    AppScreen.MORE -> true
    else -> false
}

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefs = AppPreferences(appContext)
    private val collector = DeviceInfoCollector(appContext)
    private val usageCollector = UsageCollector(appContext)
    private val log = DiagnosticLog.get(appContext)
    private val history = MetricHistory.get(appContext)
    private val elevatedManager = ElevatedAccessManager(appContext)

    /**
     * Serialises full and live collects. A full pass (EGL GPU probe + optional
     * sensor sampling) can exceed [LIVE_INTERVAL_MS], so without this the live
     * loop, refreshNow and the elevated-shell observer could overlap and race
     * on [_report] and the collector's elevated shell reference.
     */
    private val collectionMutex = Mutex()

    /** Live view of what elevated access is available and active, for Settings. */
    val elevatedStatus: StateFlow<ElevatedStatus> = elevatedManager.status

    private val _report = MutableStateFlow<FullDeviceReport?>(null)
    val report: StateFlow<FullDeviceReport?> = _report.asStateFlow()

    private val _isLive = MutableStateFlow(true)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private val _lastUpdated = MutableStateFlow("")
    val lastUpdated: StateFlow<String> = _lastUpdated.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.DASHBOARD)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    /** Drives whether the system back gesture is intercepted. */
    private val _canNavigateBack = MutableStateFlow(false)
    val canNavigateBack: StateFlow<Boolean> = _canNavigateBack.asStateFlow()

    /** Screens visited before the current one. Mutated from the main thread only. */
    private val backStack = ArrayList<AppScreen>()

    private val _networkProbeEnabled = MutableStateFlow(prefs.networkProbeEnabled)
    val networkProbeEnabled: StateFlow<Boolean> = _networkProbeEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val privacyAuditor = PrivacyAuditor(appContext)

    private val _privacyAudit = MutableStateFlow<PrivacyAudit?>(null)
    val privacyAudit: StateFlow<PrivacyAudit?> = _privacyAudit.asStateFlow()

    private val _privacyLoading = MutableStateFlow(false)
    val privacyLoading: StateFlow<Boolean> = _privacyLoading.asStateFlow()

    private val _latencyTarget = MutableStateFlow(prefs.latencyTarget)
    val latencyTarget: StateFlow<LatencyTarget> = _latencyTarget.asStateFlow()

    private val _redactReports = MutableStateFlow(prefs.redactReports)
    val redactReports: StateFlow<Boolean> = _redactReports.asStateFlow()

    private val _secureScreens = MutableStateFlow(prefs.secureScreens)
    val secureScreens: StateFlow<Boolean> = _secureScreens.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _processRam = MutableStateFlow<List<ProcessRamEntry>?>(null)
    val processRam: StateFlow<List<ProcessRamEntry>?> = _processRam.asStateFlow()

    private val _processRamLoading = MutableStateFlow(false)
    val processRamLoading: StateFlow<Boolean> = _processRamLoading.asStateFlow()

    private val _appStorage = MutableStateFlow<List<AppStorageEntry>?>(null)
    val appStorage: StateFlow<List<AppStorageEntry>?> = _appStorage.asStateFlow()

    private val _appStorageLoading = MutableStateFlow(false)
    val appStorageLoading: StateFlow<Boolean> = _appStorageLoading.asStateFlow()

    private val _hasUsageStats = MutableStateFlow(usageCollector.hasUsageStatsPermission())
    val hasUsageStats: StateFlow<Boolean> = _hasUsageStats.asStateFlow()

    private val _usageAccessRestricted = MutableStateFlow(usageCollector.mayNeedRestrictedSettingUnblock())
    val usageAccessRestricted: StateFlow<Boolean> = _usageAccessRestricted.asStateFlow()

    private val _logLines = MutableStateFlow(log.snapshot())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    private val _loadTesting = MutableStateFlow(false)
    val loadTesting: StateFlow<Boolean> = _loadTesting.asStateFlow()

    private val _loadProgress = MutableStateFlow<LoadTestProgress?>(null)
    val loadProgress: StateFlow<LoadTestProgress?> = _loadProgress.asStateFlow()

    private val _lastLoadResult = MutableStateFlow<LoadTestResult?>(null)
    val lastLoadResult: StateFlow<LoadTestResult?> = _lastLoadResult.asStateFlow()

    private val _metricHistory = MutableStateFlow(history.snapshot())
    val metricHistory: StateFlow<List<MetricSample>> = _metricHistory.asStateFlow()

    private val _networkDetail = MutableStateFlow<NetworkDetail?>(null)
    val networkDetail: StateFlow<NetworkDetail?> = _networkDetail.asStateFlow()

    private val _networkDetailLoading = MutableStateFlow(false)
    val networkDetailLoading: StateFlow<Boolean> = _networkDetailLoading.asStateFlow()

    private val _latencyStats = MutableStateFlow<LatencyStats?>(null)
    val latencyStats: StateFlow<LatencyStats?> = _latencyStats.asStateFlow()

    private val _latencyRunning = MutableStateFlow(false)
    val latencyRunning: StateFlow<Boolean> = _latencyRunning.asStateFlow()

    private val _backgroundMonitorEnabled = MutableStateFlow(prefs.backgroundMonitorEnabled)
    val backgroundMonitorEnabled: StateFlow<Boolean> = _backgroundMonitorEnabled.asStateFlow()

    /**
     * Whether the activity is started. viewModelScope outlives onStop, so
     * without this the live loop kept probing the network and waking sensors
     * every few seconds with the app in the background.
     */
    private val _inForeground = MutableStateFlow(true)

    /** Name of the sensor opened in the live detail view, if any. */
    private val _selectedSensor = MutableStateFlow<String?>(null)
    val selectedSensor: StateFlow<String?> = _selectedSensor.asStateFlow()

    init {
        collector.latencyTarget = prefs.latencyTarget
        viewModelScope.launch(Dispatchers.Default) {
            runCollection(full = true)
        }

        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(LIVE_INTERVAL_MS)
                if (_isLive.value && _inForeground.value &&
                    !_isRefreshing.value && !_loadTesting.value
                ) {
                    runCollection(full = _report.value == null)
                }
            }
        }

        if (prefs.backgroundMonitorEnabled) {
            MonitorService.start(appContext)
        }

        // Feed the collector whichever elevated shell is live, and re-collect
        // once one becomes available so gated data (battery fuel gauge, per-core
        // clocks) appears without the user having to hit refresh.
        viewModelScope.launch {
            elevatedManager.activeShell.collect { shell ->
                val gained = collector.elevated == null && shell != null
                collector.elevated = shell
                usageCollector.elevated = shell
                if (gained && !_isRefreshing.value && !_loadTesting.value) {
                    viewModelScope.launch(Dispatchers.Default) { runCollection(full = true) }
                }
            }
        }
    }

    fun setInForeground(inForeground: Boolean) {
        _inForeground.value = inForeground
        if (inForeground) {
            val enabled = prefs.backgroundMonitorEnabled
            if (_backgroundMonitorEnabled.value != enabled) {
                _backgroundMonitorEnabled.value = enabled
            }
        }
    }

    fun toggleLive() {
        _isLive.value = !_isLive.value
    }

    fun refreshNow() {
        viewModelScope.launch(Dispatchers.Default) {
            _isRefreshing.value = true
            try {
                runCollection(full = true)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ---------------------------------------------------------------- navigation

    private fun navigateTo(target: AppScreen) {
        val current = _screen.value
        if (current == target) return
        backStack.add(current)
        while (backStack.size > MAX_BACK_STACK) {
            backStack.removeAt(0)
        }
        _screen.value = target
        _canNavigateBack.value = true
    }

    fun selectMainTab(tab: AppScreen) {
        if (!tab.isMainTab()) return
        backStack.clear()
        if (tab != AppScreen.DASHBOARD) {
            backStack.add(AppScreen.DASHBOARD)
        }
        _screen.value = tab
        _canNavigateBack.value = backStack.isNotEmpty()
        if (tab == AppScreen.MORE) {
            refreshHistory()
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) {
            _canNavigateBack.value = false
            return false
        }
        val previous = backStack.removeAt(backStack.size - 1)
        _screen.value = previous
        _canNavigateBack.value = backStack.isNotEmpty()
        return true
    }

    /** Whether [screen] is waiting underneath the current one to be returned to. */
    fun isOnBackStack(screen: AppScreen): Boolean = screen in backStack

    fun openSettings() = navigateTo(AppScreen.SETTINGS)
    fun openAbout() = navigateTo(AppScreen.ABOUT)
    fun openThermals() = navigateTo(AppScreen.THERMALS)

    fun openTools() {
        refreshLog()
        navigateTo(AppScreen.TOOLS)
    }

    fun openRamDetail() {
        navigateTo(AppScreen.RAM_DETAIL)
        loadProcessRam()
    }

    fun openStorageDetail() {
        navigateTo(AppScreen.STORAGE_DETAIL)
        refreshUsagePermission()
        if (_hasUsageStats.value) {
            loadAppStorage()
        }
    }

    fun openHistory() {
        refreshHistory()
        navigateTo(AppScreen.HISTORY)
    }

    fun openNetwork() {
        navigateTo(AppScreen.NETWORK)
        loadNetworkDetail()
    }

    fun openSensorDetail(sensorName: String) {
        _selectedSensor.value = sensorName
        navigateTo(AppScreen.SENSOR_DETAIL)
    }

    // ------------------------------------------------------------------- details

    fun loadProcessRam() {
        viewModelScope.launch(Dispatchers.Default) {
            _processRamLoading.value = true
            try {
                _processRam.value = usageCollector.collectProcessRam()
            } finally {
                _processRamLoading.value = false
            }
        }
    }

    fun loadAppStorage() {
        viewModelScope.launch(Dispatchers.Default) {
            _appStorageLoading.value = true
            try {
                refreshUsagePermission()
                _appStorage.value = if (_hasUsageStats.value) {
                    usageCollector.collectAppStorage()
                } else {
                    null
                }
            } finally {
                _appStorageLoading.value = false
            }
        }
    }

    fun openPrivacy() {
        navigateTo(AppScreen.PRIVACY)
        loadPrivacyAudit()
    }

    fun loadPrivacyAudit() {
        viewModelScope.launch(Dispatchers.Default) {
            _privacyLoading.value = true
            try {
                _privacyAudit.value = privacyAuditor.collect()
            } catch (_: Exception) {
                _privacyAudit.value = null
            } finally {
                _privacyLoading.value = false
            }
        }
    }

    fun loadNetworkDetail() {
        viewModelScope.launch(Dispatchers.Default) {
            _networkDetailLoading.value = true
            try {
                _networkDetail.value = collector.collectNetworkDetail()
            } catch (_: Exception) {
                _networkDetail.value = null
            } finally {
                _networkDetailLoading.value = false
            }
        }
    }

    fun runLatencyTest(samples: Int = 5) {
        if (_latencyRunning.value) return
        if (!_networkProbeEnabled.value) return
        viewModelScope.launch {
            _latencyRunning.value = true
            try {
                _latencyStats.value = withContext(Dispatchers.IO) {
                    collector.measureLatencyStats(samples)
                }
            } catch (_: Exception) {
                _latencyStats.value = null
            } finally {
                _latencyRunning.value = false
            }
        }
    }

    fun refreshUsagePermission() {
        _hasUsageStats.value = usageCollector.hasUsageStatsPermission()
        _usageAccessRestricted.value = usageCollector.mayNeedRestrictedSettingUnblock()
    }

    fun refreshHistory() {
        _metricHistory.value = history.snapshot()
    }

    fun clearHistory() {
        history.clear()
        refreshHistory()
        log.append("Metric history cleared")
        refreshLog()
    }

    // ------------------------------------------------------------------ settings

    fun setNetworkProbeEnabled(enabled: Boolean) {
        prefs.networkProbeEnabled = enabled
        _networkProbeEnabled.value = enabled
        if (!enabled) {
            _latencyStats.value = null
        }
        refreshNow()
    }

    fun setBackgroundMonitorEnabled(enabled: Boolean) {
        prefs.backgroundMonitorEnabled = enabled
        _backgroundMonitorEnabled.value = enabled
        if (enabled) {
            MonitorService.start(appContext)
            log.append("Background monitor enabled")
        } else {
            MonitorService.stop(appContext)
            log.append("Background monitor disabled")
        }
        refreshLog()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun setLatencyTarget(target: LatencyTarget) {
        prefs.latencyTarget = target
        _latencyTarget.value = target
        collector.latencyTarget = target
        _latencyStats.value = null
        refreshNow()
    }

    fun setRedactReports(enabled: Boolean) {
        prefs.redactReports = enabled
        _redactReports.value = enabled
    }

    fun setSecureScreens(enabled: Boolean) {
        prefs.secureScreens = enabled
        _secureScreens.value = enabled
    }

    // ------------------------------------------------------------ elevated access

    fun setAccessTier(tier: AccessTier) {
        elevatedManager.setPreferredTier(tier)
    }

    fun requestShizukuPermission() {
        elevatedManager.requestShizukuPermission()
    }

    fun refreshElevatedStatus() {
        elevatedManager.refresh()
    }

    fun refreshLog() {
        _logLines.value = log.snapshot()
    }

    fun clearLog() {
        log.clear()
        refreshLog()
    }

    // ----------------------------------------------------------------- load test

    fun runLoadTest(durationSec: Int, thermal: Boolean = false) {
        if (_loadTesting.value) return
        viewModelScope.launch(Dispatchers.Default) {
            _loadTesting.value = true
            _loadProgress.value = null
            try {
                val modeLabel = if (thermal) "thermal" else "standard"
                log.append("Load test starting ($modeLabel, ${durationSec / 60} min)")
                refreshLog()
                val result = LoadTester.run(
                    context = appContext,
                    durationSec = durationSec,
                    threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                    mode = if (thermal) LoadMode.THERMAL else LoadMode.STANDARD,
                    onProgress = { progress ->
                        _loadProgress.value = progress
                    }
                )
                _lastLoadResult.value = result
                refreshLog()
                runCollection(full = true)
            } catch (e: Exception) {
                log.append("Load test failed: ${e.message ?: e.javaClass.simpleName}")
                refreshLog()
            } finally {
                _loadTesting.value = false
                _loadProgress.value = null
            }
        }
    }

    // ---------------------------------------------------------------- collection

    /**
     * Serialised full / live collect. The mutex prevents the live loop, pull-to-refresh,
     * elevated-shell observer and load-test completion from overlapping on the collector
     * and on [_report].
     */
    private suspend fun runCollection(full: Boolean) {
        collectionMutex.withLock {
            try {
                val probe = _networkProbeEnabled.value
                val current = _report.value
                val sampleSensors = _screen.value == AppScreen.SENSORS ||
                    _screen.value == AppScreen.SENSOR_DETAIL
                val next = if (full || current == null) {
                    collector.collect(networkProbe = probe, sampleSensors = sampleSensors)
                } else {
                    collector.collectLive(current, networkProbe = probe, sampleSensors = sampleSensors)
                }
                _report.value = next
                _lastUpdated.value = currentTimeLabel()
                _errorMessage.value = null
                recordHistory(next)
            } catch (e: Exception) {
                if (_report.value == null) {
                    _errorMessage.value = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    private fun recordHistory(report: FullDeviceReport) {
        val stored = history.record(
            MetricSample(
                timestampMs = System.currentTimeMillis(),
                batteryPct = report.battery.level,
                batteryTempC = report.battery.temperature,
                ramUsedMb = report.memory.usedRamMb,
                ramTotalMb = report.memory.totalRamMb,
                charging = report.battery.isCharging
            )
        )
        if (stored && (_screen.value == AppScreen.HISTORY || _screen.value == AppScreen.MORE)) {
            _metricHistory.value = history.snapshot()
        }
    }

    private fun currentTimeLabel(): String {
        val now = java.util.Calendar.getInstance()
        return String.format(
            java.util.Locale.US,
            "%02d:%02d:%02d",
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE),
            now.get(java.util.Calendar.SECOND)
        )
    }

    companion object {
        private const val LIVE_INTERVAL_MS = 3000L
        private const val MAX_BACK_STACK = 16
    }
}
