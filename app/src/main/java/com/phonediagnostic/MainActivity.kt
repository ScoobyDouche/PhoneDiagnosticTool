package com.phonediagnostic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.phonediagnostic.data.DeviceInfoCollector
import com.phonediagnostic.ui.DashboardScreen
import com.phonediagnostic.ui.theme.PhoneDiagnosticTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PhoneDiagnosticTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val report = remember {
                        DeviceInfoCollector(applicationContext).collect()
                    }
                    DashboardScreen(report = report)
                }
            }
        }
    }
}
