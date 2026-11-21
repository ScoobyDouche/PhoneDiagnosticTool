package com.phonediagnostic.data

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Hardware pokes for the checkup. Each is best-effort and reports failure as false. */
object CheckupHardware {

    private const val SAMPLE_RATE = 44_100

    /** A short two-tone chime, easy to tell apart from background noise. */
    private fun chime(): ShortArray {
        val seconds = 1.2
        val n = (SAMPLE_RATE * seconds).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val freq = if (t < seconds / 2) 880.0 else 1320.0
            // 20 ms fade in/out avoids clicks.
            val env = minOf(1.0, t / 0.02, (seconds - t) / 0.02)
            (sin(2 * PI * freq * t) * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /**
     * Plays the chime. With [earpiece] the audio is routed as a voice call, which
     * is what sends it to the earpiece rather than the loudspeaker; that routing
     * needs MODIFY_AUDIO_SETTINGS, a normal (install-time) permission.
     */
    suspend fun playTone(context: Context, earpiece: Boolean): Boolean = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@withContext false
        val pcm = chime()
        val usage = if (earpiece) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
        val previousMode = am.mode
        var track: AudioTrack? = null
        try {
            if (earpiece) routeToEarpiece(am)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(
                            if (earpiece) AudioAttributes.CONTENT_TYPE_SPEECH
                            else AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(pcm.size * 1000L / SAMPLE_RATE + 150)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
            if (earpiece) restoreRouting(am, previousMode)
        }
    }

    private fun routeToEarpiece(am: AudioManager) {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                ?.let { am.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        }
    }

    private fun restoreRouting(am: AudioManager, previousMode: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice()
            am.mode = previousMode
        }
    }

    fun vibrate(context: Context): Boolean = try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            false
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 600), -1))
            true
        }
    } catch (_: Exception) {
        false
    }

    /** First camera with a flash unit, or null. No CAMERA permission is needed to read this or drive the torch. */
    fun torchCameraId(context: Context): String? = try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: Exception) {
        null
    }

    fun setTorch(context: Context, id: String, on: Boolean): Boolean = try {
        (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, on)
        true
    } catch (_: Exception) {
        false
    }

    /** Whether a wired or USB headset / headphones is connected right now. */
    fun headphonesConnected(context: Context): Boolean = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    } catch (_: Exception) {
        false
    }

    /** Charger state straight from the sticky battery broadcast. */
    fun isPlugged(context: Context): Boolean = try {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        (intent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    } catch (_: Exception) {
        false
    }
}
