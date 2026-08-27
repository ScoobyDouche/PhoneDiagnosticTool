package com.phonediagnostic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonediagnostic.data.ReportExporter
import com.phonediagnostic.data.ThemeMode
import com.phonediagnostic.ui.AboutScreen
import com.phonediagnostic.ui.AppScreen
import com.phonediagnostic.ui.DashboardScreen
import com.phonediagnostic.ui.DeviceViewModel
import com.phonediagnostic.ui.RamDetailScreen
import com.phonediagnostic.ui.SettingsScreen
import com.phonediagnostic.ui.StorageDetailScreen
import com.phonediagnostic.ui.theme.PhoneDiagnosticTheme

class MainActivity : ComponentActivity() {

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
                    val networkProbe by viewModel.networkProbeEnabled.collectAsStateWithLifecycle()
                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
                    val processRam by viewModel.processRam.collectAsStateWithLifecycle()
                    val processRamLoading by viewModel.processRamLoading.collectAsStateWithLifecycle()
                    val appStorage by viewModel.appStorage.collectAsStateWithLifecycle()
                    val appStorageLoading by viewModel.appStorageLoading.collectAsStateWithLifecycle()
                    val hasUsageStats by viewModel.hasUsageStats.collectAsStateWithLifecycle()

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                viewModel.refreshUsagePermission()
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
                                onOpenStorageDetail = { viewModel.openStorageDetail() }
                            )
                        }
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                networkProbeEnabled = networkProbe,
                                themeMode = themeMode,
                                onNetworkProbeChange = { viewModel.setNetworkProbeEnabled(it) },
                                onThemeModeChange = { viewModel.setThemeMode(it) },
                                onBack = { viewModel.openDashboard() },
                                onOpenAbout = { viewModel.openAbout() }
                            )
                        }
                        AppScreen.ABOUT -> {
                            AboutScreen(
                                versionName = BuildConfig.VERSION_NAME,
                                onBack = { viewModel.openSettings() }
                            )
                        }
                        AppScreen.RAM_DETAIL -> {
                            RamDetailScreen(
                                memory = report?.memory,
                                entries = processRam,
                                isLoading = processRamLoading,
                                onBack = { viewModel.openDashboard() },
                                onRefresh = { viewModel.loadProcessRam() }
                            )
                        }
                        AppScreen.STORAGE_DETAIL -> {
                            StorageDetailScreen(
                                storageOverview = report?.storage,
                                entries = appStorage,
                                isLoading = appStorageLoading,
                                hasPermission = hasUsageStats,
                                onBack = { viewModel.openDashboard() },
                                onRefresh = {
                                    viewModel.refreshNow()
                                    viewModel.loadAppStorage()
                                },
                                onRequestPermission = { openUsageAccessSettings() },
                                onOpenAppInfo = { openAppInfo(it) },
                                onUninstallApp = { uninstallApp(it) }
                            )
                        }
                    }
                }
            }
        }
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

    private fun copyText() {
        val report = currentReport() ?: return
        val text = ReportExporter.toShareText(report)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone Diagnostic Report", text))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }
}
