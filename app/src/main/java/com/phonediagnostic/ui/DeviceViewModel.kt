package com.phonediagnostic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonediagnostic.data.AppPreferences
import com.phonediagnostic.data.AppStorageEntry
import com.phonediagnostic.data.DeviceInfoCollector
import com.phonediagnostic.data.DiagnosticLog
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.data.LoadTestProgress
import com.phonediagnostic.data.LoadTestResult
import com.phonediagnostic.data.LoadTester
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

enum class AppScreen {
    DASHBOARD,
    SETTINGS,
    ABOUT,
    RAM_DETAIL,
    STORAGE_DETAIL,
    TOOLS
}

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefs = AppPreferences(appContext)
    private val collector = DeviceInfoCollector(appContext)
    private val usageCollector = UsageCollector(appContext)
    private val log = DiagnosticLog.get(appContext)

    private val _report = MutableStateFlow<FullDeviceReport?>(null)
    val report: StateFlow<FullDeviceReport?> = _report.asStateFlow()

    private val _isLive = MutableStateFlow(true)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private val _lastUpdated = MutableStateFlow("")
    val lastUpdated: StateFlow<String> = _lastUpdated.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.DASHBOARD)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

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

    fun clearError() {
        _errorMessage.value = null
    }

    fun openSettings() {
        _screen.value = AppScreen.SETTINGS
    }

    fun openAbout() {
        _screen.value = AppScreen.ABOUT
    }

    fun openDashboard() {
        _screen.value = AppScreen.DASHBOARD
    }

    fun openTools() {
        refreshLog()
        _screen.value = AppScreen.TOOLS
    }

    fun openRamDetail() {
        _screen.value = AppScreen.RAM_DETAIL
        loadProcessRam()
    }

    fun openStorageDetail() {
        _screen.value = AppScreen.STORAGE_DETAIL
        refreshUsagePermission()
        if (_hasUsageStats.value) {
            loadAppStorage()
        }
    }

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

    fun refreshUsagePermission() {
        _hasUsageStats.value = usageCollector.hasUsageStatsPermission()
    }

    fun setNetworkProbeEnabled(enabled: Boolean) {
        prefs.networkProbeEnabled = enabled
        _networkProbeEnabled.value = enabled
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
                    threads = 4,
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

    private fun runCollection(full: Boolean) {
        try {
            val probe = _networkProbeEnabled.value
            val current = _report.value
            val next = if (full || current == null) {
                collector.collect(networkProbe = probe)
            } else {
                collector.collectLive(current, networkProbe = probe)
            }
            _report.value = next
            _lastUpdated.value = currentTimeLabel()
            _errorMessage.value = null
        } catch (e: Exception) {
            if (_report.value == null) {
                _errorMessage.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    private fun currentTimeLabel(): String {
        val now = java.util.Calendar.getInstance()
        return String.format(
            "%02d:%02d:%02d",
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE),
            now.get(java.util.Calendar.SECOND)
        )
    }

    companion object {
        private const val LIVE_INTERVAL_MS = 3000L
    }
}
