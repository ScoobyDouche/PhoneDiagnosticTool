package com.phonediagnostic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonediagnostic.data.AppPreferences
import com.phonediagnostic.data.DeviceInfoCollector
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.data.ThemeMode
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
    ABOUT
}

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application.applicationContext)
    private val collector = DeviceInfoCollector(application.applicationContext)

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

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val initial = collector.collect(networkProbe = _networkProbeEnabled.value)
            _report.value = initial
            _lastUpdated.value = currentTimeLabel()
        }

        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(2000)
                if (_isLive.value) {
                    val current = _report.value
                    if (current != null) {
                        val updated = collector.collectLive(current, networkProbe = _networkProbeEnabled.value)
                        _report.value = updated
                        _lastUpdated.value = currentTimeLabel()
                    } else {
                        val full = collector.collect(networkProbe = _networkProbeEnabled.value)
                        _report.value = full
                        _lastUpdated.value = currentTimeLabel()
                    }
                }
            }
        }
    }

    fun toggleLive() {
        _isLive.value = !_isLive.value
    }

    fun refreshNow() {
        viewModelScope.launch(Dispatchers.Default) {
            val full = collector.collect(networkProbe = _networkProbeEnabled.value)
            _report.value = full
            _lastUpdated.value = currentTimeLabel()
        }
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

    fun setNetworkProbeEnabled(enabled: Boolean) {
        prefs.networkProbeEnabled = enabled
        _networkProbeEnabled.value = enabled
        refreshNow()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _themeMode.value = mode
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
}
