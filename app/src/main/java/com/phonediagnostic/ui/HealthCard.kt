package com.phonediagnostic.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.HealthBaseline
import com.phonediagnostic.data.HealthFactor
import com.phonediagnostic.data.HealthScore
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@StringRes
private fun factorLabel(f: HealthFactor): Int = when (f) {
    HealthFactor.BATTERY_WORN -> R.string.health_battery_worn
    HealthFactor.BATTERY_VERY_WORN -> R.string.health_battery_very_worn
    HealthFactor.BATTERY_FAULT -> R.string.health_battery_fault
    HealthFactor.STORAGE_NEARLY_FULL -> R.string.health_storage_nearly_full
    HealthFactor.STORAGE_FULL -> R.string.health_storage_full
    HealthFactor.FLASH_WEAR_HIGH -> R.string.health_flash_wear
    HealthFactor.FLASH_SPARE_WARNING -> R.string.health_flash_spare_warning
    HealthFactor.FLASH_SPARE_URGENT -> R.string.health_flash_spare_urgent
    HealthFactor.THERMAL_MODERATE -> R.string.health_thermal_moderate
    HealthFactor.THERMAL_SEVERE -> R.string.health_thermal_severe
    HealthFactor.PATCH_OLD -> R.string.health_patch_old
    HealthFactor.PATCH_VERY_OLD -> R.string.health_patch_very_old
    HealthFactor.CHECKUP_FAILURES -> R.string.health_checkup_failures
    HealthFactor.BATTERY_UNKNOWN -> R.string.health_battery_unknown
}

/**
 * Overall health with every deduction spelled out, plus the comparison with a
 * saved baseline when there is one.
 */
@Composable
fun HealthCard(
    health: HealthScore,
    baseline: HealthBaseline?,
    currentBatteryHealth: Int?,
    currentCycles: Int?,
    currentStorageUsedGb: Double,
    currentLoadScore: Long?,
    onSaveBaseline: () -> Unit,
    onClearBaseline: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val container = when (health.grade) {
        HealthScore.Grade.GOOD -> MaterialTheme.colorScheme.primaryContainer
        HealthScore.Grade.FAIR -> MaterialTheme.colorScheme.secondaryContainer
        HealthScore.Grade.POOR -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.health_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = stringResource(
                            R.string.health_score,
                            health.score,
                            stringResource(
                                when (health.grade) {
                                    HealthScore.Grade.GOOD -> R.string.health_grade_good
                                    HealthScore.Grade.FAIR -> R.string.health_grade_fair
                                    HealthScore.Grade.POOR -> R.string.health_grade_poor
                                }
                            )
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (expanded) {
                if (health.deductions.isEmpty()) {
                    Text(stringResource(R.string.health_no_issues), style = MaterialTheme.typography.bodySmall)
                }
                health.deductions.forEach { d ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(factorLabel(d.factor)) + if (d.detail.isNotEmpty()) " (${d.detail})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (d.points > 0) Text("−${d.points}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    stringResource(R.string.health_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (baseline == null) {
                    Text(stringResource(R.string.baseline_none), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onSaveBaseline) { Text(stringResource(R.string.baseline_save)) }
                } else {
                    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(baseline.savedAtMs))
                    Text(
                        stringResource(R.string.baseline_since, date),
                        style = MaterialTheme.typography.labelLarge
                    )
                    DiffRow(stringResource(R.string.baseline_score), "${baseline.score}", "${health.score}")
                    if (baseline.batteryHealthPercent != null || currentBatteryHealth != null) {
                        DiffRow(
                            stringResource(R.string.baseline_battery),
                            baseline.batteryHealthPercent?.let { "$it%" } ?: "—",
                            currentBatteryHealth?.let { "$it%" } ?: "—"
                        )
                    }
                    if (baseline.cycleCount != null || currentCycles != null) {
                        DiffRow(
                            stringResource(R.string.label_cycle_count),
                            baseline.cycleCount?.toString() ?: "—",
                            currentCycles?.toString() ?: "—"
                        )
                    }
                    DiffRow(
                        stringResource(R.string.baseline_storage),
                        String.format(Locale.US, "%.1f GB", baseline.storageUsedGb),
                        String.format(Locale.US, "%.1f GB", currentStorageUsedGb)
                    )
                    if (baseline.loadScore != null || currentLoadScore != null) {
                        DiffRow(
                            stringResource(R.string.baseline_load),
                            baseline.loadScore?.let { "${it}k" } ?: "—",
                            currentLoadScore?.let { "${it}k" } ?: "—"
                        )
                    }
                    Row {
                        TextButton(onClick = onSaveBaseline) { Text(stringResource(R.string.baseline_replace)) }
                        TextButton(onClick = onClearBaseline) { Text(stringResource(R.string.baseline_clear)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffRow(label: String, before: String, now: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text("$before → $now", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
