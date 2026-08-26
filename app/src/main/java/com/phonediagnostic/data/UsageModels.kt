package com.phonediagnostic.data

data class ProcessRamEntry(
    val pid: Int,
    val processName: String,
    val appLabel: String,
    val importance: String,
    val pssMb: Float
)

data class AppStorageEntry(
    val packageName: String,
    val appLabel: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}
