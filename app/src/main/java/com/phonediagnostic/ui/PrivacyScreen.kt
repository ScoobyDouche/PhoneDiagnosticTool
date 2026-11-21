package com.phonediagnostic.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.AppPrivacyEntry
import com.phonediagnostic.data.OwnPermission
import com.phonediagnostic.data.PostureCheck
import com.phonediagnostic.data.PostureResult
import com.phonediagnostic.data.PostureState
import com.phonediagnostic.data.PrivacyAudit
import com.phonediagnostic.data.SensitiveAccess
import com.phonediagnostic.data.SensorUse
import com.phonediagnostic.data.RecentUseEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    audit: PrivacyAudit?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAppInfo: (String) -> Unit
) {
    // System apps hold most grants by design; hiding them keeps the list about
    // what the user actually installed.
    var showSystem by rememberSaveable { mutableStateOf(false) }
    // Collapsed by default so the screen opens as a short summary.
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.privacy_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (audit == null) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        if (isLoading) CircularProgressIndicator()
                        else Text(stringResource(R.string.privacy_unavailable))
                    }
                }
                return@LazyColumn
            }

            item { SummaryCard(audit) }

            if (audit.posture.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.privacy_device_heading)) }
                item { PostureCard(audit.posture) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.privacy_show_system),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
            }

            val visible = audit.apps.filter { showSystem || !it.isSystem }
            val categories = SensitiveAccess.entries
                .sortedByDescending { it.isSpecialAccess }
                .map { access -> access to visible.filter { access in it.access } }

            item { SectionLabel(stringResource(R.string.privacy_special_heading)) }
            val special = categories.filter { it.first.isSpecialAccess }
            items(special, key = { it.first.name }) { (access, apps) ->
                CategoryCard(
                    access = access,
                    apps = apps,
                    unavailable = access.needsElevation && !audit.elevatedAvailable,
                    expanded = access.name in expanded,
                    onToggle = { expanded = expanded.toggle(access.name) },
                    onOpenAppInfo = onOpenAppInfo
                )
            }

            item { SectionLabel(stringResource(R.string.privacy_permissions_heading)) }
            val runtime = categories.filter { !it.first.isSpecialAccess && it.second.isNotEmpty() }
            if (runtime.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.privacy_no_grants),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(runtime, key = { it.first.name }) { (access, apps) ->
                CategoryCard(
                    access = access,
                    apps = apps,
                    expanded = access.name in expanded,
                    onToggle = { expanded = expanded.toggle(access.name) },
                    onOpenAppInfo = onOpenAppInfo
                )
            }

            item { SectionLabel(stringResource(R.string.privacy_recent_heading)) }
            val recent = audit.recentUse.filter { showSystem || !it.isSystem }
            when {
                !audit.elevatedAvailable -> item { HintText(stringResource(R.string.privacy_recent_needs_elevated)) }
                recent.isEmpty() -> item { HintText(stringResource(R.string.privacy_recent_none)) }
                else -> item { RecentUseCard(recent, onOpenAppInfo) }
            }

            item { SectionLabel(stringResource(R.string.privacy_this_app_heading)) }
            item { OwnPermissionsCard(audit.ownPermissions) }
        }
    }
}

