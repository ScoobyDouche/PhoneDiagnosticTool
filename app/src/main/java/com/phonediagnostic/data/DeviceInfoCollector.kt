package com.phonediagnostic.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.os.Build
import com.phonediagnostic.data.elevated.ElevatedShell

/**
 * Public collector entry point. Delegates to [DeviceInfoCollectorBase] and
 * enriches CPU / memory / security / sensor / camera / thermal fields via [DeepProbes].
 */
class DeviceInfoCollector(context: Context) : DeviceInfoCollectorBase(context) {

    override var elevated: ElevatedShell?
        get() = super.elevated
        set(value) { super.elevated = value }

    override fun collectCpu(): CpuInfo {
        val base = super.collectCpu()
        val governors = DeepProbes.cpuGovernors(::readNode)
        val availableFreq = DeepProbes.availableFreqMhz(::readNode)
        val online = DeepProbes.onlineCores(::readNode)
        val topology = DeepProbes.topologyHint(online, base.currentFreqMhz, base.maxFreqMhz)
        return base.copy(
            governors = governors,
            availableFreqMhz = availableFreq,
            onlineCores = online,
            topologyHint = topology
        )
    }

    override fun collectMemory(): MemoryInfo {
        val base = super.collectMemory()
        val extra = DeepProbes.meminfoExtras(::readNode)
        return base.copy(
            cachedMb = extra.cachedMb,
            buffersMb = extra.buffersMb,
            swapTotalMb = extra.swapTotalMb,
            swapFreeMb = extra.swapFreeMb,
            zramMb = extra.zramMb,
            kernelActiveMb = extra.activeMb
        )
    }

    override fun collectSecurity(): SecurityInfo {
        val base = super.collectSecurity()
        val tags = Build.TAGS.orEmpty()
        val debuggable = when {
            tags.contains("debug") || tags.contains("eng") -> true
            tags.contains("release-keys") || tags == "release-keys" -> false
            else -> null
        }
        return base.copy(
            selinux = DeepProbes.selinuxMode(::readNode),
            isDebuggableBuild = debuggable,
            buildTags = tags
        )
    }

    override fun collectThermals(): List<ThermalZone> {
        return super.collectThermals().map { z ->
            if (z.typeLabel.isNotEmpty()) z
            else z.copy(typeLabel = DeepProbes.thermalTypeLabel(z.type))
        }
    }

    override fun collectSensors(live: Boolean): List<SensorEntry> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return emptyList()
        val sensors = try { sm.getSensorList(Sensor.TYPE_ALL) } catch (_: Exception) { emptyList() }
        if (sensors.isEmpty()) return emptyList()
        return super.collectSensors(live).map { entry ->
            val match = sensors.find { it.name == entry.name }
            if (match == null) entry
            else entry.copy(
                maxDelayUs = match.maxDelay,
                isWakeUp = match.isWakeUpSensor,
                fifoMaxEventCount = match.fifoMaxEventCount,
                reportingMode = DeepProbes.sensorReportingMode(match.reportingMode)
            )
        }
    }

    override fun collectCameras(): List<CameraEntry> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return emptyList()
        return super.collectCameras().map { cam ->
            try {
                val chars = cm.getCameraCharacteristics(cam.id)
                val deep = DeepProbes.cameraExtras(chars)
                cam.copy(
                    physicalIds = deep.physicalIds,
                    capabilities = deep.capabilities,
                    activeArraySize = deep.activeArraySize,
                    isoRange = deep.isoRange,
                    exposureRange = deep.exposureRange,
                    hasFlash = deep.hasFlash
                )
            } catch (_: Exception) {
                cam
            }
        }
    }
}
