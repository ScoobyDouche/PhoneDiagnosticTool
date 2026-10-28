package com.phonediagnostic

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonediagnostic.data.ReportExporter
import com.phonediagnostic.data.ThemeMode
import com.phonediagnostic.service.MonitorService
import com.phonediagnostic.tile.QuickStatusTileService
import com.phonediagnostic.ui.AboutScreen
import com.phonediagnostic.ui.AppScreen
import com.phonediagnostic.ui.BatteryScreen
import com.phonediagnostic.ui.CpuScreen
import com.phonediagnostic.ui.DashboardScreen
import com.phonediagnostic.ui.DeviceViewModel
import com.phonediagnostic.ui.HistoryScreen
import com.phonediagnostic.ui.MoreScreen
import com.phonediagnostic.ui.NetworkScreen
import com.phonediagnostic.ui.RamDetailScreen
import com.phonediagnostic.ui.SensorDetailScreen
import com.phonediagnostic.ui.SensorsScreen
import com.phonediagnostic.ui.SettingsScreen
import com.phonediagnostic.ui.StorageDetailScreen
import com.phonediagnostic.ui.ThermalsScreen
import com.phonediagnostic.ui.ToolsScreen
import com.phonediagnostic.ui.theme.PhoneDiagnosticTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: DeviceViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setBackgroundMonitorEnabled(true)
        } else {
            Toast.makeText(this, R.string.toast_notification_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PhoneDiagnosticTheme(darkTheme = dark) {
                val report by viewModel.report.collectAsStateWithLifecycle()
                val isLive by viewModel.isLive.collectAsStateWithLifecycle()
                val lastUpdated by viewModel.lastUpdated.collectAsStateWithLifecycle()
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                val canNavigateBack by viewModel.canNavigateBack.collectAsStateWithLifecycle()
                val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
                val processRam by viewModel.processRam.collectAsStateWithLifecycle()
                val processRamLoading by viewModel.processRamLoading.collectAsStateWithLifecycle()
                val appStorage by viewModel.appStorage.collectAsStateWithLifecycle()
                val appStorageLoading by viewModel.appStorageLoading.collectAsStateWithLifecycle()
                val hasUsageStats by viewModel.hasUsageStats.collectAsStateWithLifecycle()
                val usageAccessRestricted by viewModel.usageAccessRestricted.collectAsStateWithLifecycle()
                val logLines by viewModel.logLines.collectAsStateWithLifecycle()
                val loadTesting by viewModel.loadTesting.collectAsStateWithLifecycle()
                val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
                val lastLoadResult by viewModel.lastLoadResult.collectAsStateWithLifecycle()
                val metricHistory by viewModel.metricHistory.collectAsStateWithLifecycle()
                val networkDetail by viewModel.networkDetail.collectAsStateWithLifecycle()
                val networkDetailLoading by viewModel.networkDetailLoading.collectAsStateWithLifecycle()
                val latencyStats by viewModel.latencyStats.collectAsStateWithLifecycle()
                val latencyRunning by viewModel.latencyRunning.collectAsStateWithLifecycle()
                val networkProbe by viewModel.networkProbeEnabled.collectAsStateWithLifecycle()
                val bgMonitor by viewModel.backgroundMonitorEnabled.collectAsStateWithLifecycle()
                val elevatedStatus by viewModel.elevatedStatus.collectAsStateWithLifecycle()
                val selectedSensor by viewModel.selectedSensor.collectAsStateWithLifecycle()

                BackHandler(enabled = canNavigateBack) {
                    viewModel.navigateBack()
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> viewModel.setInForeground(true)
                            Lifecycle.Event.ON_STOP -> viewModel.setInForeground(false)
                            else -> {}
                        }
                        if (event == Lifecycle.Event.ON_RESUME) {
                            if (viewModel.screen.value == AppScreen.HISTORY) {
                                viewModel.refreshHistory()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (screen.isMainTab()) {
                            MainBottomBar(
                                current = screen,
                                onSelect = { viewModel.selectMainTab(it) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (screen) {
                            AppScreen.DASHBOARD -> {
                                DashboardScreen(
                                    report = report,
                                    isLive = isLive,
                                    lastUpdated = lastUpdated,
                                    isRefreshing = isRefreshing,
                                    errorMessage = errorMessage,
                                    versionName = BuildConfig.VERSION_NAME,
                                    onToggleLive = { viewModel.toggleLive() },
                                    onRefresh = { viewModel.refreshNow() },
                                    onOpenRamDetail = { viewModel.openRamDetail() },
                                    onOpenStorageDetail = { viewModel.openStorageDetail() },
                                    onOpenNetwork = { viewModel.openNetwork() },
                                    onOpenHistory = { viewModel.openHistory() }
                                )
                            }
                            AppScreen.CPU -> {
                                CpuScreen(
                                    cpu = report?.cpu,
                                    isLive = isLive,
                                    isRefreshing = isRefreshing,
                                    onRefresh = { viewModel.refreshNow() }
                                )
                            }
                            AppScreen.BATTERY -> {
                                BatteryScreen(
                                    battery = report?.battery,
                                    thermals = report?.thermals.orEmpty(),
                                    isLive = isLive,
                                    isRefreshing = isRefreshing,
                                    onRefresh = { viewModel.refreshNow() },
                                    onOpenThermals = { viewModel.openThermals() },
                                    onOpenHistory = { viewModel.openHistory() }
                                )
                            }
                            AppScreen.SENSORS -> {
                                SensorsScreen(
                                    sensors = report?.sensors.orEmpty(),
                                    cameras = report?.cameras.orEmpty(),
                                    isRefreshing = isRefreshing,
                                    onBack = null,
                                    onRefresh = { viewModel.refreshNow() },
                                    onOpenSensor = { viewModel.openSensorDetail(it) }
                                )
                            }
                            AppScreen.MORE -> {
                                MoreScreen(
                                    report = report,
                                    versionName = BuildConfig.VERSION_NAME,
                                    historySamples = metricHistory.size,
                                    onOpenRam = { viewModel.openRamDetail() },
                                    onOpenStorage = { viewModel.openStorageDetail() },
                                    onOpenNetwork = { viewModel.openNetwork() },
                                    onOpenHistory = { viewModel.openHistory() },
                                    onOpenThermals = { viewModel.openThermals() },
                                    onOpenTools = { viewModel.openTools() },
                                    onOpenSettings = { viewModel.openSettings() },
                                    onOpenAbout = { viewModel.openAbout() },
                                    onShareText = { shareText() },
                                    onShareJson = { shareJson() },
                                    onShareFile = { shareAsFile() },
                                    onSaveText = { saveText() },
                                    onSaveJson = { saveJson() },
                                    onCopyText = { copyText() }
                                )
                            }
                            AppScreen.SETTINGS -> {
                                LaunchedEffect(Unit) { viewModel.refreshElevatedStatus() }
                                SettingsScreen(
                                    networkProbeEnabled = networkProbe,
                                    backgroundMonitorEnabled = bgMonitor,
                                    themeMode = themeMode,
                                    elevatedStatus = elevatedStatus,
                                    onNetworkProbeChange = { viewModel.setNetworkProbeEnabled(it) },
                                    onBackgroundMonitorChange = { enabled ->
                                        if (enabled) {
                                            requestNotificationThenStartMonitor()
                                        } else {
                                            viewModel.setBackgroundMonitorEnabled(false)
                                        }
                                    },
                                    onThemeModeChange = { viewModel.setThemeMode(it) },
                                    onAccessTierChange = { viewModel.setAccessTier(it) },
                                    onRequestShizuku = { viewModel.requestShizukuPermission() },
                                    onRefreshElevated = { viewModel.refreshElevatedStatus() },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.ABOUT -> {
                                AboutScreen(
                                    versionName = BuildConfig.VERSION_NAME,
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.RAM_DETAIL -> {
                                RamDetailScreen(
                                    memory = report?.memory,
                                    processes = processRam,
                                    loading = processRamLoading,
                                    onBack = { viewModel.navigateBack() },
                                    onRefresh = { viewModel.loadProcessRam() }
                                )
                            }
                            AppScreen.STORAGE_DETAIL -> {
                                StorageDetailScreen(
                                    storage = report?.storage,
                                    apps = appStorage,
                                    loading = appStorageLoading,
                                    hasUsageStats = hasUsageStats,
                                    usageAccessRestricted = usageAccessRestricted,
                                    onBack = { viewModel.navigateBack() },
                                    onRefresh = { viewModel.loadAppStorage() },
                                    onOpenUsageAccess = { openUsageAccessSettings() }
                                )
                            }
                            AppScreen.TOOLS -> {
                                ToolsScreen(
                                    logLines = logLines,
                                    loadTesting = loadTesting,
                                    loadProgress = loadProgress,
                                    lastLoadResult = lastLoadResult,
                                    onBack = { viewModel.navigateBack() },
                                    onRefreshLog = { viewModel.refreshLog() },
                                    onClearLog = { viewModel.clearLog() },
                                    onRunLoadTest = { mins, thermal ->
                                        viewModel.runLoadTest(mins * 60, thermal)
                                    }
                                )
                            }
                            AppScreen.THERMALS -> {
                                ThermalsScreen(
                                    zones = report?.thermals.orEmpty(),
                                    isLive = isLive,
                                    isRefreshing = isRefreshing,
                                    onBack = { viewModel.navigateBack() },
                                    onRefresh = { viewModel.refreshNow() }
                                )
                            }
                            AppScreen.HISTORY -> {
                                HistoryScreen(
                                    samples = metricHistory,
                                    monitorEnabled = bgMonitor,
                                    onBack = { viewModel.navigateBack() },
                                    onRefresh = { viewModel.refreshHistory() },
                                    onClear = { viewModel.clearHistory() }
                                )
                            }
                            AppScreen.NETWORK -> {
                                NetworkScreen(
                                    network = report?.network,
                                    detail = networkDetail,
                                    isLoading = networkDetailLoading,
                                    probeEnabled = networkProbe,
                                    latency = latencyStats,
                                    latencyRunning = latencyRunning,
                                    onBack = { viewModel.navigateBack() },
                                    onRefresh = { viewModel.loadNetworkDetail() },
                                    onRunLatency = { viewModel.runLatencyTest() }
                                )
                            }
                            AppScreen.SENSOR_DETAIL -> {
                                SensorDetailScreen(
                                    sensorName = selectedSensor,
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationThenStartMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.setBackgroundMonitorEnabled(true)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.setBackgroundMonitorEnabled(true)
        }
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_open_settings_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareText() {
        val r = viewModel.report.value ?: return
        val text = ReportExporter.toShareText(r)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.action_share)))
    }

    private fun shareJson() {
        val r = viewModel.report.value ?: return
        val text = ReportExporter.toJson(r)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.action_share)))
    }

    private fun shareAsFile() {
        val r = viewModel.report.value ?: return
        val text = ReportExporter.toShareText(r)
        val name = ReportExporter.suggestedFileName(r, "txt")
        val file = File(cacheDir, name)
        file.writeText(text)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, getString(R.string.action_share)))
    }

    private fun saveText() {
        val r = viewModel.report.value ?: return
        val text = ReportExporter.toShareText(r)
        val name = ReportExporter.suggestedFileName(r, "txt")
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, name)
        file.writeText(text)
        Toast.makeText(this, getString(R.string.toast_saved, file.absolutePath), Toast.LENGTH_LONG).show()
    }

    private fun saveJson() {
        val r = viewModel.report.value ?: return
        val text = ReportExporter.toJson(r)
        val name = ReportExporter.suggestedFileName(r, "json")
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, name)
        file.writeText(text)
        Toast.makeText(this, getString(R.string.toast_saved, file.absolutePath), Toast.LENGTH_LONG).show()
    }

    private fun copyText() {
        val r = viewModel.report.value ?: return
        val text = ReportExporter.toShareText(r)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("report", text))
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }
}

data class TabItem(
    val screen: AppScreen,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun MainBottomBar(current: AppScreen, onSelect: (AppScreen) -> Unit) {
    val tabs = listOf(
        TabItem(AppScreen.DASHBOARD, R.string.tab_overview, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        TabItem(AppScreen.CPU, R.string.tab_cpu, Icons.Filled.Memory, Icons.Outlined.Memory),
        TabItem(AppScreen.BATTERY, R.string.tab_battery, Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull),
        TabItem(AppScreen.SENSORS, R.string.tab_sensors, Icons.Filled.Sensors, Icons.Outlined.Sensors),
        TabItem(AppScreen.MORE, R.string.tab_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
    )
    NavigationBar {
        tabs.forEach { tab ->
            val selected = current == tab.screen
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.screen) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label) }
            )
        }
    }
}