private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun CategoryCard(
    access: SensitiveAccess,
    apps: List<AppPrivacyEntry>,
    unavailable: Boolean = false,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenAppInfo: (String) -> Unit
) {
    // Any app with special access deserves a second look, so those counts stand out.
    val warn = access.isSpecialAccess && apps.isNotEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warn) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = apps.isNotEmpty(), onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(accessTitle(access)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (unavailable) stringResource(R.string.privacy_needs_elevated)
                    else if (apps.isEmpty()) stringResource(R.string.privacy_none)
                    else pluralStringResource(R.plurals.privacy_app_count, apps.size, apps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (apps.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }
        }
        if (expanded) {
            apps.forEach { app ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAppInfo(app.packageName) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(text = app.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val tags = listOfNotNull(
                        stringResource(R.string.privacy_tag_sideloaded).takeIf { app.sideloaded },
                        stringResource(R.string.privacy_tag_hidden).takeIf { app.hiddenFromLauncher },
                        stringResource(R.string.privacy_tag_background).takeIf { app.unrestrictedBackground }
                    )
                    if (tags.isNotEmpty()) {
                        Text(
                            text = tags.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (app.worthReview) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Device settings that bear on privacy, each with what it currently is. */
@Composable
private fun PostureCard(results: List<PostureResult>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            results.forEach { result ->
                val (title, good, other) = postureStrings(result.check)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(if (result.state == PostureState.GOOD) good else other),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = when (result.state) {
                            PostureState.GOOD -> MaterialTheme.colorScheme.primary
                            PostureState.ATTENTION -> MaterialTheme.colorScheme.error
                            PostureState.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Text(
                text = stringResource(R.string.privacy_device_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Title, wording for the safer setting, wording for the other one. */
private fun postureStrings(check: PostureCheck): Triple<Int, Int, Int> = when (check) {
    PostureCheck.SCREEN_LOCK ->
        Triple(R.string.posture_screen_lock, R.string.posture_set, R.string.posture_not_set)
    PostureCheck.ENCRYPTION ->
        Triple(R.string.posture_encryption, R.string.radio_on, R.string.radio_off)
    PostureCheck.BOOTLOADER ->
        Triple(R.string.posture_bootloader, R.string.bootloader_locked, R.string.bootloader_unlocked)
    PostureCheck.PATCH_AGE ->
        Triple(R.string.posture_patch, R.string.posture_recent, R.string.posture_out_of_date)
    PostureCheck.USB_DEBUGGING ->
        Triple(R.string.posture_usb_debugging, R.string.radio_off, R.string.radio_on)
    PostureCheck.WIRELESS_DEBUGGING ->
        Triple(R.string.posture_wireless_debugging, R.string.radio_off, R.string.radio_on)
    PostureCheck.DEVELOPER_OPTIONS ->
        Triple(R.string.posture_developer_options, R.string.radio_off, R.string.radio_on)
    PostureCheck.PRIVATE_DNS ->
        Triple(R.string.posture_private_dns, R.string.posture_in_use, R.string.posture_not_in_use)
    PostureCheck.VPN ->
        Triple(R.string.posture_vpn, R.string.posture_in_use, R.string.posture_not_in_use)
    PostureCheck.LOCATION ->
        Triple(R.string.posture_location, R.string.radio_off, R.string.radio_on)
    PostureCheck.BLUETOOTH ->
        Triple(R.string.label_bluetooth, R.string.radio_off, R.string.radio_on)
    PostureCheck.NFC ->
        Triple(R.string.label_nfc, R.string.radio_off, R.string.radio_on)
    PostureCheck.SENSOR_TOGGLES ->
        Triple(R.string.posture_sensor_toggles, R.string.posture_available, R.string.posture_not_available)
    PostureCheck.USER_CA_CERTS ->
        Triple(R.string.posture_user_ca, R.string.posture_none, R.string.posture_installed)
    PostureCheck.DEVICE_MANAGED ->
        Triple(R.string.posture_device_managed, R.string.posture_no, R.string.posture_managed)
    PostureCheck.WORK_PROFILE ->
        Triple(R.string.posture_work_profile, R.string.posture_none, R.string.posture_present)
    PostureCheck.WIFI_SECURITY ->
        Triple(R.string.posture_wifi_security, R.string.posture_encrypted, R.string.posture_open)
}

@Composable
private fun OwnPermissionsCard(permissions: List<OwnPermission>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.privacy_this_app_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            permissions.forEach { permission ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = permission.name.substringAfterLast('.'),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(
                                if (permission.granted) R.string.privacy_granted else R.string.privacy_not_granted
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (permission.granted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(ownPermissionPurpose(permission.name)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@StringRes
private fun accessTitle(access: SensitiveAccess): Int = when (access) {
    SensitiveAccess.CAMERA -> R.string.privacy_cat_camera
    SensitiveAccess.MICROPHONE -> R.string.privacy_cat_microphone
    SensitiveAccess.LOCATION -> R.string.privacy_cat_location
    SensitiveAccess.BACKGROUND_LOCATION -> R.string.privacy_cat_background_location
    SensitiveAccess.CONTACTS -> R.string.privacy_cat_contacts
    SensitiveAccess.SMS -> R.string.privacy_cat_sms
    SensitiveAccess.CALL_LOG -> R.string.privacy_cat_call_log
    SensitiveAccess.PHONE -> R.string.privacy_cat_phone
    SensitiveAccess.CALENDAR -> R.string.privacy_cat_calendar
    SensitiveAccess.BODY_SENSORS -> R.string.privacy_cat_body
    SensitiveAccess.NEARBY_DEVICES -> R.string.privacy_cat_nearby
    SensitiveAccess.PHOTOS_MEDIA -> R.string.privacy_cat_media
    SensitiveAccess.ACCESSIBILITY -> R.string.privacy_cat_accessibility
    SensitiveAccess.KEYBOARD -> R.string.privacy_cat_keyboard
    SensitiveAccess.NOTIFICATION_LISTENER -> R.string.privacy_cat_notifications
    SensitiveAccess.DEVICE_ADMIN -> R.string.privacy_cat_admin
    SensitiveAccess.OVERLAY -> R.string.privacy_cat_overlay
    SensitiveAccess.INSTALL_APPS -> R.string.privacy_cat_install_apps
    SensitiveAccess.USAGE_ACCESS -> R.string.privacy_cat_usage_access
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/**
 * One-glance verdict: how many things on this screen are worth a look. It
 * counts device checks in the red and apps that fit the stalkerware pattern
 * (special access held by a sideloaded or icon-less app). Not a verdict on any
 * single app — just where to start reading.
 */
@Composable
private fun SummaryCard(audit: PrivacyAudit) {
    val flagged = audit.appsWorthReview
    val warnings = audit.postureWarnings
    val total = flagged.size + warnings.size
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (total > 0) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (total == 0) stringResource(R.string.privacy_summary_clear)
                else pluralStringResource(R.plurals.privacy_summary_items, total, total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (warnings.isNotEmpty()) {
                Text(
                    text = pluralStringResource(R.plurals.privacy_summary_settings, warnings.size, warnings.size),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (flagged.isNotEmpty()) {
                Text(
                    text = pluralStringResource(R.plurals.privacy_summary_apps, flagged.size, flagged.size) +
                        ": " + flagged.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!audit.elevatedAvailable) {
                Text(
                    text = stringResource(R.string.privacy_summary_elevated_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Apps that used camera, microphone or location in the last week, most recent first. */
@Composable
private fun RecentUseCard(entries: List<RecentUseEntry>, onOpenAppInfo: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAppInfo(entry.packageName) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = entry.label, style = MaterialTheme.typography.bodyMedium)
                val parts = SensorUse.entries.mapNotNull { use ->
                    val ago = entry.lastUsedAgoMs[use] ?: return@mapNotNull null
                    stringResource(sensorUseLabel(use)) + " " + formatAgo(ago)
                }
                Text(
                    text = parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@StringRes
private fun sensorUseLabel(use: SensorUse): Int = when (use) {
    SensorUse.CAMERA -> R.string.privacy_cat_camera
    SensorUse.MICROPHONE -> R.string.privacy_cat_microphone
    SensorUse.LOCATION -> R.string.privacy_cat_location
}

@Composable
private fun formatAgo(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.ago_just_now)
        minutes < 60 -> stringResource(R.string.ago_minutes, minutes)
        minutes < 48 * 60 -> stringResource(R.string.ago_hours, minutes / 60)
        else -> stringResource(R.string.ago_days, minutes / (24 * 60))
    }
}

@StringRes
private fun ownPermissionPurpose(name: String): Int = when (name.substringAfterLast('.')) {
    "INTERNET" -> R.string.purpose_internet
    "ACCESS_NETWORK_STATE" -> R.string.purpose_network_state
    "ACCESS_WIFI_STATE" -> R.string.purpose_wifi_state
    "VIBRATE" -> R.string.purpose_vibrate
    "PACKAGE_USAGE_STATS" -> R.string.purpose_usage_stats
    "QUERY_ALL_PACKAGES" -> R.string.purpose_query_packages
    "REQUEST_DELETE_PACKAGES" -> R.string.purpose_delete_packages
    "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_SPECIAL_USE" -> R.string.purpose_foreground_service
    "POST_NOTIFICATIONS" -> R.string.purpose_notifications
    "RECEIVE_BOOT_COMPLETED" -> R.string.purpose_boot
    "API_V23" -> R.string.purpose_shizuku
    else -> R.string.purpose_other
}
