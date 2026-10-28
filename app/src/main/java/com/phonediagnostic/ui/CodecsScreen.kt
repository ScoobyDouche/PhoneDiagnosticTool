package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonediagnostic.R
import com.phonediagnostic.data.CodecSupport
import com.phonediagnostic.ui.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecsScreen(
    codecs: List<CodecSupport>,
    onBack: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.codecs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
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
                    text = stringResource(R.string.codecs_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (codecs.isEmpty()) {
                item { Text(stringResource(R.string.codecs_empty)) }
            }
            listOf(true to R.string.codecs_video, false to R.string.codecs_audio).forEach { (video, title) ->
                val group = codecs.filter { it.isVideo == video }
                if (group.isNotEmpty()) {
                    item(key = "header_$video") {
                        Text(
                            text = stringResource(title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }
                    items(group, key = { it.mimeType }) { codec -> CodecCard(codec) }
                }
            }
        }
    }
}

@Composable
private fun CodecCard(codec: CodecSupport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = codec.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            InfoRow(stringResource(R.string.codecs_decode), support(codec.hardwareDecode, codec.softwareDecode))
            InfoRow(stringResource(R.string.codecs_encode), support(codec.hardwareEncode, codec.softwareEncode))
            if (codec.maxDecodeSize.isNotBlank()) {
                Text(
                    text = codec.maxDecodeFps?.let {
                        stringResource(R.string.codecs_max_fps, codec.maxDecodeSize, it)
                    } ?: stringResource(R.string.codecs_max, codec.maxDecodeSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Hardware wins when both exist; that is the one playback will use. */
@Composable
private fun support(hardware: Boolean, software: Boolean): String = stringResource(
    when {
        hardware -> R.string.codecs_hardware
        software -> R.string.codecs_software
        else -> R.string.codecs_none
    }
)
