package com.phonediagnostic.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager

class UsageCollector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun collectProcessRam(): List<ProcessRamEntry> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return emptyList()
        if (processes.isEmpty()) return emptyList()

        val pids = processes.map { it.pid }.toIntArray()
        val memInfos = try {
            am.getProcessMemoryInfo(pids)
        } catch (_: Exception) {
            return emptyList()
        }

        return processes.mapIndexedNotNull { index, proc ->
            val mem = memInfos.getOrNull(index) ?: return@mapIndexedNotNull null
            val pssKb = mem.totalPss
            if (pssKb <= 0) return@mapIndexedNotNull null
            val name = proc.processName ?: "unknown"
            ProcessRamEntry(
                pid = proc.pid,
                processName = name,
                appLabel = labelForProcess(name),
                importance = importanceLabel(proc.importance),
                pssMb = pssKb / 1024f
            )
        }.sortedByDescending { it.pssMb }
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
                val stats = statsManager.queryStatsForPackage(uuid, app.packageName, Process.myUserHandle())
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
