package com.phonediagnostic.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    /**
     * A filename that sorts by capture time and identifies the device, e.g.
     * `phone-diagnostic-Pixel_7-20260831-142530.txt`.
     */
    fun suggestedFileName(report: FullDeviceReport, extension: String): String {
        val model = report.overview.model
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "device" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "phone-diagnostic-$model-$stamp.$extension"
    }

    /**
     * Copy of [report] without the fields that pin down one exact device build:
     * fingerprint, bootloader and baseband versions, build host, and the kernel
     * build line (which names the builder). Model, Android version and patch
     * level stay, since a report is useless without them.
     */
    fun redact(report: FullDeviceReport): FullDeviceReport = report.copy(
        overview = report.overview.copy(
            fingerprint = "",
            bootloader = "",
            host = "",
            radioVersion = "",
            kernelVersion = kernelRelease(report.overview.kernelVersion)
        ),
        // App-private paths carry the Android user number and help nobody else.
        storage = report.storage.copy(dataDirectory = "", filesDirectory = "", cacheDirectory = "")
    )

    /**
     * Applied to every export, redacted or not: the router's address is shown
     * on screen when the latency probe targets it, but PRIVACY.md promises
     * reports never contain an IP address.
     */
    internal fun withoutAddresses(network: NetworkInfo): NetworkInfo =
        if (network.latencyTarget.startsWith("Router")) {
            network.copy(latencyTarget = "Router (address withheld)")
        } else {
            network
        }

    /** "Linux version 5.15.104-android14 (builder@host) ..." → "5.15.104-android14". */
    internal fun kernelRelease(procVersion: String): String =
        Regex("Linux version (\\S+)").find(procVersion)?.groupValues?.get(1)
            ?: procVersion.substringBefore(' ')

    fun toShareText(report: FullDeviceReport, redact: Boolean = false): String {
        val shown = (if (redact) redact(report) else report).let {
            it.copy(network = withoutAddresses(it.network))
        }
        return buildString {
            appendLine("Phone Diagnostic Report")
            appendLine("========================")
            if (redact) {
                appendLine("Device identifiers removed (fingerprint, bootloader, baseband, build host, app paths).")
            }
            append(reportBody(shown))
        }
    }

    private fun reportBody(report: FullDeviceReport): String {
        val o = report.overview
        val c = report.cpu
        val g = report.gpu
        val b = report.battery
        val m = report.memory
        val n = report.network
        val s = report.storage
        val d = report.display

        return buildString {
            appendLine()
            appendLine("DEVICE")
            appendLine("  Manufacturer: ${o.manufacturer}")
            appendLine("  Model: ${o.model}")
            appendLine("  Brand: ${o.brand}")
            appendLine("  Android: ${o.androidVersion} (API ${o.apiLevel})")
            appendLine("  Security Patch: ${o.securityPatch}")
            appendLine("  Build ID: ${o.buildId}")
            if (o.board.isNotBlank()) appendLine("  Board: ${o.board}")
            if (o.bootloader.isNotBlank()) appendLine("  Bootloader: ${o.bootloader}")
            if (o.hardware.isNotBlank()) appendLine("  Hardware: ${o.hardware}")
            if (o.type.isNotBlank()) appendLine("  Build type: ${o.type}")
            appendLine("  Uptime: ${o.uptime}")
            if (o.kernelVersion.isNotBlank()) appendLine("  Kernel: ${o.kernelVersion}")
            if (o.radioVersion.isNotBlank()) appendLine("  Radio: ${o.radioVersion}")
            if (o.fingerprint.isNotBlank()) appendLine("  Fingerprint: ${o.fingerprint}")
            appendLine()
            appendLine("CPU / SoC")
            appendLine("  Cores: ${c.cores}")
            appendLine("  Architecture: ${c.architecture}")
            appendLine("  Board / Platform: ${c.boardPlatform}")
            appendLine("  Hardware: ${c.hardware}")
            appendLine("  Processor: ${c.processor}")
            appendLine("  ABIs: ${c.supportedAbis.joinToString(", ")}")
            if (c.minFreqMhz != null || c.maxFreqMhz != null) {
                appendLine("  Freq range: ${c.minFreqMhz ?: "?"} – ${c.maxFreqMhz ?: "?"} MHz")
            }
            if (c.currentFreqMhz.isNotEmpty()) {
                appendLine("  Current (cores): ${c.currentFreqMhz.joinToString(", ")} MHz")
            }
            if (c.governors.isNotEmpty()) {
                appendLine("  Governors: ${c.governors.joinToString(", ")}")
            }
            if (c.availableFreqMhz.isNotEmpty()) {
                appendLine("  Available freqs: ${c.availableFreqMhz.joinToString(", ")} MHz")
            }
            if (c.onlineCores.isNotEmpty()) {
                appendLine("  Online cores: ${c.onlineCores.joinToString(", ")}")
            }
            if (c.topologyHint.isNotBlank()) {
                appendLine("  Topology: ${c.topologyHint}")
            }
            if (c.socModel.isNotBlank()) {
                appendLine("  SoC: ${listOf(c.socManufacturer, c.socModel).filter { it.isNotBlank() }.joinToString(" ")}")
            }
            if (c.coreNames.isNotEmpty()) appendLine("  Core types: ${c.coreNames.joinToString(" + ")}")
            if (c.features.isNotEmpty()) appendLine("  Features: ${c.features.joinToString(" ")}")
            appendLine()
            appendLine("GPU")
            appendLine("  Renderer: ${g.renderer}")
            appendLine("  Vendor: ${g.vendor}")
            appendLine("  Version: ${g.version}")
            appendLine()
            appendLine("BATTERY")
            appendLine("  Level: ${b.level}%")
            appendLine("  Status: ${b.status}")
            appendLine("  Health: ${b.health}")
            appendLine("  Temperature: ${String.format(Locale.US, "%.1f", b.temperature)} °C")
            appendLine("  Voltage: ${b.voltage} mV")
            appendLine("  Current (now): ${b.currentNowMa?.let { "$it mA" } ?: "Unavailable"}")
            appendLine("  Current (avg): ${b.currentAvgMa?.let { "$it mA" } ?: "Unavailable"}")
            if (b.capacityMah != null) appendLine("  Design capacity: ${b.capacityMah} mAh")
            if (b.chargeCounterUah != null) {
                appendLine("  Charge counter: ${String.format(Locale.US, "%.0f", b.chargeCounterUah / 1000.0)} mAh")
            }
            b.capacityHealthPercent?.let { appendLine("  Capacity health: $it% of design") }
            b.cycleCount?.let { appendLine("  Charge cycles: $it") }
            appendLine("  Technology: ${b.technology}")
            appendLine("  Power Source: ${b.powerSource}")
            b.chargerMaxWatts?.let { appendLine("  Charger offers: ${String.format(Locale.US, "%.1f", it)} W") }
            b.chargeTimeRemainingMs?.let { appendLine("  Time to full: ${it / 60_000} min") }
            b.energyMwh?.let { appendLine("  Energy remaining: $it mWh") }
            appendLine("  Battery Saver: ${if (b.powerSaveMode) "On" else "Off"}")
            appendLine()
            appendLine("MEMORY (RAM)")
            appendLine("  Total: ${m.totalRamMb} MB")
            appendLine("  Available: ${m.availableRamMb} MB")
            appendLine("  Used: ${m.usedRamMb} MB (${m.usagePercent}%)")
            if (m.cachedMb != null) appendLine("  Cached: ${m.cachedMb} MB")
            if (m.buffersMb != null) appendLine("  Buffers: ${m.buffersMb} MB")
            if (m.swapTotalMb != null) {
                appendLine("  Swap: ${m.swapFreeMb ?: "?"} free / ${m.swapTotalMb} total MB")
            }
            if (m.zramMb != null) appendLine("  ZRAM in RAM: ${m.zramMb} MB")
            if (m.kernelActiveMb != null) appendLine("  Kernel Active: ${m.kernelActiveMb} MB")
            appendLine()
            appendLine("NETWORK")
            appendLine("  Connection: ${if (n.isConnected) "Connected" else "Disconnected"}")
            appendLine("  Type: ${n.networkType}")
            appendLine("  Latency: ${n.latencyMs?.let { "$it ms" } ?: "—"}")
            appendLine("  Target: ${n.latencyTarget}")
            appendLine("  Status: ${n.latencyStatus}")
            if (n.downstreamMbps != null || n.upstreamMbps != null) {
                appendLine("  Link bandwidth: ${n.downstreamMbps ?: "?"} ↓ / ${n.upstreamMbps ?: "?"} ↑ Mbps")
            }
            appendLine("  Validated: ${n.validated}")
            appendLine("  Metered: ${n.metered}")
            appendLine()
            appendLine("STORAGE")
            appendLine("  Internal total: ${String.format(Locale.US, "%.2f", s.totalInternalGb)} GB")
            appendLine("  Internal free: ${String.format(Locale.US, "%.2f", s.freeInternalGb)} GB")
            appendLine("  Internal used: ${String.format(Locale.US, "%.2f", s.usedInternalGb)} GB (${s.usagePercent}%)")
            if (s.dataDirectory.isNotBlank()) appendLine("  Data path: ${s.dataDirectory}")
            if (s.filesDirectory.isNotBlank()) appendLine("  Files path: ${s.filesDirectory}")
            if (s.cacheDirectory.isNotBlank()) appendLine("  Cache path: ${s.cacheDirectory}")
            appendLine("  External state: ${s.externalStorageState}")
            appendLine("  Emulated external: ${s.emulatedExternal}")
            if (s.storageType.isNotBlank()) appendLine("  Flash type: ${s.storageType}")
            s.lifeUsed?.let { appendLine("  Flash wear: $it") }
            s.preEol?.let { appendLine("  Spare blocks: $it") }
            s.volumes.forEachIndexed { i, v ->
                appendLine("  Volume ${i + 1}: ${v.name}")
                appendLine("    Path: ${v.path}")
                appendLine("    ${v.description}")
                appendLine("    Total: ${String.format(Locale.US, "%.2f", v.totalGb)} GB")
                appendLine("    Free: ${String.format(Locale.US, "%.2f", v.freeGb)} GB")
                appendLine("    Used: ${String.format(Locale.US, "%.2f", v.usedGb)} GB (${v.usagePercent}%)")
                appendLine("    State: ${v.state}")
            }
            appendLine()
            appendLine("DISPLAY")
            appendLine("  Resolution: ${d.widthPx} × ${d.heightPx}")
            appendLine("  Density: ${d.densityDpi} dpi")
            appendLine("  Refresh Rate: ${String.format(Locale.US, "%.1f", d.refreshRate)} Hz")
            appendLine("  Approx. Size: ${String.format(Locale.US, "%.2f", d.screenSizeInches)}\"")
            if (d.supportedRefreshRatesHz.size > 1) {
                appendLine("  Supported rates: ${d.supportedRefreshRatesHz.joinToString(", ") { String.format(Locale.US, "%.0f", it) }} Hz")
            }
            appendLine("  HDR: ${d.hdrFormats.joinToString(", ").ifBlank { "None" }}")
            d.hdrMaxNits?.let { appendLine("  HDR peak: ${it.toInt()} nits") }
            if (d.modes.size > 1) appendLine("  Modes: ${d.modes.joinToString("; ")}")
            appendLine("  Wide colour gamut: ${if (d.wideColorGamut) "Yes" else "No"}")
            if (d.orientation.isNotBlank()) appendLine("  Orientation: ${d.orientation}")
            appendLine()
            appendLine("CONNECTIVITY & AUDIO")
            appendLine("  NFC: ${radioState(report.nfc.present, report.nfc.enabled)}")
            appendLine("  Bluetooth: ${radioState(report.bluetooth.present, report.bluetooth.enabled)}")
            appendLine("  Audio out: ${report.audio.outputDevices.joinToString(", ").ifBlank { "—" }}")
            appendLine("  Audio in: ${report.audio.inputDevices.joinToString(", ").ifBlank { "—" }}")
            appendLine()
            val sec = report.security
            appendLine("SECURITY")
            appendLine("  Verified boot: ${sec.verifiedBootState.ifBlank { "Unavailable" }}")
            val bootloader = when (sec.bootloaderLocked) {
                true -> "Locked"
                false -> "Unlocked"
                null -> "Unavailable"
            }
            appendLine("  Bootloader lock: $bootloader")
            appendLine("  Encryption: ${sec.encryption.ifBlank { "Unavailable" }}")
            if (sec.selinux.isNotBlank()) appendLine("  SELinux: ${sec.selinux}")
            if (sec.buildTags.isNotBlank()) appendLine("  Build tags: ${sec.buildTags}")
            sec.isDebuggableBuild?.let { appendLine("  Debuggable build: ${if (it) "Yes" else "No"}") }
            sec.patchAgeDays?.let { appendLine("  Patch age: $it days") }
            sec.strongBox?.let { appendLine("  StrongBox keystore: ${if (it) "Yes" else "No"}") }
            sec.hardwareKeystore?.let { appendLine("  Hardware keystore: ${if (it) "Yes" else "No"}") }
            val hwVideo = report.mediaCodecs.filter { it.isVideo && it.hardwareDecode }
            if (hwVideo.isNotEmpty()) {
                appendLine()
                appendLine("MEDIA CODECS")
                appendLine("  Hardware video decode: ${hwVideo.joinToString(", ") { it.name }}")
                val hwEncode = report.mediaCodecs.filter { it.isVideo && it.hardwareEncode }
                if (hwEncode.isNotEmpty()) {
                    appendLine("  Hardware video encode: ${hwEncode.joinToString(", ") { it.name }}")
                }
            }
            appendLine()
            val sys = report.system
            appendLine("SYSTEM")
            appendLine("  Since boot: ${sys.sinceBootMs / 60_000} min, CPU awake ${sys.awakeMs / 60_000} min")
            sys.deepSleepPercent?.let { appendLine("  Deep sleep: $it%") }
            sys.lastWakeReason?.let { appendLine("  Last woken by: $it") }
            sys.topWakeSources.forEach { appendLine("  Wake source: ${it.name} (${it.activeMs / 1000} s)") }
            appendLine()
            if (report.thermalState.status.isNotBlank()) {
                appendLine("THERMAL STATUS: ${report.thermalState.status}")
                report.thermalState.headroom?.let {
                    appendLine("  Headroom: ${(it * 100).toInt()}% of throttling limit (10 s forecast)")
                }
                appendLine()
            }
            if (report.thermals.isNotEmpty()) {
                appendLine("THERMALS (${report.thermals.size})")
                report.thermals.forEach { z ->
                    val raw = z.type.ifBlank { z.name }
                    val label = if (z.typeLabel.isBlank() || z.typeLabel == raw) raw else "${z.typeLabel} ($raw)"
                    appendLine("  $label: ${String.format(Locale.US, "%.1f", z.tempC)} °C")
                }
                appendLine()
            }
            appendHardware(report.hardware)
            appendLine("SENSORS (${report.sensors.size})")
            val missing = SensorCatalog.coverage(report.sensors.map { it.typeId })
                .filter { !it.second }.map { it.first }
            if (missing.isNotEmpty() && report.sensors.isNotEmpty()) {
                appendLine("  Not present: ${missing.joinToString(", ")}")
            }
            report.sensors.take(40).forEach { sens ->
                val wake = if (sens.isWakeUp) ", wake-up" else ""
                appendLine("  ${sens.type}: ${sens.name} (${sens.vendor}$wake)")
                if (sens.liveValues.isNotBlank()) {
                    appendLine("    Live: ${sens.liveValues}")
                }
            }
            if (report.sensors.size > 40) {
                appendLine("  … +${report.sensors.size - 40} more")
            }
            appendLine()
            appendLine("CAMERAS (${report.cameras.size})")
            report.cameras.forEach { cam ->
                appendLine("  ${cam.id} ${cam.facing} · ${cam.pixelArraySize} · ${cam.hardwareLevel}")
                appendLine("    Focal: ${cam.focalLengths} · Aperture: ${cam.aperture}")
                if (cam.capabilities.isNotBlank()) appendLine("    Caps: ${cam.capabilities}")
                if (cam.isoRange.isNotBlank()) appendLine("    ISO: ${cam.isoRange}")
                if (cam.exposureRange.isNotBlank()) appendLine("    Exposure: ${cam.exposureRange}")
                if (cam.physicalIds.isNotBlank()) appendLine("    Physical IDs: ${cam.physicalIds}")
                cam.hasFlash?.let { appendLine("    Flash: ${if (it) "Yes" else "No"}") }
            }
            appendLine()
            appendLine("Generated by Phone Diagnostic Tool")
            appendLine("Privacy: on-device diagnostics; optional latency probe only.")
        }
    }

    private fun StringBuilder.appendHardware(h: HardwareInfo) {
        if (h == HardwareInfo()) return
        fun yesNo(value: Boolean) = if (value) "Yes" else "No"
        appendLine("HARDWARE")
        h.displays.forEach { d ->
            val role = if (d.isDefault) "main" else "additional"
            appendLine(
                "  Screen ${d.id} ($role): ${d.widthPx} × ${d.heightPx} @ " +
                    "${String.format(Locale.US, "%.0f", d.refreshRate)} Hz"
            )
        }
        if (h.glEsVersion.isNotBlank()) appendLine("  OpenGL ES: ${h.glEsVersion}")
        appendLine("  Vulkan: ${h.vulkanVersion.ifBlank { "Not supported" }}")
        val vibration = when {
            !h.hasVibrator -> "None"
            h.vibratorAmplitudeControl -> "Yes, variable strength"
            else -> "Yes, on/off only"
        }
        appendLine("  Vibration motor: $vibration")
        h.audioSampleRate?.let { appendLine("  Audio output: $it Hz, ${h.audioFramesPerBuffer ?: "?"} frames/buffer") }
        appendLine("  Low-latency audio: ${yesNo(h.audioLowLatency)} · Pro audio: ${yesNo(h.audioPro)}")
        if (h.gnssModel.isNotBlank() || h.gnssYear != null) {
            appendLine("  GNSS chip: ${listOfNotNull(h.gnssModel.takeIf { it.isNotBlank() }, h.gnssYear?.toString()).joinToString(", ")}")
        }
        h.simSlots?.let { appendLine("  SIM slots: $it") }
        h.pageSizeKb?.let { appendLine("  Memory page size: $it KB") }
        if (h.kernelArch.isNotBlank()) appendLine("  Kernel architecture: ${h.kernelArch}")
        appendLine("  64-bit only: ${yesNo(h.is64BitOnly)} · Low-RAM device: ${yesNo(h.isLowRamDevice)}")
        h.heapLimitMb?.let { appendLine("  App heap limit: $it MB (large: ${h.largeHeapLimitMb ?: "?"} MB)") }
        if (h.runtimeVersion.isNotBlank()) appendLine("  ART version: ${h.runtimeVersion}")
        h.firstApiLevel?.let { appendLine("  Shipped with API: $it") }
        h.trebleEnabled?.let { appendLine("  Project Treble: ${yesNo(it)}") }
        h.abUpdates?.let { appendLine("  Seamless (A/B) updates: ${yesNo(it)}") }
        h.dynamicPartitions?.let { appendLine("  Dynamic partitions: ${yesNo(it)}") }
        val (present, absent) = h.features.partition { it.present }
        if (present.isNotEmpty()) appendLine("  Has: ${present.joinToString(", ") { it.name }}")
        if (absent.isNotEmpty()) appendLine("  Lacks: ${absent.joinToString(", ") { it.name }}")
        appendLine()
    }

    fun toJson(report: FullDeviceReport, redact: Boolean = false): String {
        @Suppress("NAME_SHADOWING")
        val report = (if (redact) redact(report) else report).let {
            it.copy(network = withoutAddresses(it.network))
        }
        val o = report.overview
        val c = report.cpu
        val g = report.gpu
        val b = report.battery
        val m = report.memory
        val n = report.network
        val s = report.storage
        val d = report.display

        val root = JSONObject()
        root.put("app", "Phone Diagnostic Tool")
        root.put("redacted", redact)
        root.put("overview", JSONObject().apply {
            put("manufacturer", o.manufacturer)
            put("model", o.model)
            put("brand", o.brand)
            put("androidVersion", o.androidVersion)
            put("apiLevel", o.apiLevel)
            put("securityPatch", o.securityPatch)
            put("buildId", o.buildId)
            put("uptime", o.uptime)
            put("fingerprint", o.fingerprint)
            put("board", o.board)
            put("bootloader", o.bootloader)
            put("hardware", o.hardware)
            put("host", o.host)
            put("tags", o.tags)
            put("type", o.type)
            put("kernelVersion", o.kernelVersion)
            put("radioVersion", o.radioVersion)
        })
        root.put("cpu", JSONObject().apply {
            put("cores", c.cores)
            put("architecture", c.architecture)
            put("boardPlatform", c.boardPlatform)
            put("hardware", c.hardware)
            put("processor", c.processor)
            put("supportedAbis", JSONArray(c.supportedAbis))
            put("currentFreqMhz", JSONArray(c.currentFreqMhz))
            put("minFreqMhz", c.minFreqMhz ?: JSONObject.NULL)
            put("maxFreqMhz", c.maxFreqMhz ?: JSONObject.NULL)
            put("governors", JSONArray(c.governors))
            put("availableFreqMhz", JSONArray(c.availableFreqMhz))
            put("onlineCores", JSONArray(c.onlineCores))
            put("topologyHint", c.topologyHint)
            put("freqCoreIndices", JSONArray(c.freqCoreIndices))
            put("coreNames", JSONArray(c.coreNames))
            put("features", JSONArray(c.features))
            put("socManufacturer", c.socManufacturer)
            put("socModel", c.socModel)
        })
        root.put("gpu", JSONObject().apply {
            put("renderer", g.renderer)
            put("vendor", g.vendor)
            put("version", g.version)
        })
        root.put("battery", JSONObject().apply {
            put("level", b.level)
            put("status", b.status)
            put("health", b.health)
            put("temperatureC", b.temperature)
            put("voltageMv", b.voltage)
            put("currentNowMa", b.currentNowMa ?: JSONObject.NULL)
            put("currentAvgMa", b.currentAvgMa ?: JSONObject.NULL)
            put("capacityMah", b.capacityMah ?: JSONObject.NULL)
            put("chargeCounterUah", b.chargeCounterUah ?: JSONObject.NULL)
            put("technology", b.technology)
            put("powerSource", b.powerSource)
            put("capacityHealthPercent", b.capacityHealthPercent ?: JSONObject.NULL)
            put("cycleCount", b.cycleCount ?: JSONObject.NULL)
            put("chargerMaxWatts", b.chargerMaxWatts ?: JSONObject.NULL)
            put("chargeTimeRemainingMs", b.chargeTimeRemainingMs ?: JSONObject.NULL)
            put("powerSaveMode", b.powerSaveMode)
            put("energyMwh", b.energyMwh ?: JSONObject.NULL)
        })
        root.put("memory", JSONObject().apply {
            put("totalRamMb", m.totalRamMb)
            put("availableRamMb", m.availableRamMb)
            put("usedRamMb", m.usedRamMb)
            put("usagePercent", m.usagePercent)
            put("cachedMb", m.cachedMb ?: JSONObject.NULL)
            put("buffersMb", m.buffersMb ?: JSONObject.NULL)
            put("swapTotalMb", m.swapTotalMb ?: JSONObject.NULL)
            put("swapFreeMb", m.swapFreeMb ?: JSONObject.NULL)
            put("zramMb", m.zramMb ?: JSONObject.NULL)
            put("kernelActiveMb", m.kernelActiveMb ?: JSONObject.NULL)
        })
        root.put("network", JSONObject().apply {
            put("isConnected", n.isConnected)
            put("networkType", n.networkType)
            put("latencyMs", n.latencyMs ?: JSONObject.NULL)
            put("latencyTarget", n.latencyTarget)
            put("latencyStatus", n.latencyStatus)
            put("downstreamMbps", n.downstreamMbps ?: JSONObject.NULL)
            put("upstreamMbps", n.upstreamMbps ?: JSONObject.NULL)
            put("validated", n.validated)
            put("metered", n.metered)
        })
        root.put("storage", JSONObject().apply {
            put("totalInternalGb", s.totalInternalGb)
            put("freeInternalGb", s.freeInternalGb)
            put("usedInternalGb", s.usedInternalGb)
            put("usagePercent", s.usagePercent)
            put("dataDirectory", s.dataDirectory)
            put("filesDirectory", s.filesDirectory)
            put("cacheDirectory", s.cacheDirectory)
            put("externalStorageState", s.externalStorageState)
            put("emulatedExternal", s.emulatedExternal)
            put("storageType", s.storageType)
            put("lifeUsed", s.lifeUsed ?: JSONObject.NULL)
            put("preEol", s.preEol ?: JSONObject.NULL)
            put("volumes", JSONArray().apply {
                s.volumes.forEach { v ->
                    put(JSONObject().apply {
                        put("name", v.name)
                        put("path", v.path)
                        put("description", v.description)
                        put("totalBytes", v.totalBytes)
                        put("freeBytes", v.freeBytes)
                        put("usedBytes", v.usedBytes)
                        put("isRemovable", v.isRemovable)
                        put("isPrimary", v.isPrimary)
                        put("state", v.state)
                    })
                }
            })
        })
        root.put("display", JSONObject().apply {
            put("widthPx", d.widthPx)
            put("heightPx", d.heightPx)
            put("densityDpi", d.densityDpi)
            put("density", d.density)
            put("refreshRate", d.refreshRate)
            put("screenSizeInches", d.screenSizeInches)
            put("supportedRefreshRatesHz", JSONArray().apply {
                d.supportedRefreshRatesHz.forEach { put(it.toDouble()) }
            })
            put("hdrFormats", JSONArray(d.hdrFormats))
            put("hdrMaxNits", d.hdrMaxNits?.toDouble() ?: JSONObject.NULL)
            put("modes", JSONArray(d.modes))
            put("wideColorGamut", d.wideColorGamut)
            put("orientation", d.orientation)
        })
        root.put("nfc", JSONObject().apply {
            put("present", report.nfc.present)
            put("enabled", report.nfc.enabled)
        })
        root.put("bluetooth", JSONObject().apply {
            put("present", report.bluetooth.present)
            put("enabled", report.bluetooth.enabled)
            put("bleSupported", report.bluetooth.bleSupported)
        })
        root.put("audio", JSONObject().apply {
            put("outputDevices", JSONArray(report.audio.outputDevices))
            put("inputDevices", JSONArray(report.audio.inputDevices))
        })
        root.put("security", JSONObject().apply {
            // Unreadable stays null, so a reader cannot mistake it for "unlocked".
            put("verifiedBootState", report.security.verifiedBootState.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("bootloaderLocked", report.security.bootloaderLocked ?: JSONObject.NULL)
            put("encryption", report.security.encryption.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("selinux", report.security.selinux.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("buildTags", report.security.buildTags.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("isDebuggableBuild", report.security.isDebuggableBuild ?: JSONObject.NULL)
            put("patchAgeDays", report.security.patchAgeDays ?: JSONObject.NULL)
            put("strongBox", report.security.strongBox ?: JSONObject.NULL)
            put("hardwareKeystore", report.security.hardwareKeystore ?: JSONObject.NULL)
        })
        root.put("system", JSONObject().apply {
            put("sinceBootMs", report.system.sinceBootMs)
            put("awakeMs", report.system.awakeMs)
            put("deepSleepPercent", report.system.deepSleepPercent ?: JSONObject.NULL)
            put("lastWakeReason", report.system.lastWakeReason ?: JSONObject.NULL)
            put("topWakeSources", JSONArray().apply {
                report.system.topWakeSources.forEach { w ->
                    put(JSONObject().apply {
                        put("name", w.name)
                        put("activeMs", w.activeMs)
                    })
                }
            })
        })
        root.put("mediaCodecs", JSONArray().apply {
            report.mediaCodecs.forEach { codec ->
                put(JSONObject().apply {
                    put("mimeType", codec.mimeType)
                    put("name", codec.name)
                    put("hardwareDecode", codec.hardwareDecode)
                    put("softwareDecode", codec.softwareDecode)
                    put("hardwareEncode", codec.hardwareEncode)
                    put("softwareEncode", codec.softwareEncode)
                    put("maxDecodeSize", codec.maxDecodeSize)
                    put("maxDecodeFps", codec.maxDecodeFps ?: JSONObject.NULL)
                })
            }
        })
        val h = report.hardware
        root.put("hardware", JSONObject().apply {
            put("displays", JSONArray().apply {
                h.displays.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id)
                        put("widthPx", d.widthPx)
                        put("heightPx", d.heightPx)
                        put("refreshRate", d.refreshRate.toDouble())
                        put("isDefault", d.isDefault)
                    })
                }
            })
            put("features", JSONObject().apply {
                h.features.forEach { put(it.name, it.present) }
            })
            put("glEsVersion", h.glEsVersion)
            put("vulkanVersion", h.vulkanVersion.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("hasVibrator", h.hasVibrator)
            put("vibratorAmplitudeControl", h.vibratorAmplitudeControl)
            put("audioSampleRate", h.audioSampleRate ?: JSONObject.NULL)
            put("audioFramesPerBuffer", h.audioFramesPerBuffer ?: JSONObject.NULL)
            put("audioLowLatency", h.audioLowLatency)
            put("audioPro", h.audioPro)
            put("gnssModel", h.gnssModel)
            put("gnssYear", h.gnssYear ?: JSONObject.NULL)
            put("simSlots", h.simSlots ?: JSONObject.NULL)
            put("pageSizeKb", h.pageSizeKb ?: JSONObject.NULL)
            put("kernelArch", h.kernelArch)
            put("is64BitOnly", h.is64BitOnly)
            put("isLowRamDevice", h.isLowRamDevice)
            put("heapLimitMb", h.heapLimitMb ?: JSONObject.NULL)
            put("largeHeapLimitMb", h.largeHeapLimitMb ?: JSONObject.NULL)
            put("runtimeVersion", h.runtimeVersion)
            put("firstApiLevel", h.firstApiLevel ?: JSONObject.NULL)
            put("trebleEnabled", h.trebleEnabled ?: JSONObject.NULL)
            put("abUpdates", h.abUpdates ?: JSONObject.NULL)
            put("dynamicPartitions", h.dynamicPartitions ?: JSONObject.NULL)
        })
        root.put("thermalState", JSONObject().apply {
            put("status", report.thermalState.status.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("headroom", report.thermalState.headroom?.toDouble() ?: JSONObject.NULL)
        })
        root.put("thermals", JSONArray().apply {
            report.thermals.forEach { z ->
                put(JSONObject().apply {
                    put("name", z.name)
                    put("type", z.type)
                    put("typeLabel", z.typeLabel)
                    put("tempC", z.tempC)
                })
            }
        })
        root.put("sensors", JSONArray().apply {
            report.sensors.forEach { sens ->
                put(JSONObject().apply {
                    put("name", sens.name)
                    put("type", sens.type)
                    put("vendor", sens.vendor)
                    put("powerMa", sens.powerMa)
                    put("resolution", sens.resolution)
                    put("maxRange", sens.maxRange)
                    put("minDelayUs", sens.minDelayUs)
                    put("liveValues", sens.liveValues)
                    put("maxDelayUs", sens.maxDelayUs)
                    put("isWakeUp", sens.isWakeUp)
                    put("fifoMaxEventCount", sens.fifoMaxEventCount)
                    put("reportingMode", sens.reportingMode)
                    put("typeId", sens.typeId)
                    put("stringType", sens.stringType)
                    put("unit", sens.unit)
                    put("category", sens.category.name)
                    put("version", sens.version)
                })
            }
        })
        root.put("cameras", JSONArray().apply {
            report.cameras.forEach { cam ->
                put(JSONObject().apply {
                    put("id", cam.id)
                    put("facing", cam.facing)
                    put("sensorOrientation", cam.sensorOrientation)
                    put("hardwareLevel", cam.hardwareLevel)
                    put("pixelArraySize", cam.pixelArraySize)
                    put("focalLengths", cam.focalLengths)
                    put("aperture", cam.aperture)
                    put("physicalIds", cam.physicalIds)
                    put("capabilities", cam.capabilities)
                    put("activeArraySize", cam.activeArraySize)
                    put("isoRange", cam.isoRange)
                    put("exposureRange", cam.exposureRange)
                    put("hasFlash", cam.hasFlash ?: JSONObject.NULL)
                })
            }
        })
        return root.toString(2)
    }

    private fun radioState(present: Boolean, enabled: Boolean): String = when {
        !present -> "Not present"
        enabled -> "On"
        else -> "Off"
    }
}
