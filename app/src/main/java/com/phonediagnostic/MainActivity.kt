package com.phonediagnostic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonediagnostic.data.ReportExporter
import com.phonediagnostic.ui.DashboardScreen
import com.phonediagnostic.ui.DeviceViewModel
import com.phonediagnostic.ui.theme.PhoneDiagnosticTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PhoneDiagnosticTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val report by viewModel.report.collectAsStateWithLifecycle()
                    val isLive by viewModel.isLive.collectAsStateWithLifecycle()
                    val lastUpdated by viewModel.lastUpdated.collectAsStateWithLifecycle()

                    DashboardScreen(
                        report = report,
                        isLive = isLive,
                        lastUpdated = lastUpdated,
                        onToggleLive = { viewModel.toggleLive() },
                        onRefresh = { viewModel.refreshNow() },
                        onShare = {
                            val current = viewModel.report.value ?: return@DashboardScreen
                            val text = ReportExporter.toShareText(current)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Phone Diagnostic Report")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            startActivity(Intent.createChooser(send, "Share diagnostic report"))
                        }
                    )
                }
            }
        }
    }
}
