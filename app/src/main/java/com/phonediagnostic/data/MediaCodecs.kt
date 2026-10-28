package com.phonediagnostic.data

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/** What the phone can do with one media format, folded across all its codecs. */
data class CodecSupport(
    val mimeType: String,
    /** Readable name, e.g. "AV1" or "HEVC (H.265)". */
    val name: String,
    val isVideo: Boolean,
    val hardwareDecode: Boolean,
    val softwareDecode: Boolean,
    val hardwareEncode: Boolean,
    val softwareEncode: Boolean,
    /** Largest decode size any decoder offers, e.g. "3840×2160"; blank for audio. */
    val maxDecodeSize: String = "",
    /** Highest decode frame rate any decoder offers; null for audio. */
    val maxDecodeFps: Int? = null
) {
    val canDecode: Boolean get() = hardwareDecode || softwareDecode
    val canEncode: Boolean get() = hardwareEncode || softwareEncode
}

object MediaCodecs {

    /** Codecs never change while the process lives, and listing them is not free. */
    @Volatile
    private var cached: List<CodecSupport>? = null

    fun collect(): List<CodecSupport> {
        cached?.let { return it }
        val infos = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()
        } catch (_: Exception) {
            return emptyList()
        }
        val byType = LinkedHashMap<String, MutableList<MediaCodecInfo>>()
        for (info in infos) {
            for (type in info.supportedTypes) {
                byType.getOrPut(type.lowercase()) { ArrayList() }.add(info)
            }
        }
        val result = byType.mapNotNull { (type, codecs) ->
            val name = friendlyName(type) ?: return@mapNotNull null
            val isVideo = type.startsWith("video/")
            var maxW = 0
            var maxH = 0
            var maxFps = 0
            if (isVideo) {
                codecs.filter { !it.isEncoder }.forEach { codec ->
                    try {
                        val video = codec.getCapabilitiesForType(type).videoCapabilities ?: return@forEach
                        val w = video.supportedWidths.upper
                        val h = video.supportedHeights.upper
                        if (w.toLong() * h > maxW.toLong() * maxH) {
                            maxW = w
                            maxH = h
                        }
                        maxFps = maxOf(maxFps, video.supportedFrameRates.upper)
                    } catch (_: Exception) {
                    }
                }
            }
            CodecSupport(
                mimeType = type,
                name = name,
                isVideo = isVideo,
                hardwareDecode = codecs.any { !it.isEncoder && isHardware(it) },
                softwareDecode = codecs.any { !it.isEncoder && !isHardware(it) },
                hardwareEncode = codecs.any { it.isEncoder && isHardware(it) },
                softwareEncode = codecs.any { it.isEncoder && !isHardware(it) },
                maxDecodeSize = if (maxW > 0 && maxH > 0) "${maxW}×$maxH" else "",
                maxDecodeFps = maxFps.takeIf { it > 0 }
            )
        }.sortedWith(compareByDescending<CodecSupport> { it.isVideo }.thenBy { it.name })
        cached = result
        return result
    }

    /**
     * Below API 29 there is no flag, so fall back to the naming convention:
     * Google's software codecs are OMX.google.* and c2.android.*.
     */
    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            isHardwareName(info.name)
        }

    internal fun isHardwareName(name: String): Boolean {
        val n = name.lowercase()
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw."))
    }

    /** Formats people recognise; vendor-private and raw types are left out. */
    internal fun friendlyName(mimeType: String): String? = when (mimeType.lowercase()) {
        "video/av01" -> "AV1"
        "video/hevc" -> "HEVC (H.265)"
        "video/avc" -> "AVC (H.264)"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/dolby-vision" -> "Dolby Vision"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "video/mpeg2" -> "MPEG-2"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/flac" -> "FLAC"
        "audio/vorbis" -> "Vorbis"
        "audio/mpeg" -> "MP3"
        "audio/ac3" -> "Dolby Digital (AC-3)"
        "audio/eac3" -> "Dolby Digital Plus (E-AC-3)"
        "audio/eac3-joc" -> "Dolby Atmos (E-AC-3 JOC)"
        "audio/ac4" -> "Dolby AC-4"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd" -> "DTS-HD"
        "audio/3gpp" -> "AMR-NB"
        "audio/amr-wb" -> "AMR-WB"
        "audio/g711-alaw" -> "G.711 A-law"
        "audio/g711-mlaw" -> "G.711 µ-law"
        "audio/raw" -> null
        else -> null
    }
}
