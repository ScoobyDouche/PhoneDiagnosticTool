package com.phonediagnostic.data

data class ProcessRamEntry(
    val pid: Int,
    val processName: String,
    val appLabel: String,
    val importance: String,
    val pssMb: Float,
    /** CPU load percent, only available via elevated access (dumpsys cpuinfo). */
    val cpuPercent: Float? = null,
    /**
     * Name of the elevated tier (SHIZUKU / ROOT) this entry came from, or null
     * for the normal self/visible-process path. When set, the list is the full
     * system-wide process table rather than just this app.
     */
    val elevatedSource: String? = null
)

data class AppStorageEntry(
    val packageName: String,
    val appLabel: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val isSystemApp: Boolean
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}
