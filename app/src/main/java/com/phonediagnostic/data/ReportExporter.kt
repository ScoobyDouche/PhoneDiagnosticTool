package com.phonediagnostic.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object ReportExporter {

    fun toShareText(report: FullDeviceReport): String {
        val o = report.overview
        val c = report.cpu
        val g = report.gpu
        val b = report.battery
        val m = report.memory
        val n = report.network
        val s = report.storage
        val d = report.display

        return buildString {
            appendLine("Phone Diagnostic Report")
            appendLine("========================")
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
            appendLine("  Technology: ${b.technology}")
            appendLine("  Power Source: ${b.powerSource}")
            appendLine()
            appendLine("MEMORY (RAM)")
            appendLine("  Total: ${m.totalRamMb} MB")
            appendLine("  Available: ${m.availableRamMb} MB")
            appendLine("  Used: ${m.usedRamMb} MB (${m.usagePercent}%)")
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
            appendLine("  Data path: ${s.dataDirectory}")
            appendLine("  Files path: ${s.filesDirectory}")
            appendLine("  Cache path: ${s.cacheDirectory}")
            appendLine("  External state: ${s.externalStorageState}")
            appendLine("  Emulated external: ${s.emulatedExternal}")
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
            appendLine()
            if (report.thermals.isNotEmpty()) {
                appendLine("THERMALS (${report.thermals.size})")
                report.thermals.forEach { z ->
                    appendLine("  ${z.type.ifBlank { z.name }}: ${String.format(Locale.US, "%.1f", z.tempC)} °C")
                }
                appendLine()
            }
            appendLine("SENSORS (${report.sensors.size})")
            report.sensors.take(40).forEach { sens ->
                appendLine("  ${sens.type}: ${sens.name} (${sens.vendor})")
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
            }
            appendLine()
            appendLine("Generated by Phone Diagnostic Tool")
            appendLine("Privacy: on-device diagnostics; optional latency probe only.")
        }
    }

    fun toJson(report: FullDeviceReport): String {
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
        })
        root.put("memory", JSONObject().apply {
            put("totalRamMb", m.totalRamMb)
            put("availableRamMb", m.availableRamMb)
            put("usedRamMb", m.usedRamMb)
            put("usagePercent", m.usagePercent)
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
        })
        root.put("thermals", JSONArray().apply {
            report.thermals.forEach { z ->
                put(JSONObject().apply {
                    put("name", z.name)
                    put("type", z.type)
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
                })
            }
        })
        return root.toString(2)
    }
}
