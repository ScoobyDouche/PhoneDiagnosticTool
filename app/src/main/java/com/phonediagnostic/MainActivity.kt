package com.phonediagnostic

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonediagnostic.data.ReportExporter
import com.phonediagnostic.data.ThemeMode
import com.phonediagnostic.ui.AboutScreen
import com.phonediagnostic.ui.AppScreen
import com.phonediagnostic.ui.BatteryScreen
import com.phonediagnostic.ui.CpuScreen
import com.phonediagnostic.ui.DashboardScreen
import com.phonediagnostic.ui.DeviceViewModel
import com.phonediagnostic.ui.MoreScreen
import com.phonediagnostic.ui.RamDetailScreen
import com.phonediagnostic.ui.SensorsScreen
import com.phonediagnostic.ui.SettingsScreen
import com.phonediagnostic.ui.StorageDetailScreen
import com.phonediagnostic.ui.ThermalsScreen
import com.phonediagnostic.ui.ToolsScreen
import com.phonediagnostic.ui.isMainTab
import com.phonediagnostic.ui.theme.PhoneDiagnosticTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DeviceViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.setBackgroundMonitorEnabled(true)
            } else {
                Toast.makeText(
                    this,
                    "Notification permission needed for background monitor",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            PhoneDiagnosticTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val report by viewModel.report.collectAsStateWithLifecycle()
                    val isLive by viewModel.isLive.collectAsStateWithLifecycle()
                    val lastUpdated by viewModel.lastUpdated.collectAsStateWithLifecycle()
                    val screen by viewModel.screen.collectAsStateWithLifecycle()
                    val networkProbe by viewModel.networkProbeEnabled.collectAsStateWithLifecycle()
                    val bgMonitor by viewModel.backgroundMonitorEnabled.collectAsStateWithLifecycle()
                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
                    val processRam by viewModel.processRam.collectAsStateWithLifecycle()
                    val processRamLoading by viewModel.processRamLoading.collectAsStateWithLifecycle()
                    val appStorage by viewModel.appStorage.collectAsStateWithLifecycle()
                    val appStorageLoading by viewModel.appStorageLoading.collectAsStateWithLifecycle()
                    val hasUsageStats by viewModel.hasUsageStats.collectAsStateWithLifecycle()
                    val logLines by viewModel.logLines.collectAsStateWithLifecycle()
                    val loadTesting by viewModel.loadTesting.collectAsStateWithLifecycle()
                    val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
                    val lastLoadResult by viewModel.lastLoadResult.collectAsStateWithLifecycle()

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                viewModel.refreshUsagePermission()
                                viewModel.refreshLog()
                                if (viewModel.screen.value == AppScreen.STORAGE_DETAIL &&
                                    viewModel.hasUsageStats.value
                                ) {
                                    viewModel.loadAppStorage()
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    Scaffold(
                        bottomBar = {
                            if (screen.isMainTab()) {
                                MainBottomBar(
                                    current = screen,
                                    onSelect = { viewModel.selectMainTab(it) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Surface(modifier = Modifier.padding(innerPadding)) {
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
                                        onShareText = { shareText() },
                                        onShareJson = { shareJson() },
                                        onCopyText = { copyText() },
                                        onOpenSettings = { viewModel.openSettings() },
                                        onOpenRamDetail = { viewModel.openRamDetail() },
                                        onOpenStorageDetail = { viewModel.openStorageDetail() },
                                        onOpenSensors = { viewModel.openSensors() },
                                        onOpenThermals = { viewModel.openThermals() }
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
                                        onOpenThermals = { viewModel.openThermals() }
                                    )
                                }
                                AppScreen.SENSORS -> {
                                    SensorsScreen(
                                        sensors = report?.sensors.orEmpty(),
                                        cameras = report?.cameras.orEmpty(),
                                        isRefreshing = isRefreshing,
                                        onBack = { viewModel.openDashboard() },
                                        onRefresh = { viewModel.refreshNow() }
                                    )
                                }
                                AppScreen.MORE -> {
                                    MoreScreen(
                                        report = report,
                                        versionName = BuildConfig.VERSION_NAME,
                                        onOpenRam = { viewModel.openRamDetail() },
                                        onOpenStorage = { viewModel.openStorageDetail() },
                                        onOpenThermals = { viewModel.openThermals() },
                                        onOpenTools = { viewModel.openTools() },
                                        onOpenSettings = { viewModel.openSettings() },
                                        onOpenAbout = { viewModel.openAbout() }
                                    )
                                }
                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        networkProbeEnabled = networkProbe,
                                        backgroundMonitorEnabled = bgMonitor,
                                        themeMode = themeMode,
                                        onNetworkProbeChange = { viewModel.setNetworkProbeEnabled(it) },
                                        onBackgroundMonitorChange = { enabled ->
                                            if (enabled) {
                                                requestNotificationThenStartMonitor()
                                            } else {
                                                viewModel.setBackgroundMonitorEnabled(false)
                                            }
                                        },
                                        onThemeModeChange = { viewModel.setThemeMode(it) },
                                        onBack = { viewModel.openMore() },
                                        onOpenAbout = { viewModel.openAbout() },
                                        onOpenTools = { viewModel.openTools() }
                                    )
                                }
                                AppScreen.ABOUT -> {
                                    AboutScreen(
                                        versionName = BuildConfig.VERSION_NAME,
                                        onBack = { viewModel.openMore() }
                                    )
                                }
                                AppScreen.RAM_DETAIL -> {
                                    RamDetailScreen(
                                        memory = report?.memory,
                                        entries = processRam,
                                        isLoading = processRamLoading,
                                        onBack = { viewModel.openMore() },
                                        onRefresh = { viewModel.loadProcessRam() }
                                    )
                                }
                                AppScreen.STORAGE_DETAIL -> {
                                    StorageDetailScreen(
                                        storageOverview = report?.storage,
                                        entries = appStorage,
                                        isLoading = appStorageLoading,
                                        hasPermission = hasUsageStats,
                                        onBack = { viewModel.openMore() },
                                        onRefresh = {
                                            viewModel.refreshNow()
                                            viewModel.loadAppStorage()
                                        },
                                        onRequestPermission = { openUsageAccessSettings() },
                                        onOpenAppInfo = { openAppInfo(it) },
                                        onUninstallApp = { uninstallApp(it) }
                                    )
                                }
                                AppScreen.TOOLS -> {
                                    ToolsScreen(
                                        logLines = logLines,
                                        loadTesting = loadTesting,
                                        loadProgress = loadProgress,
                                        lastLoadResult = lastLoadResult,
                                        onBack = { viewModel.openMore() },
                                        onRefreshLog = { viewModel.refreshLog() },
                                        onClearLog = { viewModel.clearLog() },
                                        onShareLog = { shareLog() },
                                        onRunLoadTest = { durationSec ->
                                            viewModel.runLoadTest(durationSec)
                                        }
                                    )
                                }
                                AppScreen.THERMALS -> {
                                    ThermalsScreen(
                                        thermals = report?.thermals.orEmpty(),
                                        isLive = isLive,
                                        isRefreshing = isRefreshing,
                                        onBack = { viewModel.openBattery() },
                                        onRefresh = { viewModel.refreshNow() }
                                    )
                                }
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
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.setBackgroundMonitorEnabled(true)
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAppInfo(packageName: String) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open app info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp(packageName: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: Exception) {
            Toast.makeText(this, "Could not start uninstall", Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentReport() = viewModel.report.value

    private fun shareText() {
        val report = currentReport() ?: return
        val text = ReportExporter.toShareText(report)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Phone Diagnostic Report")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share diagnostic report"
            )
        )
    }

    private fun shareJson() {
        val report = currentReport() ?: return
        val json = ReportExporter.toJson(report)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "Phone Diagnostic Report (JSON)")
                    putExtra(Intent.EXTRA_TEXT, json)
                },
                "Share JSON report"
            )
        )
    }

    private fun shareLog() {
        val lines = viewModel.logLines.value
        if (lines.isEmpty()) {
            Toast.makeText(this, "Log is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val text = lines.joinToString("\n")
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Phone Diagnostic Log")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share diagnostic log"
            )
        )
    }

    private fun copyText() {
        val report = currentReport() ?: return
        val text = ReportExporter.toShareText(report)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone Diagnostic Report", text))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }
}

private data class TabItem(
    val screen: AppScreen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun MainBottomBar(
    current: AppScreen,
    onSelect: (AppScreen) -> Unit
) {
    val tabs = listOf(
        TabItem(AppScreen.DASHBOARD, "Overview", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        TabItem(AppScreen.CPU, "CPU", Icons.Filled.Memory, Icons.Outlined.Memory),
        TabItem(AppScreen.BATTERY, "Battery", Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull),
        TabItem(AppScreen.SENSORS, "Sensors", Icons.Filled.Sensors, Icons.Outlined.Sensors),
        TabItem(AppScreen.MORE, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
    )

    NavigationBar {
        tabs.forEach { tab ->
            val selected = current == tab.screen
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.screen) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) }
            )
        }
    }
}
