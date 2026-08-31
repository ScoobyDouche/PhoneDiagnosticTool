package com.phonediagnostic.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.storage.StorageManager

class UsageCollector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Android hides other apps' process memory. We always report *this* app accurately
     * via Debug.MemoryInfo + getProcessMemoryInfo(myPid), then any other visible processes.
     */
    fun collectProcessRam(): List<ProcessRamEntry> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val byPid = LinkedHashMap<Int, ProcessRamEntry>()

        // 1) This app — always accurate
        val myPid = Process.myPid()
        val selfPssMb = readPssMb(am, myPid)
        byPid[myPid] = ProcessRamEntry(
            pid = myPid,
            processName = context.packageName,
            appLabel = "Phone Diagnostic (this app)",
            importance = "Foreground",
            pssMb = selfPssMb
        )

        // 2) Whatever else Android still exposes
        val processes = try {
            am.runningAppProcesses
        } catch (_: Exception) {
            null
        }
        if (!processes.isNullOrEmpty()) {
            val pids = processes.map { it.pid }.distinct().toIntArray()
            val memInfos = try {
                am.getProcessMemoryInfo(pids)
            } catch (_: Exception) {
                emptyArray()
            }
            // Results line up with `pids`, not with `processes`. Those differ as soon
            // as two entries share a pid, so index into the query array by pid.
            val memByPid = pids.withIndex().mapNotNull { (index, pid) ->
                memInfos.getOrNull(index)?.let { pid to it }
            }.toMap()
            processes.forEach { proc ->
                if (byPid.containsKey(proc.pid)) return@forEach
                val mem = memByPid[proc.pid] ?: return@forEach
                val pssKb = mem.totalPss
                if (pssKb <= 0) return@forEach
                val name = proc.processName ?: return@forEach
                byPid[proc.pid] = ProcessRamEntry(
                    pid = proc.pid,
                    processName = name,
                    appLabel = labelForProcess(name),
                    importance = importanceLabel(proc.importance),
                    pssMb = pssKb / 1024f
                )
            }
        }

        return byPid.values.sortedByDescending { it.pssMb }
    }

    private fun readPssMb(am: ActivityManager, pid: Int): Float {
        try {
            val info = am.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
            if (info != null && info.totalPss > 0) {
                return info.totalPss / 1024f
            }
        } catch (_: Exception) {
            // fall through
        }
        // Fallback: Debug for current process only
        if (pid == Process.myPid()) {
            val mi = Debug.MemoryInfo()
            Debug.getMemoryInfo(mi)
            if (mi.totalPss > 0) return mi.totalPss / 1024f
            // Last resort: Java heap
            val rt = Runtime.getRuntime()
            val used = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
            return used.coerceAtLeast(0.1f)
        }
        return 0f
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun collectAppStorage(): List<AppStorageEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        if (!hasUsageStatsPermission()) return emptyList()

        val statsManager =
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                ?: return emptyList()

        val apps = try {
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (_: Exception) {
            pm.getInstalledApplications(0)
        }

        val uuid = StorageManager.UUID_DEFAULT
        val result = ArrayList<AppStorageEntry>(apps.size)

        for (app in apps) {
            try {
                val stats = statsManager.queryStatsForPackage(
                    uuid,
                    app.packageName,
                    Process.myUserHandle()
                )
                val total = stats.appBytes + stats.dataBytes + stats.cacheBytes
                if (total <= 0L) continue
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                result.add(
                    AppStorageEntry(
                        packageName = app.packageName,
                        appLabel = labelForPackage(app),
                        appBytes = stats.appBytes,
                        dataBytes = stats.dataBytes,
                        cacheBytes = stats.cacheBytes,
                        isSystemApp = isSystem
                    )
                )
            } catch (_: Exception) {
                // Skip packages we cannot query
            }
        }

        return result.sortedByDescending { it.totalBytes }
    }

    private fun labelForProcess(processName: String): String {
        val pkg = processName.substringBefore(":")
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun labelForPackage(app: ApplicationInfo): String {
        return try {
            pm.getApplicationLabel(app).toString()
        } catch (_: Exception) {
            app.packageName
        }
    }

    private fun importanceLabel(importance: Int): String {
        return when (importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "Gone"
            else -> "Other"
        }
    }
}
