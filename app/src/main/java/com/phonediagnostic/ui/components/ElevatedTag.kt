package com.phonediagnostic.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R

/**
 * Small caption marking a reading that was only available through elevated
 * access. [sourceName] is an [com.phonediagnostic.data.elevated.AccessTier]
 * name; a direct read (null, or anything unrecognised) renders nothing.
 */
@Composable
fun ElevatedTag(sourceName: String?, modifier: Modifier = Modifier) {
    val res = when (sourceName) {
        "SHIZUKU" -> R.string.elevated_source_shizuku
        "ROOT" -> R.string.elevated_source_root
        else -> null
    } ?: return
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 4.dp)
    )
}
