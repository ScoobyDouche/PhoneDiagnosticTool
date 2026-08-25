package com.phonediagnostic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonediagnostic.data.DeviceInfoCollector
import com.phonediagnostic.data.FullDeviceReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val collector = DeviceInfoCollector(application.applicationContext)

    private val _report = MutableStateFlow<FullDeviceReport?>(null)
    val report: StateFlow<FullDeviceReport?> = _report.asStateFlow()

    private val _isLive = MutableStateFlow(true)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private val _lastUpdated = MutableStateFlow("")
    val lastUpdated: StateFlow<String> = _lastUpdated.asStateFlow()

    init {
        // Initial full collection
        viewModelScope.launch(Dispatchers.Default) {
            val initial = collector.collect()
            _report.value = initial
            _lastUpdated.value = currentTimeLabel()
        }

        // Live updates every 2 seconds (battery, memory, uptime)
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(2000)
                if (_isLive.value) {
                    val current = _report.value
                    if (current != null) {
                        val updated = collector.collectLive(current)
                        _report.value = updated
                        _lastUpdated.value = currentTimeLabel()
                    } else {
                        val full = collector.collect()
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
            val full = collector.collect()
            _report.value = full
            _lastUpdated.value = currentTimeLabel()
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
}
