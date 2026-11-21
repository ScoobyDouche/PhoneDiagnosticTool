package com.phonediagnostic.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.StabilityKind
import com.phonediagnostic.data.StabilityReport
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import java.text.DateFormat
import java.util.Date

@StringRes
fun stabilityKindLabel(kind: StabilityKind): Int = when (kind) {
    StabilityKind.APP_CRASH -> R.string.stability_app_crash
    StabilityKind.NATIVE_CRASH -> R.string.stability_native_crash
    StabilityKind.ANR -> R.string.stability_anr
    StabilityKind.SYSTEM_RESTART -> R.string.stability_system_restart
    StabilityKind.KERNEL -> R.string.stability_kernel
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StabilityScreen(
    report: StabilityReport?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stability_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.stability_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                report == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        if (isLoading) CircularProgressIndicator()
                    }
                }
                !report.available -> item {
                    InfoCard(title = stringResource(R.string.stability_title)) {
                        Text(
                            text = stringResource(R.string.stability_needs_elevated),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    item {
                        InfoCard(title = stringResource(R.string.stability_last_7_days)) {
                            Column {
                                StabilityKind.entries.forEach { kind ->
                                    InfoRow(stringResource(stabilityKindLabel(kind)), report.count(kind).toString())
                                }
                            }
                        }
                    }
                    val top = report.topOffenders()
                    if (top.isNotEmpty()) {
                        item {
                            InfoCard(title = stringResource(R.string.stability_top)) {
                                Column { top.forEach { (process, n) -> InfoRow(process, n.toString()) } }
                            }
                        }
                    }
                    if (report.events.isNotEmpty()) {
                        item {
                            InfoCard(title = stringResource(R.string.stability_recent)) {
                                Column {
                                    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    report.events.take(40).forEach { e ->
                                        InfoRow(
                                            fmt.format(Date(e.timeMs)),
                                            stringResource(stabilityKindLabel(e.kind)) +
                                                (e.process?.let { " · $it" } ?: "")
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = stringResource(R.string.stability_none),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
