package com.phonediagnostic.data

import android.content.Context
import org.json.JSONObject

/** One reason the score went down, or a check that could not be made. */
enum class HealthFactor {
    BATTERY_WORN,
    BATTERY_VERY_WORN,
    BATTERY_FAULT,
    STORAGE_NEARLY_FULL,
    STORAGE_FULL,
    FLASH_WEAR_HIGH,
    FLASH_SPARE_WARNING,
    FLASH_SPARE_URGENT,
    THERMAL_MODERATE,
    THERMAL_SEVERE,
    PATCH_OLD,
    PATCH_VERY_OLD,
    CHECKUP_FAILURES,
    BATTERY_UNKNOWN
}

data class HealthDeduction(val factor: HealthFactor, val points: Int, val detail: String = "")

data class HealthScore(val score: Int, val deductions: List<HealthDeduction>) {
    val grade: Grade
        get() = when {
            score >= 85 -> Grade.GOOD
            score >= 65 -> Grade.FAIR
            else -> Grade.POOR
        }

    enum class Grade { GOOD, FAIR, POOR }

    companion object {

        /**
         * Pure. Starts at 100 and subtracts a fixed amount per problem, so the
         * number can always be explained line by line — no hidden weighting.
         * Checks that could not be made cost nothing; battery wear that cannot
         * be read is listed (0 points) so the score is not mistaken for proof
         * of a healthy battery.
         */
        fun compute(report: FullDeviceReport, checkup: CheckupResults? = null): HealthScore {
            val out = ArrayList<HealthDeduction>()
            val b = report.battery

            val wear = batteryHealthPercent(b)
            when {
                wear == null -> out += HealthDeduction(HealthFactor.BATTERY_UNKNOWN, 0)
                wear < 80 -> out += HealthDeduction(HealthFactor.BATTERY_VERY_WORN, 25, "$wear%")
                wear < 90 -> out += HealthDeduction(HealthFactor.BATTERY_WORN, 10, "$wear%")
            }
            if (b.health.isNotBlank() && b.health !in setOf("Good", "Unknown", "Cold", "")) {
                out += HealthDeduction(HealthFactor.BATTERY_FAULT, 25, b.health)
            }

            val used = report.storage.usagePercent
            when {
                used >= 95 -> out += HealthDeduction(HealthFactor.STORAGE_FULL, 15, "$used%")
                used >= 90 -> out += HealthDeduction(HealthFactor.STORAGE_NEARLY_FULL, 8, "$used%")
            }

            val pre = report.storage.preEol.orEmpty()
            when {
                pre.startsWith("Urgent") -> out += HealthDeduction(HealthFactor.FLASH_SPARE_URGENT, 20)
                pre.startsWith("Warning") -> out += HealthDeduction(HealthFactor.FLASH_SPARE_WARNING, 10)
            }
            val lifeUpper = flashLifeUsedUpper(report.storage.lifeUsed)
            if (lifeUpper != null && lifeUpper >= 80) {
                out += HealthDeduction(HealthFactor.FLASH_WEAR_HIGH, 10, report.storage.lifeUsed.orEmpty())
            }

            when (report.thermalState.status) {
                "Severe", "Critical", "Emergency", "Shutdown" ->
                    out += HealthDeduction(HealthFactor.THERMAL_SEVERE, 10, report.thermalState.status)
                "Moderate" -> out += HealthDeduction(HealthFactor.THERMAL_MODERATE, 5, report.thermalState.status)
            }

            val patch = report.security.patchAgeDays
            when {
                patch == null -> Unit
                patch > 365 -> out += HealthDeduction(HealthFactor.PATCH_VERY_OLD, 15, "$patch")
                patch > 120 -> out += HealthDeduction(HealthFactor.PATCH_OLD, 5, "$patch")
            }

            val failed = checkup?.failed ?: 0
            if (failed > 0) {
                out += HealthDeduction(HealthFactor.CHECKUP_FAILURES, (failed * 5).coerceAtMost(30), "$failed")
            }

            val score = (100 - out.sumOf { it.points }).coerceIn(0, 100)
            return HealthScore(score, out)
        }

        /** Gauge figure when there is one, else the measured capacity against design. */
        fun batteryHealthPercent(b: BatteryInfo): Int? {
            b.capacityHealthPercent?.let { return it }
            val measured = b.measuredCapacity?.fullMah ?: return null
            val design = b.designChargeMah ?: b.capacityMah ?: return null
            if (design <= 0) return null
            return (measured * 100 / design).coerceAtMost(100)
        }

        /** "70–80% used" -> 80; "Rated life exceeded" -> 100. */
        fun flashLifeUsedUpper(lifeUsed: String?): Int? {
            if (lifeUsed == null) return null
            if (lifeUsed.startsWith("Rated life exceeded")) return 100
            return Regex("(\\d+)%").find(lifeUsed)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}

/**
 * A few slow-moving numbers saved at one moment — before a repair, on the day
 * you bought the phone — to compare against later. Stored in the app's private
 * preferences only; never exported unless the user shares a report.
 */
data class HealthBaseline(
    val savedAtMs: Long,
    val score: Int,
    val batteryHealthPercent: Int?,
    val cycleCount: Int?,
    val storageUsedGb: Double,
    val loadScore: Long?
) {
    fun toJson(): String = JSONObject().apply {
        put("savedAtMs", savedAtMs)
        put("score", score)
        put("batteryHealthPercent", batteryHealthPercent ?: JSONObject.NULL)
        put("cycleCount", cycleCount ?: JSONObject.NULL)
        put("storageUsedGb", storageUsedGb)
        put("loadScore", loadScore ?: JSONObject.NULL)
    }.toString()

    companion object {
        fun of(report: FullDeviceReport, score: HealthScore, loadScore: Long?, nowMs: Long = System.currentTimeMillis()) =
            HealthBaseline(
                savedAtMs = nowMs,
                score = score.score,
                batteryHealthPercent = HealthScore.batteryHealthPercent(report.battery),
                cycleCount = report.battery.cycleCount,
                storageUsedGb = report.storage.usedInternalGb,
                loadScore = loadScore
            )

        fun fromJson(json: String?): HealthBaseline? = try {
            if (json.isNullOrBlank()) null else JSONObject(json).let { o ->
                HealthBaseline(
                    savedAtMs = o.getLong("savedAtMs"),
                    score = o.getInt("score"),
                    batteryHealthPercent = if (o.isNull("batteryHealthPercent")) null else o.getInt("batteryHealthPercent"),
                    cycleCount = if (o.isNull("cycleCount")) null else o.getInt("cycleCount"),
                    storageUsedGb = o.getDouble("storageUsedGb"),
                    loadScore = if (o.isNull("loadScore")) null else o.getLong("loadScore")
                )
            }
        } catch (_: Exception) {
            null
        }

        private const val PREFS = "health_baseline"
        private const val KEY = "baseline"

        fun load(context: Context): HealthBaseline? =
            fromJson(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

        fun save(context: Context, baseline: HealthBaseline?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (baseline == null) remove(KEY) else putString(KEY, baseline.toJson())
            }.apply()
        }
    }
}
