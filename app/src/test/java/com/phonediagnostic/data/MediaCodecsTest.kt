package com.phonediagnostic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCodecsTest {

    @Test
    fun softwareCodecNamesAreRecognised() {
        assertFalse(MediaCodecs.isHardwareName("c2.android.av1.decoder"))
        assertFalse(MediaCodecs.isHardwareName("OMX.google.h264.decoder"))
        assertTrue(MediaCodecs.isHardwareName("c2.qti.hevc.decoder"))
        assertTrue(MediaCodecs.isHardwareName("OMX.Exynos.avc.dec"))
    }

    @Test
    fun friendlyNamesCoverCommonFormatsOnly() {
        assertEquals("AV1", MediaCodecs.friendlyName("video/av01"))
        assertEquals("HEVC (H.265)", MediaCodecs.friendlyName("VIDEO/HEVC"))
        assertNull(MediaCodecs.friendlyName("audio/raw"))
        assertNull(MediaCodecs.friendlyName("video/x-vendor-private"))
    }
}
