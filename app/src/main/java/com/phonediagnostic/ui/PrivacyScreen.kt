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
import com.phonediagnostic.data.PrivacyAudit
import com.phonediagnostic.data.SensitiveAccess

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
                    text = if (apps.isEmpty()) stringResource(R.string.privacy_none)
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
                }
            }
        }
    }
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
    SensitiveAccess.NOTIFICATION_LISTENER -> R.string.privacy_cat_notifications
    SensitiveAccess.DEVICE_ADMIN -> R.string.privacy_cat_admin
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
    "API_V23" -> R.string.purpose_shizuku
    else -> R.string.purpose_other
}
