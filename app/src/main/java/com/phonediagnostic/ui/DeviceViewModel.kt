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
import com.phonediagnostic.data.LoadTestProgress
import com.phonediagnostic.data.LoadTestResult
import com.phonediagnostic.data.LoadTester
import com.phonediagnostic.data.MetricHistory
import com.phonediagnostic.data.MetricSample
import com.phonediagnostic.data.NetworkDetail
import com.phonediagnostic.data.ProcessRamEntry
import com.phonediagnostic.data.ThemeMode
import com.phonediagnostic.data.UsageCollector
import com.phonediagnostic.service.MonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    SENSOR_DETAIL
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

    private val _backgroundMonitorEnabled = MutableStateFlow(prefs.backgroundMonitorEnabled)
    val backgroundMonitorEnabled: StateFlow<Boolean> = _backgroundMonitorEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

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

    /** Name of the sensor opened in the live detail view, if any. */
    private val _selectedSensor = MutableStateFlow<String?>(null)
    val selectedSensor: StateFlow<String?> = _selectedSensor.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            runCollection(full = true)
        }

        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(LIVE_INTERVAL_MS)
                if (_isLive.value && !_isRefreshing.value && !_loadTesting.value) {
                    runCollection(full = _report.value == null)
                }
            }
        }

        if (prefs.backgroundMonitorEnabled) {
            MonitorService.start(appContext)
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

    /**
     * Bottom-tab destinations are roots. Selecting one clears the stack, but a
     * non-Overview tab still returns to Overview on back, matching how the
     * bottom bar reads.
     */
    fun selectMainTab(tab: AppScreen) {
        if (!tab.isMainTab()) return
        backStack.clear()
        if (tab != AppScreen.DASHBOARD) {
            backStack.add(AppScreen.DASHBOARD)
        }
        _screen.value = tab
        _canNavigateBack.value = backStack.isNotEmpty()
    }

    /**
     * Pops one entry.
     *
     * @return false when nothing was left to pop, so the caller can let the
     *   system handle back and leave the app.
     */
    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) {
            _canNavigateBack.value = false
            return false
        }
        // removeAt rather than removeLast: the latter collides with the JDK 21
        // SequencedCollection method, which does not exist below API 35.
        val previous = backStack.removeAt(backStack.size - 1)
        _screen.value = previous
        _canNavigateBack.value = backStack.isNotEmpty()
        return true
    }

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
                // Blocking socket work belongs on IO, not on the small
                // Default pool that also serves collection.
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

    fun refreshLog() {
        _logLines.value = log.snapshot()
    }

    fun clearLog() {
        log.clear()
        refreshLog()
    }

    // ----------------------------------------------------------------- load test

    fun runLoadTest(durationSec: Int) {
        if (_loadTesting.value) return
        viewModelScope.launch(Dispatchers.Default) {
            _loadTesting.value = true
            _loadProgress.value = null
            try {
                log.append("Load test starting (${durationSec / 60} min)")
                refreshLog()
                val result = LoadTester.run(
                    context = appContext,
                    durationSec = durationSec,
                    threads = LOAD_TEST_THREADS,
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

    private fun runCollection(full: Boolean) {
        try {
            val probe = _networkProbeEnabled.value
            val current = _report.value
            // Sampling sensors wakes them, so only do it where readings are shown.
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

    /**
     * Feeds foreground samples into the same history the background monitor
     * writes, so trends exist even with the monitor switched off.
     * [MetricHistory] rate-limits, so offering every tick is fine.
     */
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
        if (stored && _screen.value == AppScreen.HISTORY) {
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
        private const val LOAD_TEST_THREADS = 4
    }
}
