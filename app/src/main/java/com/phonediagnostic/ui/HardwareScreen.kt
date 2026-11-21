package com.phonediagnostic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.phonediagnostic.data.HardwareInfo
import com.phonediagnostic.ui.components.InfoCard
import com.phonediagnostic.ui.components.InfoRow
import java.util.Locale

/** What the phone is built from: screens, radios, biometrics, audio and platform. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareScreen(
    hardware: HardwareInfo?,
    onBack: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hardware_title)) },
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
        if (hardware == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.cpu_collecting))
            }
            return@Scaffold
        }

        val yes = stringResource(R.string.yes)
        val no = stringResource(R.string.no)
        fun yesNo(value: Boolean) = if (value) yes else no

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (hardware.displays.isNotEmpty()) {
                item(key = "screens") {
                    InfoCard(title = stringResource(R.string.hardware_section_screens)) {
                        Column {
                            hardware.displays.forEach { display ->
                                InfoRow(
                                    display.name.ifBlank {
                                        stringResource(R.string.hardware_screen_n, display.id)
                                    },
                                    String.format(
                                        Locale.US,
                                        "%d × %d · %.0f Hz",
                                        display.widthPx,
                                        display.heightPx,
                                        display.refreshRate
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item(key = "features") {
                val (present, absent) = hardware.features.partition { it.present }
                InfoCard(title = stringResource(R.string.hardware_section_features)) {
                    Column {
                        FeatureList(stringResource(R.string.hardware_has), present.map { it.name })
                        FeatureList(stringResource(R.string.hardware_lacks), absent.map { it.name })
                        if (hardware.gnssModel.isNotBlank() || hardware.gnssYear != null) {
                            InfoRow(
                                stringResource(R.string.label_gnss_chip),
                                listOfNotNull(
                                    hardware.gnssModel.takeIf { it.isNotBlank() },
                                    hardware.gnssYear?.toString()
                                ).joinToString(" · ")
                            )
                        }
                        hardware.simSlots?.let {
                            InfoRow(stringResource(R.string.label_sim_slots), it.toString())
                        }
                    }
                }
            }

            item(key = "graphics") {
                InfoCard(title = stringResource(R.string.hardware_section_graphics_audio)) {
                    Column {
                        if (hardware.glEsVersion.isNotBlank()) {
                            InfoRow(stringResource(R.string.label_gles), hardware.glEsVersion)
                        }
                        InfoRow(
                            stringResource(R.string.label_vulkan),
                            hardware.vulkanVersion.ifBlank { stringResource(R.string.hardware_not_supported) }
                        )
                        hardware.audioSampleRate?.let {
                            InfoRow(stringResource(R.string.label_audio_rate), "$it Hz")
                        }
                        hardware.audioFramesPerBuffer?.let {
                            InfoRow(stringResource(R.string.label_audio_buffer), stringResource(R.string.value_frames, it))
                        }
                        InfoRow(stringResource(R.string.label_audio_low_latency), yesNo(hardware.audioLowLatency))
                        InfoRow(stringResource(R.string.label_audio_pro), yesNo(hardware.audioPro))
                        InfoRow(
                            stringResource(R.string.label_vibration),
                            stringResource(
                                when {
                                    !hardware.hasVibrator -> R.string.none
                                    hardware.vibratorAmplitudeControl -> R.string.vibration_variable
                                    else -> R.string.vibration_on_off
                                }
                            )
                        )
                    }
                }
            }

            item(key = "platform") {
                InfoCard(title = stringResource(R.string.hardware_section_platform)) {
                    Column {
                        hardware.firstApiLevel?.let {
                            InfoRow(stringResource(R.string.label_first_api), it.toString())
                        }
                        if (hardware.kernelArch.isNotBlank()) {
                            InfoRow(stringResource(R.string.label_kernel_arch), hardware.kernelArch)
                        }
                        hardware.pageSizeKb?.let {
                            InfoRow(stringResource(R.string.label_page_size), "$it KB")
                        }
                        InfoRow(stringResource(R.string.label_64bit_only), yesNo(hardware.is64BitOnly))
                        InfoRow(stringResource(R.string.label_low_ram), yesNo(hardware.isLowRamDevice))
                        hardware.heapLimitMb?.let { heap ->
                            InfoRow(
                                stringResource(R.string.label_heap_limit),
                                hardware.largeHeapLimitMb?.let { "$heap / $it MB" } ?: "$heap MB"
                            )
                        }
                        if (hardware.runtimeVersion.isNotBlank()) {
                            InfoRow(stringResource(R.string.label_runtime), hardware.runtimeVersion)
                        }
                        hardware.trebleEnabled?.let {
                            InfoRow(stringResource(R.string.label_treble), yesNo(it))
                        }
                        hardware.abUpdates?.let {
                            InfoRow(stringResource(R.string.label_ab_updates), yesNo(it))
                        }
                        hardware.dynamicPartitions?.let {
                            InfoRow(stringResource(R.string.label_dynamic_partitions), yesNo(it))
                        }
                    }
                }
            }

            item(key = "note") {
                Text(
                    text = stringResource(R.string.hardware_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureList(label: String, names: List<String>) {
    if (names.isEmpty()) return
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = names.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
    )
}
