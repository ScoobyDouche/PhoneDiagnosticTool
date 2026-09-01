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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonediagnostic.data.FullDeviceReport
import com.phonediagnostic.data.ReportExporter
import com.phonediagnostic.data.ThemeMode
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
import com.phonediagnostic.ui.isMainTab
import com.phonediagnostic.ui.theme.PhoneDiagnosticTheme
import java.io.File

class MainActivity : ComponentActivity() {

    /** Content queued for the document picker, consumed when it returns a target. */
    private var pendingExport: String? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.setBackgroundMonitorEnabled(true)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.toast_notification_permission_needed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val saveTextLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TEXT)) { uri ->
            writePendingExport(uri)
        }

    private val saveJsonLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
            writePendingExport(uri)
        }

    private val viewModel: DeviceViewModel by viewModels()

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
                    val canGoBack by viewModel.canNavigateBack.collectAsStateWithLifecycle()
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
                    val metricHistory by viewModel.metricHistory.collectAsStateWithLifecycle()
                    val networkDetail by viewModel.networkDetail.collectAsStateWithLifecycle()
                    val networkDetailLoading by
                        viewModel.networkDetailLoading.collectAsStateWithLifecycle()
                    val latencyStats by viewModel.latencyStats.collectAsStateWithLifecycle()
                    val latencyRunning by viewModel.latencyRunning.collectAsStateWithLifecycle()
                    val selectedSensor by viewModel.selectedSensor.collectAsStateWithLifecycle()

                    // Back used to leave the app from every detail screen. Intercept
                    // while there is somewhere to return to, and hold it entirely
                    // during a load test so a stray gesture cannot abandon the run.
                    BackHandler(enabled = canGoBack || loadTesting) {
                        if (!loadTesting) {
                            viewModel.navigateBack()
                        }
                    }

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
                                        onBack = { viewModel.navigateBack() },
                                        onOpenAbout = { viewModel.openAbout() },
                                        onOpenTools = { viewModel.openTools() }
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
                                        entries = processRam,
                                        isLoading = processRamLoading,
                                        onBack = { viewModel.navigateBack() },
                                        onRefresh = { viewModel.loadProcessRam() }
                                    )
                                }
                                AppScreen.STORAGE_DETAIL -> {
                                    StorageDetailScreen(
                                        storageOverview = report?.storage,
                                        entries = appStorage,
                                        isLoading = appStorageLoading,
                                        hasPermission = hasUsageStats,
                                        onBack = { viewModel.navigateBack() },
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
                                        onBack = { viewModel.navigateBack() },
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
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_usage_access_settings, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(packageName: String) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_app_info, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, R.string.toast_no_uninstall, Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentReport(): FullDeviceReport? {
        val report = viewModel.report.value
        if (report == null) {
            Toast.makeText(this, R.string.toast_no_report_yet, Toast.LENGTH_SHORT).show()
        }
        return report
    }

    // ------------------------------------------------------------------- sharing

    private fun shareText() {
        val report = currentReport() ?: return
        startChooser(
            text = ReportExporter.toShareText(report),
            mimeType = MIME_TEXT,
            subject = getString(R.string.report_subject),
            title = getString(R.string.chooser_share_report)
        )
    }

    private fun shareJson() {
        val report = currentReport() ?: return
        startChooser(
            text = ReportExporter.toJson(report),
            mimeType = MIME_JSON,
            subject = getString(R.string.report_subject_json),
            title = getString(R.string.chooser_share_json)
        )
    }

    private fun shareLog() {
        val lines = viewModel.logLines.value
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.toast_log_empty, Toast.LENGTH_SHORT).show()
            return
        }
        startChooser(
            text = lines.joinToString("\n"),
            mimeType = MIME_TEXT,
            subject = getString(R.string.log_subject),
            title = getString(R.string.chooser_share_log)
        )
    }

    private fun startChooser(text: String, mimeType: String, subject: String, title: String) {
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    title
                )
            )
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_share_app, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares the report as a real attachment. A full report is large enough that
     * some targets silently truncate or drop `EXTRA_TEXT`, so this route writes a
     * file and hands over a content URI instead.
     */
    private fun shareAsFile() {
        val report = currentReport() ?: return
        val name = ReportExporter.suggestedFileName(report, "txt")
        val uri = try {
            val dir = File(cacheDir, SHARE_DIR).apply { mkdirs() }
            // One file per share keeps this from growing without bound.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, name)
            file.writeText(ReportExporter.toShareText(report))
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(
                    R.string.toast_prepare_file_failed,
                    e.message ?: e.javaClass.simpleName
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = MIME_TEXT
                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject))
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    getString(R.string.chooser_share_file)
                )
            )
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_share_app, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------- saving

    private fun saveText() {
        val report = currentReport() ?: return
        pendingExport = ReportExporter.toShareText(report)
        launchSave(saveTextLauncher, ReportExporter.suggestedFileName(report, "txt"))
    }

    private fun saveJson() {
        val report = currentReport() ?: return
        pendingExport = ReportExporter.toJson(report)
        launchSave(saveJsonLauncher, ReportExporter.suggestedFileName(report, "json"))
    }

    private fun launchSave(
        launcher: androidx.activity.result.ActivityResultLauncher<String>,
        fileName: String
    ) {
        try {
            launcher.launch(fileName)
        } catch (_: Exception) {
            pendingExport = null
            Toast.makeText(this, R.string.toast_no_file_picker, Toast.LENGTH_SHORT).show()
        }
    }

    private fun writePendingExport(uri: Uri?) {
        val content = pendingExport
        pendingExport = null
        if (uri == null || content == null) return
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray())
            } ?: throw IllegalStateException(getString(R.string.error_open_selected_file))
            Toast.makeText(this, R.string.toast_report_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.toast_save_failed, e.message ?: e.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun copyText() {
        val report = currentReport() ?: return
        val text = ReportExporter.toShareText(report)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(this, R.string.toast_clipboard_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.report_subject), text)
        )
        // Android 13+ shows its own copy confirmation; a toast would duplicate it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.toast_report_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val MIME_TEXT = "text/plain"
        const val MIME_JSON = "application/json"
        const val SHARE_DIR = "reports"
    }
}

private data class TabItem(
    val screen: AppScreen,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun MainBottomBar(
    current: AppScreen,
    onSelect: (AppScreen) -> Unit
) {
    val tabs = listOf(
        TabItem(
            AppScreen.DASHBOARD, R.string.tab_overview,
            Icons.Filled.Dashboard, Icons.Outlined.Dashboard
        ),
        TabItem(AppScreen.CPU, R.string.tab_cpu, Icons.Filled.Memory, Icons.Outlined.Memory),
        TabItem(
            AppScreen.BATTERY, R.string.tab_battery,
            Icons.Filled.BatteryFull, Icons.Outlined.BatteryFull
        ),
        TabItem(AppScreen.SENSORS, R.string.tab_sensors, Icons.Filled.Sensors, Icons.Outlined.Sensors),
        TabItem(AppScreen.MORE, R.string.tab_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
    )

    NavigationBar {
        tabs.forEach { tab ->
            val selected = current == tab.screen
            val label = stringResource(tab.label)
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
